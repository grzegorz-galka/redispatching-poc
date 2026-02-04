package pl.tso.redispatch.service.model.event;

import java.time.Instant;

/**
 * Notification sent via SSE when a new redispatch order is issued.
 * Matches OpenAPI schema: RedispatchOrderIssuedEvent
 */
public record RedispatchOrderIssuedEvent(
    String eventType,
    String redispatchOrderId,
    String entityId,
    Instant timestamp,
    String resourceUrl
) {
    public static RedispatchOrderIssuedEvent create(String redispatchOrderId, String entityId) {
        String encodedOrderId = java.net.URLEncoder.encode(redispatchOrderId, java.nio.charset.StandardCharsets.UTF_8);
        String resourceUrl = "/redispatch/" + entityId + "/orders/" + encodedOrderId;

        return new RedispatchOrderIssuedEvent(
            "ORDER_ISSUED",
            redispatchOrderId,
            entityId,
            Instant.now(),
            resourceUrl
        );
    }
}
