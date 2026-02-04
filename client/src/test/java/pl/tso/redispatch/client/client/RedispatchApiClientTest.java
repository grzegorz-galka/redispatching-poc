package pl.tso.redispatch.client.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tso.redispatch.client.model.RedispatchAcknowledgement;
import pl.tso.redispatch.client.model.RedispatchOrder;
import pl.tso.redispatch.client.model.RedispatchOrderPeriod;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedispatchApiClient verifying URL encoding handling.
 */
class RedispatchApiClientTest {

    private MockWebServer mockServer;
    private RedispatchApiClient apiClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        String baseUrl = mockServer.url("/v1").toString();
        // Remove trailing slash if present
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        apiClient = new RedispatchApiClient(baseUrl);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void getOrderByUrl_shouldNotDoubleEncodeSlashesInOrderId() throws Exception {
        // Given: Order ID with slashes that are URL-encoded
        String orderId = "51/I/03.02.2026";
        String encodedOrderId = "51%2FI%2F03.02.2026";
        String resourceUrl = "/redispatch/ENT01/orders/" + encodedOrderId;

        // Mock response
        Instant now = Instant.now();
        RedispatchOrder mockOrder = new RedispatchOrder(
            orderId,
            "ENT01",
            now,
            "Test reason",
            new RedispatchOrderPeriod(now, now.plusSeconds(3600)),
            List.of()
        );
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(mockOrder)));

        // When: Fetching order using resourceUrl
        RedispatchOrder result = apiClient.getOrderByUrl(resourceUrl).block();

        // Then: Request should have single-encoded URL (not double-encoded)
        RecordedRequest request = mockServer.takeRequest();
        String requestPath = request.getPath();

        // Verify the path contains single-encoded slashes
        assertTrue(requestPath.contains("51%2FI%2F03.02.2026"),
            "Path should contain single-encoded order ID: " + requestPath);

        // Verify it does NOT contain double-encoded slashes
        assertFalse(requestPath.contains("51%252FI%252F"),
            "Path should NOT contain double-encoded order ID: " + requestPath);

        // Verify the full path
        assertEquals("/v1/redispatch/ENT01/orders/51%2FI%2F03.02.2026", requestPath,
            "Full path should be correctly constructed");

        // Verify the response
        assertNotNull(result);
        assertEquals(orderId, result.redispatchOrderId());
    }

    @Test
    void sendAcknowledgementByUrl_shouldNotDoubleEncodeSlashesInOrderId() throws Exception {
        // Given: Order ID with slashes that are URL-encoded
        String orderId = "52/I/03.02.2026";
        String encodedOrderId = "52%2FI%2F03.02.2026";
        String resourceUrl = "/redispatch/ENT01/orders/" + encodedOrderId;

        RedispatchAcknowledgement ack = RedispatchAcknowledgement.received(orderId, "ENT01");

        // Mock response
        mockServer.enqueue(new MockResponse()
            .setResponseCode(202)
            .setHeader("Content-Type", "application/json"));

        // When: Sending acknowledgement using resourceUrl
        apiClient.sendAcknowledgementByUrl(resourceUrl, ack).block();

        // Then: Request should have single-encoded URL (not double-encoded)
        RecordedRequest request = mockServer.takeRequest();
        String requestPath = request.getPath();

        // Verify the path contains single-encoded slashes
        assertTrue(requestPath.contains("52%2FI%2F03.02.2026"),
            "Path should contain single-encoded order ID: " + requestPath);

        // Verify it does NOT contain double-encoded slashes
        assertFalse(requestPath.contains("52%252FI%252F"),
            "Path should NOT contain double-encoded order ID: " + requestPath);

        // Verify the full path
        assertEquals("/v1/redispatch/ENT01/orders/52%2FI%2F03.02.2026/acknowledgement", requestPath,
            "Full path should be correctly constructed with /acknowledgement suffix");
    }

    @Test
    void getOrderByUrl_shouldHandleComplexOrderIds() throws Exception {
        // Given: Order ID with multiple special characters
        String orderId = "123/I/03.02.2026";
        String encodedOrderId = "123%2FI%2F03.02.2026";
        String resourceUrl = "/redispatch/ABCDE/orders/" + encodedOrderId;

        // Mock response
        Instant now = Instant.now();
        RedispatchOrder mockOrder = new RedispatchOrder(
            orderId,
            "ABCDE",
            now,
            "Test reason",
            new RedispatchOrderPeriod(now, now.plusSeconds(3600)),
            List.of()
        );
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(mockOrder)));

        // When: Fetching order
        RedispatchOrder result = apiClient.getOrderByUrl(resourceUrl).block();

        // Then: Verify request path
        RecordedRequest request = mockServer.takeRequest();
        String requestPath = request.getPath();

        assertEquals("/v1/redispatch/ABCDE/orders/123%2FI%2F03.02.2026", requestPath);
        assertNotNull(result);
        assertEquals(orderId, result.redispatchOrderId());
    }

    @Test
    void getOrder_directMethod_shouldHandleEncodingCorrectly() throws Exception {
        // Given: Raw order ID (not encoded)
        String orderId = "99/I/03.02.2026";
        String entityId = "ENT99";

        // Mock response
        Instant now = Instant.now();
        RedispatchOrder mockOrder = new RedispatchOrder(
            orderId,
            entityId,
            now,
            "Test reason",
            new RedispatchOrderPeriod(now, now.plusSeconds(3600)),
            List.of()
        );
        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(mockOrder)));

        // When: Using direct getOrder method (not getOrderByUrl)
        RedispatchOrder result = apiClient.getOrder(entityId, orderId).block();

        // Then: The method should encode the order ID
        RecordedRequest request = mockServer.takeRequest();
        String requestPath = request.getPath();

        assertTrue(requestPath.contains("99%2FI%2F03.02.2026"),
            "Direct method should encode order ID: " + requestPath);
        assertNotNull(result);
        assertEquals(orderId, result.redispatchOrderId());
    }
}
