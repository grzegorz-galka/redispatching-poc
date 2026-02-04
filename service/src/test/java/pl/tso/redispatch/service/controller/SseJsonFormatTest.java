package pl.tso.redispatch.service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify SSE data is properly formatted as JSON for Gatling compatibility.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
class SseJsonFormatTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testSseConnectedEvent_HasJsonDataWithEventType() throws Exception {
        // Given
        String entityId = "ENT_TEST";

        // When
        Flux<ServerSentEvent> eventStream = webTestClient.get()
            .uri("/redispatch/" + entityId + "/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(ServerSentEvent.class)
            .getResponseBody();

        // Then - Verify the connected event has proper JSON structure
        StepVerifier.create(eventStream.take(1))
            .assertNext(event -> {
                try {
                    // Verify event metadata
                    assertEquals("connected", event.event(), "Event type should be 'connected'");
                    assertNotNull(event.id(), "Event ID should be present");
                    assertNotNull(event.data(), "Event data should be present");

                    // Verify data can be parsed as JSON and has eventType field
                    String jsonData = objectMapper.writeValueAsString(event.data());
                    System.out.println("SSE data as JSON: " + jsonData);

                    JsonNode dataNode = objectMapper.readTree(jsonData);
                    assertTrue(dataNode.has("eventType"), "Data should have eventType field");
                    assertEquals("connected", dataNode.get("eventType").asText(), "eventType should be 'connected'");
                    assertTrue(dataNode.has("connectionId"), "Data should have connectionId field");
                    assertTrue(dataNode.has("timestamp"), "Data should have timestamp field");

                } catch (Exception e) {
                    fail("Failed to parse SSE data as JSON: " + e.getMessage());
                }
            })
            .verifyComplete();
    }
}
