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
            // Shorter pause between orders (30-60s instead of 60-90s)
            pause(Duration.ofSeconds(
                ThreadLocalRandom.current().nextInt(30, 61)
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

            // Fetch order details (accept rate limiting)
            .exec(
                http("GET Order Details")
                    .get("#{resourceUrl}")
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
