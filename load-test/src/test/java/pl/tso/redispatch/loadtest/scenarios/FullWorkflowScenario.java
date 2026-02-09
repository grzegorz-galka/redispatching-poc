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
 * Full workflow load test - tests realistic user behavior.
 *
 * This scenario:
 * 1. Opens SSE connection and waits for 'connected' event
 * 2. Waits for ORDER_ISSUED events via SSE stream
 * 3. Fetches order details via GET using resourceUrl from event
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

        // Step 2: Process orders as they arrive via SSE (3 orders)
        .repeat(3, "orderCount").on(
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

            .exec(session -> {
                System.out.println("[" + session.get("entityId") + "] Received ORDER_ISSUED for: " + session.get("orderId"));
                return session;
            })

            // Step 3: Fetch order details using resourceUrl from event
            .exec(
                http("GET Order Details")
                    .get("/v1#{resourceUrl}")
                    .check(status().is(200))
                    .check(jsonPath("$.redispatchOrderId").exists())
                    .check(jsonPath("$.entityId").exists())
                    .check(jsonPath("$.redispatchOrderReason").optional().saveAs("orderReason"))
            )

            .exec(session -> {
                System.out.println("[" + session.get("entityId") + "] Order fetched: " + session.get("orderId"));
                return session;
            })

            // Step 4: Send acknowledgement
            .exec(
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
            sse("Open SSE Connection").close()
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
            // Success rate assertion (applies to all operations)
            global().successfulRequests().percent().gte(LoadTestConfig.SUCCESS_RATE_THRESHOLD),

            // SSE connection establishment should be fast
            details("Open SSE Connection").responseTime().percentile3().lt(5000),

            // REST API response time assertions
            // Note: SSE "Wait for ORDER_ISSUED" is excluded as it depends on event generation interval (60-90s)
            details("GET Order Details").responseTime().percentile3().lt(1000),
            details("POST Acknowledgement").responseTime().percentile3().lt(500)
        );
    }
}
