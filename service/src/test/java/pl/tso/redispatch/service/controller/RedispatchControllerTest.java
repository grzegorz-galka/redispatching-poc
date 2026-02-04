package pl.tso.redispatch.service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import pl.tso.redispatch.service.model.RedispatchAcknowledgement;
import pl.tso.redispatch.service.model.RedispatchOrder;
import pl.tso.redispatch.service.service.RedispatchOrderService;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RedispatchController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
class RedispatchControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RedispatchOrderService orderService;

    @Test
    void testSseStream_SendsConnectedEvent() {
        // Given
        String entityId = "ENT01";

        // When
        Flux<ServerSentEvent> eventStream = webTestClient.get()
            .uri("/redispatch/" + entityId + "/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(ServerSentEvent.class)
            .getResponseBody();

        // Then
        StepVerifier.create(eventStream.take(1))
            .assertNext(event -> {
                assertEquals("connected", event.event());
                assertNotNull(event.id());
                assertNotNull(event.data());
            })
            .verifyComplete();
    }

    @Test
    void testSseStream_SendsHeartbeatEvents() {
        // Given
        String entityId = "ENT02";

        // When
        Flux<ServerSentEvent> eventStream = webTestClient.get()
            .uri("/redispatch/" + entityId + "/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(ServerSentEvent.class)
            .getResponseBody();

        // Then - Expect connected event, then wait for heartbeat
        StepVerifier.create(eventStream.take(2).timeout(Duration.ofSeconds(35)))
            .assertNext(event -> assertEquals("connected", event.event()))
            .assertNext(event -> {
                String eventType = event.event();
                assertTrue(eventType.equals("heartbeat") || eventType.equals("ORDER_ISSUED"),
                          "Second event should be heartbeat or ORDER_ISSUED");
            })
            .verifyComplete();
    }

    @Test
    void testGetOrder_ReturnsOrder() {
        // Given
        String entityId = "ENT03";
        RedispatchOrder order = orderService.generateMockOrder(entityId);

        // When & Then
        webTestClient.get()
            .uri("/redispatch/" + entityId + "/orders/" + order.redispatchOrderId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody(RedispatchOrder.class)
            .value(retrievedOrder -> {
                assertEquals(order.redispatchOrderId(), retrievedOrder.redispatchOrderId());
                assertEquals(entityId, retrievedOrder.entityId());
            });
    }

    @Test
    void testGetOrder_Returns404ForUnknownOrder() {
        // Given
        String entityId = "ENT04";
        String unknownOrderId = "999/I/01.01.2099";

        // When & Then
        webTestClient.get()
            .uri("/redispatch/" + entityId + "/orders/" + unknownOrderId)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void testAcknowledgeOrder_Returns202() {
        // Given
        String entityId = "ENT05";
        RedispatchOrder order = orderService.generateMockOrder(entityId);
        RedispatchAcknowledgement ack = new RedispatchAcknowledgement(
            order.redispatchOrderId(),
            entityId,
            "RECEIVED",
            null
        );

        // When & Then
        webTestClient.post()
            .uri("/redispatch/" + entityId + "/orders/" + order.redispatchOrderId() + "/acknowledgement")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ack)
            .exchange()
            .expectStatus().isAccepted();
    }

    @Test
    void testAcknowledgeOrder_Returns400ForMismatch() {
        // Given
        String entityId = "ENT06";
        RedispatchOrder order = orderService.generateMockOrder(entityId);
        RedispatchAcknowledgement ack = new RedispatchAcknowledgement(
            "WRONG-ORDER-ID",  // Mismatch
            entityId,
            "RECEIVED",
            null
        );

        // When & Then
        webTestClient.post()
            .uri("/redispatch/" + entityId + "/orders/" + order.redispatchOrderId() + "/acknowledgement")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ack)
            .exchange()
            .expectStatus().isBadRequest();
    }
}
