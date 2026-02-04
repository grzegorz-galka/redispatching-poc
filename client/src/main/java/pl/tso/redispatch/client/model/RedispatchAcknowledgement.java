package pl.tso.redispatch.client.model;

/**
 * Client-side DTO for RedispatchAcknowledgement.
 */
public record RedispatchAcknowledgement(
    String redispatchOrderId,
    String entityId,
    String status,
    String reason
) {
    public static RedispatchAcknowledgement received(String orderId, String entityId) {
        return new RedispatchAcknowledgement(orderId, entityId, "RECEIVED", null);
    }

    public static RedispatchAcknowledgement accepted(String orderId, String entityId, String reason) {
        return new RedispatchAcknowledgement(orderId, entityId, "ACCEPTED", reason);
    }

    public static RedispatchAcknowledgement rejected(String orderId, String entityId, String reason) {
        return new RedispatchAcknowledgement(orderId, entityId, "REJECTED", reason);
    }
}
