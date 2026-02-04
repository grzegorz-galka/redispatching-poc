package pl.tso.redispatch.client.model;

import java.time.Instant;
import java.util.List;

/**
 * Client-side DTO for RedispatchOrder.
 */
public record RedispatchOrder(
    String redispatchOrderId,
    String entityId,
    Instant issueOrderTs,
    String redispatchOrderReason,
    RedispatchOrderPeriod redispatchOrderPeriod,
    List<RedispatchOrderItem> redispatchOrders
) {
}
