package pl.tso.redispatch.loadtest.scenarios;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import pl.tso.redispatch.loadtest.config.LoadTestConfig;
import pl.tso.redispatch.loadtest.utils.EntityIdFeeder;
import pl.tso.redispatch.loadtest.utils.SseDataExtractor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Full workflow load test - tests realistic user behavior.
 *
 * This scenario:
 * 1. Opens SSE connection and waits for 'connected' event
 * 2. Simulates receiving ORDER_ISSUED events by polling API on schedule
 * 3. Fetches order details via GET
 * 4. Sends acknowledgements via POST
 * 5. Repeats for multiple orders
 *
 * Purpose: Measure end-to-end performance and throughput
 */
public class FullWorkflowScenario extends Simulation {

    // HTTP Protocol configuration
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(LoadTestConfig.BASE_URL)
        .acceptHeader("application/json, text/event-stream")
        .contentTypeHeader("application/json")
        .shareConnections()
        .maxConnectionsPerHost(2000);

    // Scenario definition
    ScenarioBuilder fullWorkflow = scenario("Full Workflow - SSE + REST API")
        .feed(EntityIdFeeder.random())

        // Step 1: Open SSE connection and wait for connected event
        // Note: Gatling wraps SSE messages as {"event":"...", "id":"...", "data":"..."}
        // The data field contains escaped JSON string that we parse using SseDataExtractor
        .exec(
            sse("Open SSE Connection")
                .get("/v1/redispatch/#{entityId}/stream")
                .await(LoadTestConfig.SSE_AWAIT_CONNECTED_TIMEOUT).on(
                    sse.checkMessage("connected")
                        // Check event type at root level
                        .check(jsonPath("$.event").is("connected"))
                        // Parse the escaped JSON data and extract connectionId
                        .check(jsonPath("$.data").transform(SseDataExtractor.field("connectionId")).saveAs("connectionId"))
                )
        )

        .exec(session -> {
            System.out.println("[" + session.get("entityId") + "] SSE Connected");
            return session;
        })

        // Step 2: Simulate order processing workflow (3 orders)
        .repeat(3, "orderCount").on(
            // Wait a bit (simulating time between ORDER_ISSUED events: 60-90s)
            pause(Duration.ofSeconds(
                ThreadLocalRandom.current().nextInt(60, 91)
            ))

            // Generate mock order ID
            .exec(session -> {
                int orderNum = ThreadLocalRandom.current().nextInt(1, 10000);
                String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                String orderId = orderNum + "/I/" + dateStr;
                String encodedOrderId = java.net.URLEncoder.encode(orderId, java.nio.charset.StandardCharsets.UTF_8);
                String resourceUrl = "/redispatch/" + session.getString("entityId") + "/orders/" + encodedOrderId;

                return session
                    .set("orderId", orderId)
                    .set("resourceUrl", resourceUrl);
            })

            .exec(session -> {
                System.out.println("[" + session.get("entityId") + "] Processing order: " + session.get("orderId"));
                return session;
            })

            // Step 3: Fetch order details using resourceUrl
            .exec(
                http("GET Order Details")
                    .get("#{resourceUrl}")
                    .check(status().is(200))
                    .check(jsonPath("$.redispatchOrderId").exists())
                    .check(jsonPath("$.entityId").is("#{entityId}"))
                    .check(jsonPath("$.redispatchOrderReason").optional().saveAs("orderReason"))
            )

            .exec(session -> {
                System.out.println("[" + session.get("entityId") + "] Order fetched: " + session.get("orderId"));
                return session;
            })

            // Step 4: Send acknowledgement
            .exec(
                http("POST Acknowledgement")
                    .post("#{resourceUrl}/acknowledgement")
                    .body(StringBody(session -> {
                        long timestamp = System.currentTimeMillis();
                        return String.format(
                            "{\"redispatchOrderId\":\"%s\"," +
                            "\"entityId\":\"%s\"," +
                            "\"status\":\"RECEIVED\"," +
                            "\"timestamp\":\"%d\"}",
                            session.get("orderId"),
                            session.get("entityId"),
                            timestamp
                        );
                    }))
                    .check(status().is(202))
            )

            .exec(session -> {
                System.out.println("[" + session.get("entityId") + "] Acknowledgement sent for: " + session.get("orderId"));
                return session;
            })

            // Small pause before next order
            .pause(Duration.ofSeconds(2))
        )

        // Step 5: Close SSE connection
        .exec(
            sse("Close SSE Connection").close()
        )

        .exec(session -> {
            System.out.println("[" + session.get("entityId") + "] Workflow completed, connection closed");
            return session;
        });

    // Load injection profile
    {
        LoadTestConfig.printConfiguration();

        setUp(
            fullWorkflow.injectOpen(
                // Gradual ramp-up
                rampUsers(LoadTestConfig.WORKFLOW_USERS)
                    .during(Duration.ofSeconds(LoadTestConfig.RAMP_DURATION_SECONDS)),

                // Maintain constant rate
                constantUsersPerSec(20)
                    .during(Duration.ofSeconds(LoadTestConfig.HOLD_DURATION_SECONDS))
            )
        )
        .protocols(httpProtocol)
        .maxDuration(Duration.ofSeconds(LoadTestConfig.HOLD_DURATION_SECONDS + LoadTestConfig.RAMP_DURATION_SECONDS + 300))
        .assertions(
            // Response time assertions
            global().responseTime().percentile3().lt(LoadTestConfig.PERCENTILE_95_THRESHOLD_MS),
            global().responseTime().percentile4().lt(LoadTestConfig.PERCENTILE_99_THRESHOLD_MS),

            // Success rate assertion
            global().successfulRequests().percent().gte(LoadTestConfig.SUCCESS_RATE_THRESHOLD),

            // Specific request assertions
            details("GET Order Details").responseTime().percentile3().lt(1000),
            details("POST Acknowledgement").responseTime().percentile3().lt(500)
        );
    }
}
