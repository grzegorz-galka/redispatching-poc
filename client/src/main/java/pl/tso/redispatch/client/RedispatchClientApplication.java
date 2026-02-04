package pl.tso.redispatch.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import pl.tso.redispatch.client.client.SseClient;

/**
 * Main application class for the Redispatch Client.
 * Demonstrates the complete workflow: connect to SSE, receive events, fetch orders, send acknowledgements.
 */
@SpringBootApplication
public class RedispatchClientApplication {

    private static final Logger log = LoggerFactory.getLogger(RedispatchClientApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(RedispatchClientApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(SseClient sseClient) {
        return args -> {
            log.info("Starting Redispatch Client...");
            log.info("Connecting to SSE stream...");

            // Connect to SSE stream and keep the application running
            sseClient.connect()
                .doOnError(error -> log.error("Fatal error in SSE connection: {}", error.getMessage()))
                .subscribe();

            // Keep application running
            log.info("Client is running. Press Ctrl+C to exit.");
        };
    }
}
