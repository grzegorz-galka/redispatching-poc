package pl.tso.redispatch.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Redispatch Gateway.
 * Routes all /v1/redispatch/** requests to the service module.
 */
@SpringBootApplication
public class RedispatchGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedispatchGatewayApplication.class, args);
    }
}
