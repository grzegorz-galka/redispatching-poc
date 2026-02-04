package pl.tso.redispatch.loadtest.config;

import java.time.Duration;

/**
 * Configuration for load tests.
 * Values can be overridden via system properties.
 */
public class LoadTestConfig {

    // Base URL
    public static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");

    // User counts
    public static final int BASELINE_USERS = Integer.parseInt(System.getProperty("baselineUsers", "1000"));
    public static final int WORKFLOW_USERS = Integer.parseInt(System.getProperty("workflowUsers", "1000"));
    public static final int STRESS_MAX_USERS = Integer.parseInt(System.getProperty("stressMaxUsers", "1500"));

    // Duration settings (in seconds)
    public static final int RAMP_DURATION_SECONDS = Integer.parseInt(System.getProperty("rampUp", "300"));
    public static final int HOLD_DURATION_SECONDS = Integer.parseInt(System.getProperty("hold", "900"));
    public static final int BASELINE_HOLD_SECONDS = Integer.parseInt(System.getProperty("baselineHold", "600"));

    // Timeouts
    public static final Duration SSE_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration SSE_AWAIT_CONNECTED_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration SSE_AWAIT_ORDER_TIMEOUT = Duration.ofSeconds(120);
    public static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // Entity ID range
    public static final int MIN_ENTITY_ID = 1;
    public static final int MAX_ENTITY_ID = Integer.parseInt(System.getProperty("maxEntityId", "10"));

    // Performance thresholds
    public static final int PERCENTILE_95_THRESHOLD_MS = 2000;
    public static final int PERCENTILE_99_THRESHOLD_MS = 5000;
    public static final double SUCCESS_RATE_THRESHOLD = 95.0;

    private LoadTestConfig() {
        // Utility class
    }

    public static void printConfiguration() {
        System.out.println("==============================================");
        System.out.println("Load Test Configuration");
        System.out.println("==============================================");
        System.out.println("Base URL: " + BASE_URL);
        System.out.println("Baseline Users: " + BASELINE_USERS);
        System.out.println("Workflow Users: " + WORKFLOW_USERS);
        System.out.println("Stress Max Users: " + STRESS_MAX_USERS);
        System.out.println("Ramp Duration: " + RAMP_DURATION_SECONDS + "s");
        System.out.println("Hold Duration: " + HOLD_DURATION_SECONDS + "s");
        System.out.println("Entity ID Range: " + MIN_ENTITY_ID + " - " + MAX_ENTITY_ID);
        System.out.println("==============================================");
    }
}
