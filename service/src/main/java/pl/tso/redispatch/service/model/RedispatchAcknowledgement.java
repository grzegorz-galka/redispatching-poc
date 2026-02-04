package pl.tso.redispatch.service.model;

/**
 * Acknowledgement payload sent by the entity to confirm receipt or decision.
 * Matches OpenAPI schema: RedispatchAcknowledgement
 */
public record RedispatchAcknowledgement(
    String redispatchOrderId,
    String entityId,
    String status,   // RECEIVED, ACCEPTED, REJECTED
    String reason    // Optional comment (max 512 chars)
) {
}
