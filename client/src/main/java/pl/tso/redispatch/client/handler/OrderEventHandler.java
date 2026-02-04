package pl.tso.redispatch.client.handler;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.tso.redispatch.client.client.RedispatchApiClient;
import pl.tso.redispatch.client.model.RedispatchAcknowledgement;

/**
 * Handles SSE events and implements the client workflow.
 */
@Component
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

    private final RedispatchApiClient apiClient;
    private final String entityId;

    public OrderEventHandler(RedispatchApiClient apiClient,
                            @Value("${client.entity-id}") String entityId) {
        this.apiClient = apiClient;
        this.entityId = entityId;
    }

    /**
     * Handle connected event.
     */
    public void handleConnected(JsonNode data) {
        String connectionId = data.has("connectionId") ? data.get("connectionId").asText() : "unknown";
        String timestamp = data.has("timestamp") ? data.get("timestamp").asText() : "unknown";

        log.info("==============================================");
        log.info("SSE CONNECTED");
        log.info("Connection ID: {}", connectionId);
        log.info("Timestamp: {}", timestamp);
        log.info("==============================================");
    }

    /**
     * Handle heartbeat event.
     */
    public void handleHeartbeat(JsonNode data) {
        String timestamp = data.has("timestamp") ? data.get("timestamp").asText() : "unknown";
        log.debug("Heartbeat received at: {}", timestamp);
    }

    /**
     * Handle ORDER_ISSUED event - implements the full workflow.
     */
    public void handleOrderIssued(JsonNode data) {
        String orderId = data.get("redispatchOrderId").asText();
        String entityId = data.get("entityId").asText();
        String timestamp = data.get("timestamp").asText();
        String resourceUrl = data.get("resourceUrl").asText();

        log.info("==============================================");
        log.info("ORDER ISSUED");
        log.info("Order ID: {}", orderId);
        log.info("Entity ID: {}", entityId);
        log.info("Timestamp: {}", timestamp);
        log.info("Resource URL: {}", resourceUrl);
        log.info("==============================================");

        // Workflow: Fetch order details using resourceUrl from the event
        apiClient.getOrderByUrl(resourceUrl)
            .doOnNext(order -> {
                log.info("Order Details Retrieved:");
                log.info("  - Reason: {}", order.redispatchOrderReason());
                log.info("  - Period: {} to {}",
                         order.redispatchOrderPeriod().startDt(),
                         order.redispatchOrderPeriod().endDt());
                log.info("  - Items count: {}", order.redispatchOrders().size());
                order.redispatchOrders().forEach(item -> {
                    log.info("    * Object MRID: {}", item.redispatchingObjectMrid());
                    log.info("      Measurement: {} {}", item.measurementUnit(), item.curveType());
                    item.seriesPeriods().forEach(period -> {
                        log.info("      Direction: {}, Resolution: {}, Points: {}",
                                 period.direction(),
                                 period.resolution(),
                                 period.seriesPoints().size());
                    });
                });
            })
            .flatMap(order -> {
                // Workflow: Send RECEIVED acknowledgement
                RedispatchAcknowledgement ack = RedispatchAcknowledgement.received(orderId, this.entityId);
                return apiClient.sendAcknowledgementByUrl(resourceUrl, ack);
            })
            .doOnSuccess(v -> {
                log.info("Acknowledgement sent successfully for order: {}", orderId);
                log.info("==============================================");
            })
            .doOnError(error -> {
                log.error("Error processing order {}: {}", orderId, error.getMessage());
                log.error("==============================================");
            })
            .subscribe();
    }
}
