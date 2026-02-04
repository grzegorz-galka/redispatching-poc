# SSE Load Test Issue - FIXED

## Problem
Gatling SSEBaseline load test was failing with error:
```
jsonPath($.eventType).find.is(connected), but actually found nothing
```

## Root Cause

The issue was in **how Gatling wraps SSE messages**. When Gatling receives an SSE event like:
```
id:2
event:connected
data:{"eventType":"connected","connectionId":"...","timestamp":"..."}
```

It transforms it into a JSON structure:
```json
{
  "event": "connected",
  "id": "2",
  "data": "{\"eventType\":\"connected\",\"connectionId\":\"...\",\"timestamp\":\"...\"}"
}
```

**Key insight**: The `data` field contains the event payload as an **escaped JSON string**, not a nested JSON object!

So `jsonPath("$.eventType")` was looking at the root level (which only has `event`, `id`, `data` fields), not inside the escaped data string.

## Solution

Created a utility class `SseDataExtractor` that:
1. Extracts the `data` field (escaped JSON string)
2. Parses it as proper JSON using Jackson
3. Extracts fields using standard JSON access

### SseDataExtractor Utility

```java
public final class SseDataExtractor {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Function<String, String> field(String fieldName) {
        return escapedJson -> {
            JsonNode node = MAPPER.readTree(escapedJson);
            return node.get(fieldName).asText();
        };
    }
}
```

### Clean Test Code Pattern

```java
sse.checkMessage("connected")
    // Check event type at root level
    .check(jsonPath("$.event").is("connected"))
    // Parse the escaped JSON data and extract fields using jsonPath + transform
    .check(jsonPath("$.data").transform(SseDataExtractor.field("connectionId")).saveAs("connectionId"))
    .check(jsonPath("$.data").transform(SseDataExtractor.field("timestamp")).saveAs("connectedAt"))
```

## Files Modified

### New Utility
- `load-test/src/test/java/.../utils/SseDataExtractor.java` - JSON parsing utility

### Load Test Scenarios
- `load-test/src/test/java/.../scenarios/SseBaselineScenario.java`
- `load-test/src/test/java/.../scenarios/FullWorkflowScenario.java`
- `load-test/src/test/java/.../scenarios/StressTestScenario.java`

### Service (Previous Changes - Still Valid)
- `service/src/main/java/.../config/WebConfig.java` - Jackson configuration
- `service/src/main/java/.../service/SseEmitterService.java` - JSON serialization
- `service/src/main/java/.../controller/RedispatchController.java` - Return types

## Test Results

```
[ENT04] Connected: a5b6d1ef-2ebe-4636-b7c3-bd05cf817f2d
Global: percentage of successful events: 100.0%
BUILD SUCCESS
```

## Summary

The fix required:
1. Understanding Gatling's internal SSE message representation (wraps in JSON with escaped data)
2. Creating `SseDataExtractor` utility to properly parse the escaped JSON
3. Using `jsonPath("$.data").transform(SseDataExtractor.field(...))` pattern for clean, readable checks
