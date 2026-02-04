package pl.tso.redispatch.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Redispatch Service.
 * Implements SSE streaming and REST endpoints for TSO redispatching orders.
 */
@SpringBootApplication
public class RedispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedispatchServiceApplication.class, args);
    }
}
