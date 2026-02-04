package pl.tso.redispatch.loadtest.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Function;

/**
 * Utility for extracting fields from Gatling SSE message data.
 *
 * Gatling wraps SSE messages as: {"event":"...", "id":"...", "data":"..."}
 * where the data field contains escaped JSON string that needs to be parsed.
 */
public final class SseDataExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseDataExtractor() {
        // Utility class
    }

    /**
     * Creates a transform function that extracts a field from the SSE data JSON string.
     *
     * @param fieldName the JSON field name to extract from the data
     * @return a function that extracts the field value as String
     */
    public static Function<String, String> field(String fieldName) {
        return escapedJson -> {
            try {
                JsonNode node = MAPPER.readTree(escapedJson);
                JsonNode fieldNode = node.get(fieldName);
                return fieldNode != null ? fieldNode.asText() : null;
            } catch (Exception e) {
                return null;
            }
        };
    }

    /**
     * Creates a transform function that checks if a field equals the expected value.
     * Returns the value if it matches, null otherwise.
     *
     * @param fieldName the JSON field name to check
     * @param expectedValue the expected value
     * @return a function that returns the value if it matches
     */
    public static Function<String, String> fieldEquals(String fieldName, String expectedValue) {
        return escapedJson -> {
            try {
                JsonNode node = MAPPER.readTree(escapedJson);
                JsonNode fieldNode = node.get(fieldName);
                if (fieldNode != null && expectedValue.equals(fieldNode.asText())) {
                    return fieldNode.asText();
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        };
    }
}
