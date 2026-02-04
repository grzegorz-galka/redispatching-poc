package pl.tso.redispatch.client.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pl.tso.redispatch.client.handler.OrderEventHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE client for subscribing to order events.
 */
@Component
public class SseClient {

    private static final Logger log = LoggerFactory.getLogger(SseClient.class);

    private final WebClient webClient;
    private final OrderEventHandler eventHandler;
    private final String entityId;
    private final AtomicReference<String> lastEventId = new AtomicReference<>();

    public SseClient(@Value("${client.gateway-url}") String gatewayUrl,
                     @Value("${client.entity-id}") String entityId,
                     OrderEventHandler eventHandler) {
        this.webClient = WebClient.builder()
            .baseUrl(gatewayUrl)
            .build();
        this.entityId = entityId;
        this.eventHandler = eventHandler;
    }

    /**
     * Connect to SSE stream and process events.
     * Automatically reconnects with Last-Event-ID on connection loss.
     */
    public Flux<Void> connect() {
        String path = "/redispatch/" + entityId + "/stream";
        log.info("Connecting to SSE stream: {}", path);

        return webClient.get()
            .uri(path)
            .headers(headers -> {
                String lastId = lastEventId.get();
                if (lastId != null) {
                    log.info("Reconnecting with Last-Event-ID: {}", lastId);
                    headers.set("Last-Event-ID", lastId);
                }
            })
            .retrieve()
            .bodyToFlux(ServerSentEvent.class)
            .doOnNext(event -> {
                // Store event ID for reconnection
                if (event.id() != null) {
                    lastEventId.set(event.id());
                }
                handleEvent(event);
            })
            .doOnError(error -> log.error("SSE connection error: {}", error.getMessage()))
            .retryWhen(reactor.util.retry.Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(5))
                .doBeforeRetry(signal -> log.info("Reconnecting to SSE stream...")))
            .then()
            .flux()
            .repeat();
    }

    private void handleEvent(ServerSentEvent<?> event) {
        String eventType = event.event();
        String eventId = event.id();
        Object data = event.data();

        log.debug("Received SSE event - type: {}, id: {}", eventType, eventId);

        if (eventType == null || data == null) {
            return;
        }

        // Convert data to JsonNode for flexible handling
        JsonNode jsonData = convertToJsonNode(data);

        switch (eventType) {
            case "connected" -> eventHandler.handleConnected(jsonData);
            case "heartbeat" -> eventHandler.handleHeartbeat(jsonData);
            case "ORDER_ISSUED" -> eventHandler.handleOrderIssued(jsonData);
            default -> log.warn("Unknown event type: {}", eventType);
        }
    }

    private JsonNode convertToJsonNode(Object data) {
        if (data instanceof JsonNode) {
            return (JsonNode) data;
        }
        // If data is already parsed, use ObjectMapper to convert
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.valueToTree(data);
        } catch (Exception e) {
            log.error("Failed to convert data to JsonNode", e);
            return null;
        }
    }
}
