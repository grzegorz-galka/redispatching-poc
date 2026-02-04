package pl.tso.redispatch.service.model;

import java.time.Instant;
import java.util.List;

/**
 * Full details of a redispatch order with header data and technical items.
 * Matches OpenAPI schema: RedispatchOrder
 */
public record RedispatchOrder(
    String redispatchOrderId,
    String entityId,
    Instant issueOrderTs,
    String redispatchOrderReason,        // B or S
    RedispatchOrderPeriod redispatchOrderPeriod,
    List<RedispatchOrderItem> redispatchOrders
) {
}
