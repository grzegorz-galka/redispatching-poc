package pl.tso.redispatch.service.model.event;

import java.time.Instant;

/**
 * Periodic event sent to keep the SSE connection alive.
 * Matches OpenAPI schema: HeartbeatEvent
 */
public record HeartbeatEvent(
    String eventType,
    Instant timestamp
) {
    public static HeartbeatEvent create() {
        return new HeartbeatEvent("heartbeat", Instant.now());
    }
}
