package pl.tso.redispatch.loadtest.scenarios;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import pl.tso.redispatch.loadtest.config.LoadTestConfig;
import pl.tso.redispatch.loadtest.utils.EntityIdFeeder;
import pl.tso.redispatch.loadtest.utils.SseDataExtractor;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Stress test scenario - finds system limits.
 *
 * This scenario:
 * - Gradually increases load beyond normal capacity
 * - Tests system behavior under extreme conditions
 * - Identifies breaking points and bottlenecks
 *
 * Load profile:
 * - Phase 1: Ramp to 500 users (1 min), hold (2 min)
 * - Phase 2: Ramp to 1000 users (1 min), hold (3 min)
 * - Phase 3: Ramp to 1500 users (1 min), hold (5 min)
 *
 * Purpose: Determine system capacity and failure modes
 */
public class StressTestScenario extends Simulation {

    // HTTP Protocol configuration with higher limits
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(LoadTestConfig.BASE_URL)
        .acceptHeader("application/json, text/event-stream")
        .contentTypeHeader("application/json")
        .shareConnections()
        .maxConnectionsPerHost(3000);

    // Scenario definition - same as full workflow but more aggressive
    ScenarioBuilder stressTest = scenario("Stress Test - High Load")
        .feed(EntityIdFeeder.sequential())  // Sequential to distribute evenly

        // Open SSE connection and wait for connected event
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

        // Process orders (up to 5 during stress test)
        .repeat(5, "orderCount").on(
            // Wait for ORDER_ISSUED event from SSE stream
            // Use matching() to filter only ORDER_ISSUED events (ignore heartbeats)
            exec(
                sse("Open SSE Connection").setCheck()
                    .await(LoadTestConfig.SSE_AWAIT_ORDER_TIMEOUT).on(
                        sse.checkMessage("ORDER_ISSUED")
                            .matching(jsonPath("$.event").is("ORDER_ISSUED"))
                            .check(jsonPath("$.data").transform(SseDataExtractor.field("redispatchOrderId")).saveAs("orderId"))
                            .check(jsonPath("$.data").transform(SseDataExtractor.field("resourceUrl")).saveAs("resourceUrl"))
                    )
            )

            // Fetch order details (accept rate limiting)
            .exec(
                http("GET Order Details")
                    .get("/v1#{resourceUrl}")
                    .check(status().in(200, 429, 503).saveAs("httpStatus"))
                    .check(jsonPath("$.redispatchOrderId").optional())
            )

            // Send acknowledgement (only if GET succeeded with 200)
            .doIf(session -> {
                Integer statusCode = session.getInt("httpStatus");
                return statusCode != null && statusCode == 200;
            }).then(
                exec(
                    http("POST Acknowledgement")
                        .post("/v1#{resourceUrl}/acknowledgement")
                        .body(StringBody(session -> {
                            return String.format(
                                "{\"redispatchOrderId\":\"%s\"," +
                                "\"entityId\":\"%s\"," +
                                "\"status\":\"RECEIVED\"," +
                                "\"reason\":null}",
                                session.get("orderId"),
                                session.get("entityId")
                            );
                        }))
                        .check(status().in(202, 429, 503))
                )
            )

            // Minimal pause
            .pause(Duration.ofMillis(500))
        )

        // Close connection
        .exec(
            sse("Close SSE Connection").close()
        );

    // Aggressive load injection profile
    {
        LoadTestConfig.printConfiguration();

        System.out.println("==============================================");
        System.out.println("STRESS TEST LOAD PROFILE");
        System.out.println("==============================================");
        System.out.println("Warm-up: 10 seconds");
        System.out.println("Phase 1: Ramp to 500 users over 1 min");
        System.out.println("Phase 2: Hold 500 users for 2 min");
        System.out.println("Phase 3: Ramp to 1000 users over 1 min");
        System.out.println("Phase 4: Hold 1000 users for 3 min");
        System.out.println("Phase 5: Ramp to 1500 users over 1 min");
        System.out.println("Phase 6: Hold 1500 users for 5 min");
        System.out.println("Total duration: ~13 minutes");
        System.out.println("==============================================");

        setUp(
            stressTest.injectOpen(
                nothingFor(Duration.ofSeconds(10)),  // Warm-up

                // Phase 1: Ramp to 500
                rampUsers(500).during(Duration.ofMinutes(1)),

                // Phase 2: Hold 500
                constantUsersPerSec(25).during(Duration.ofMinutes(2)),

                // Phase 3: Ramp to 1000 (add 500 more)
                rampUsers(500).during(Duration.ofMinutes(1)),

                // Phase 4: Hold 1000
                constantUsersPerSec(50).during(Duration.ofMinutes(3)),

                // Phase 5: Ramp to 1500 (add 500 more)
                rampUsers(500).during(Duration.ofMinutes(1)),

                // Phase 6: Hold 1500 (peak load)
                constantUsersPerSec(75).during(Duration.ofMinutes(5))
            )
        )
        .protocols(httpProtocol)
        .maxDuration(Duration.ofMinutes(20))
        .assertions(
            // Relaxed assertions for stress test
            global().responseTime().percentile4().lt(LoadTestConfig.PERCENTILE_99_THRESHOLD_MS),
            global().successfulRequests().percent().gte(90.0)  // Accept 10% failures under stress
        );
    }
}
