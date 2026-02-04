package pl.tso.redispatch.service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pl.tso.redispatch.service.model.RedispatchAcknowledgement;
import pl.tso.redispatch.service.model.RedispatchOrder;
import pl.tso.redispatch.service.service.RedispatchOrderService;
import pl.tso.redispatch.service.service.SseEmitterService;
import reactor.core.publisher.Flux;

/**
 * REST Controller for redispatching order endpoints.
 * Implements SSE streaming and REST endpoints for order management.
 *
 * TODO: Add OAuth2 security with scopes: redispatch.read, redispatch.write
 * See OpenAPI spec securitySchemes for details
 */
@RestController
@RequestMapping("/redispatch")
public class RedispatchController {

    private static final Logger log = LoggerFactory.getLogger(RedispatchController.class);

    private final SseEmitterService sseService;
    private final RedispatchOrderService orderService;

    public RedispatchController(SseEmitterService sseService, RedispatchOrderService orderService) {
        this.sseService = sseService;
        this.orderService = orderService;
    }

    /**
     * SSE endpoint for subscribing to order events.
     * Supports event replay via Last-Event-ID header.
     */
    @GetMapping(path = "/{entityId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<?>> streamEvents(
            @PathVariable String entityId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {

        log.info("SSE connection request from entity: {}, Last-Event-ID: {}", entityId, lastEventId);
        return sseService.createEventStream(entityId, lastEventId);
    }

    /**
     * Get redispatch order details by ID.
     */
    @GetMapping("/{entityId}/orders/{redispatchOrderId}")
    public ResponseEntity<RedispatchOrder> getOrder(
            @PathVariable String entityId,
            @PathVariable String redispatchOrderId) {

        log.info("GET order request - entityId: {}, orderId: {}", entityId, redispatchOrderId);

        RedispatchOrder order = orderService.getOrder(redispatchOrderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        // Verify entity ID matches
        if (!order.entityId().equals(entityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for this entity");
        }

        return ResponseEntity.ok(order);
    }

    /**
     * Accept acknowledgement or decision for an order.
     */
    @PostMapping("/{entityId}/orders/{redispatchOrderId}/acknowledgement")
    public ResponseEntity<Void> acknowledgeOrder(
            @PathVariable String entityId,
            @PathVariable String redispatchOrderId,
            @RequestBody RedispatchAcknowledgement acknowledgement) {

        log.info("POST acknowledgement - entityId: {}, orderId: {}, status: {}",
                 entityId, redispatchOrderId, acknowledgement.status());

        // Validate acknowledgement matches path parameters
        if (!acknowledgement.redispatchOrderId().equals(redispatchOrderId) ||
            !acknowledgement.entityId().equals(entityId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acknowledgement data mismatch");
        }

        orderService.logAcknowledgement(acknowledgement);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
