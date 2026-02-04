package pl.tso.redispatch.service.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.tso.redispatch.service.model.RedispatchOrder;
import pl.tso.redispatch.service.model.RedispatchOrderItem;
import pl.tso.redispatch.service.model.SeriesPoint;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RedispatchOrderService mock data generation.
 */
@SpringBootTest
class RedispatchOrderServiceTest {

    @Autowired
    private RedispatchOrderService orderService;

    @Test
    void testGenerateMockOrder_CreatesValidOrder() {
        // Given
        String entityId = "ENT01";

        // When
        RedispatchOrder order = orderService.generateMockOrder(entityId);

        // Then
        assertNotNull(order);
        assertEquals(entityId, order.entityId());
        assertNotNull(order.redispatchOrderId());
        assertNotNull(order.issueOrderTs());
        assertNotNull(order.redispatchOrderPeriod());
        assertNotNull(order.redispatchOrders());
        assertFalse(order.redispatchOrders().isEmpty());
    }

    @Test
    void testOrderIdFormat_MatchesExpectedPattern() {
        // Given
        String entityId = "ENT01";

        // When
        RedispatchOrder order = orderService.generateMockOrder(entityId);

        // Then
        String orderId = order.redispatchOrderId();
        assertTrue(orderId.matches("\\d+/I/\\d{2}\\.\\d{2}\\.\\d{4}"),
                   "Order ID should match pattern {seq}/I/{dd.MM.yyyy}");

        // Verify date part is today
        String[] parts = orderId.split("/I/");
        assertEquals(2, parts.length);
        String expectedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        assertEquals(expectedDate, parts[1]);
    }

    @Test
    void testOrderReason_IsValid() {
        // Given
        String entityId = "ENT01";

        // When
        RedispatchOrder order = orderService.generateMockOrder(entityId);

        // Then
        String reason = order.redispatchOrderReason();
        assertTrue(reason.equals("B") || reason.equals("S"),
                   "Reason must be either B (balancing) or S (network)");
    }

    @Test
    void testSeriesPoints_QuantityMinLessThanMax() {
        // Given
        String entityId = "ENT01";

        // When
        RedispatchOrder order = orderService.generateMockOrder(entityId);

        // Then
        for (RedispatchOrderItem item : order.redispatchOrders()) {
            item.seriesPeriods().forEach(period -> {
                for (SeriesPoint point : period.seriesPoints()) {
                    assertTrue(point.quantityMin() < point.quantityMax(),
                              "quantityMin must be less than quantityMax for position " + point.position());
                }
            });
        }
    }

    @Test
    void testGetOrder_ReturnsStoredOrder() {
        // Given
        String entityId = "ENT01";
        RedispatchOrder generatedOrder = orderService.generateMockOrder(entityId);

        // When
        RedispatchOrder retrievedOrder = orderService.getOrder(generatedOrder.redispatchOrderId());

        // Then
        assertNotNull(retrievedOrder);
        assertEquals(generatedOrder.redispatchOrderId(), retrievedOrder.redispatchOrderId());
        assertEquals(generatedOrder.entityId(), retrievedOrder.entityId());
    }

    @Test
    void testGetOrder_ReturnsNullForUnknownId() {
        // Given
        String unknownOrderId = "999/I/01.01.2099";

        // When
        RedispatchOrder order = orderService.getOrder(unknownOrderId);

        // Then
        assertNull(order);
    }
}
