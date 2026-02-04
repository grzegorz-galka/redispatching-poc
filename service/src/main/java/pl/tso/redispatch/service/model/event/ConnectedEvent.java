package pl.tso.redispatch.service.model.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Initial event sent immediately after a successful SSE connection.
 * Matches OpenAPI schema: ConnectedEvent
 */
public record ConnectedEvent(
    String eventType,
    UUID connectionId,
    Instant timestamp
) {
    public static ConnectedEvent create() {
        return new ConnectedEvent("connected", UUID.randomUUID(), Instant.now());
    }
}
