package pl.tso.redispatch.loadtest.scenarios;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import pl.tso.redispatch.loadtest.config.LoadTestConfig;
import pl.tso.redispatch.loadtest.utils.EntityIdFeeder;
import pl.tso.redispatch.loadtest.utils.SseDataExtractor;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.sse;

/**
 * Baseline SSE load test - tests connection capacity.
 *
 * This scenario:
 * 1. Opens SSE connections
 * 2. Waits for 'connected' event
 * 3. Keeps connections alive (receiving heartbeats)
 * 4. Closes connections after test duration
 *
 * Purpose: Measure SSE connection capacity and stability
 */
public class SseBaselineScenario extends Simulation {

    // HTTP Protocol configuration
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(LoadTestConfig.BASE_URL)
        .acceptHeader("text/event-stream")
        .shareConnections()
        .maxConnectionsPerHost(2000);

    // Scenario definition
    ScenarioBuilder sseBaseline = scenario("SSE Baseline - Connection Test")
        .feed(EntityIdFeeder.random())

        // Open SSE connection and wait for connected event
        // Note: Gatling wraps SSE messages as {"event":"...", "id":"...", "data":"..."}
        // The data field contains escaped JSON string that we parse using SseDataExtractor
        .exec(
            sse("Open SSE and Await Connected")
                .get("/v1/redispatch/#{entityId}/stream")
                .await(LoadTestConfig.SSE_AWAIT_CONNECTED_TIMEOUT).on(
                    sse.checkMessage("connected")
                        // Check event type at root level
                        .check(jsonPath("$.event").is("connected"))
                        // Parse the escaped JSON data and extract fields
                        .check(jsonPath("$.data").transform(SseDataExtractor.fieldEquals("eventType", "connected")).notNull())
                        .check(jsonPath("$.data").transform(SseDataExtractor.field("connectionId")).saveAs("connectionId"))
                        .check(jsonPath("$.data").transform(SseDataExtractor.field("timestamp")).saveAs("connectedAt"))
                )
        )

        .exec(session -> {
            System.out.println("[" + session.get("entityId") + "] Connected: " +
                session.get("connectionId"));
            return session;
        })

        // Keep connection alive - wait for duration
        .pause(Duration.ofSeconds(LoadTestConfig.BASELINE_HOLD_SECONDS))

        // Close connection
        .exec(
            sse("Close SSE Connection").close()
        )

        .exec(session -> {
            System.out.println("[" + session.get("entityId") + "] Connection closed after " +
                LoadTestConfig.BASELINE_HOLD_SECONDS + "s");
            return session;
        });

    // Load injection profile
    {
        LoadTestConfig.printConfiguration();

        setUp(
            sseBaseline.injectOpen(
                // Ramp up gradually to target users
                rampUsers(LoadTestConfig.BASELINE_USERS)
                    .during(Duration.ofSeconds(LoadTestConfig.RAMP_DURATION_SECONDS))
            )
        )
        .protocols(httpProtocol)
        .assertions(
            // At least 95% of connections should succeed
            global().successfulRequests().percent().gte(LoadTestConfig.SUCCESS_RATE_THRESHOLD)
        );
    }
}
