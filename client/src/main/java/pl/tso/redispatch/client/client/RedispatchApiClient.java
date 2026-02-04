package pl.tso.redispatch.client.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pl.tso.redispatch.client.model.RedispatchAcknowledgement;
import pl.tso.redispatch.client.model.RedispatchOrder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST client for fetching orders and sending acknowledgements.
 */
@Component
public class RedispatchApiClient {

    private static final Logger log = LoggerFactory.getLogger(RedispatchApiClient.class);

    private final WebClient webClient;
    private final String gatewayUrl;

    public RedispatchApiClient(@Value("${client.gateway-url}") String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
        this.webClient = WebClient.builder()
            .baseUrl(gatewayUrl)
            .build();
    }

    /**
     * Fetch order details using the resource URL from the SSE event.
     * The resourceUrl is already URL-encoded by the server.
     * Build the full URL manually and use URI.create() to prevent WebClient from re-encoding.
     */
    public Mono<RedispatchOrder> getOrderByUrl(String resourceUrl) {
        String fullUrl = gatewayUrl + resourceUrl;
        log.info("Fetching order from: {}", fullUrl);

        return webClient.get()
            .uri(URI.create(fullUrl))
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(RedispatchOrder.class)
            .doOnNext(order -> log.info("Order received: {}", order.redispatchOrderId()))
            .doOnError(error -> log.error("Error fetching order: {}", error.getMessage()));
    }

    /**
     * Fetch order details by ID (alternative method for direct calls).
     * Encodes the order ID and builds the full URL with URI.create() to prevent double-encoding.
     */
    public Mono<RedispatchOrder> getOrder(String entityId, String redispatchOrderId) {
        String encodedOrderId = URLEncoder.encode(redispatchOrderId, StandardCharsets.UTF_8);
        String resourcePath = "/redispatch/" + entityId + "/orders/" + encodedOrderId;
        String fullUrl = gatewayUrl + resourcePath;

        log.info("Fetching order: {}", resourcePath);

        return webClient.get()
            .uri(URI.create(fullUrl))
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(RedispatchOrder.class)
            .doOnNext(order -> log.info("Order received: {}", order.redispatchOrderId()))
            .doOnError(error -> log.error("Error fetching order: {}", error.getMessage()));
    }

    /**
     * Send acknowledgement using the resource URL from the SSE event.
     * Appends /acknowledgement to the order resource URL.
     * Build the full URL manually and use URI.create() to prevent WebClient from re-encoding.
     */
    public Mono<Void> sendAcknowledgementByUrl(String resourceUrl, RedispatchAcknowledgement acknowledgement) {
        String ackPath = resourceUrl + "/acknowledgement";
        String fullUrl = gatewayUrl + ackPath;

        log.info("Sending acknowledgement: {} to {}", acknowledgement.status(), fullUrl);

        return webClient.post()
            .uri(URI.create(fullUrl))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(acknowledgement)
            .retrieve()
            .toBodilessEntity()
            .doOnSuccess(response -> log.info("Acknowledgement accepted: {}", response.getStatusCode()))
            .doOnError(error -> log.error("Error sending acknowledgement: {}", error.getMessage()))
            .then();
    }

    /**
     * Send acknowledgement for an order (alternative method for direct calls).
     * Encodes the order ID and builds the full URL with URI.create() to prevent double-encoding.
     */
    public Mono<Void> sendAcknowledgement(String entityId, String redispatchOrderId,
                                          RedispatchAcknowledgement acknowledgement) {
        String encodedOrderId = URLEncoder.encode(redispatchOrderId, StandardCharsets.UTF_8);
        String resourcePath = "/redispatch/" + entityId + "/orders/" + encodedOrderId + "/acknowledgement";
        String fullUrl = gatewayUrl + resourcePath;

        log.info("Sending acknowledgement: {} for order {}", acknowledgement.status(), redispatchOrderId);

        return webClient.post()
            .uri(URI.create(fullUrl))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(acknowledgement)
            .retrieve()
            .toBodilessEntity()
            .doOnSuccess(response -> log.info("Acknowledgement accepted: {}", response.getStatusCode()))
            .doOnError(error -> log.error("Error sending acknowledgement: {}", error.getMessage()))
            .then();
    }
}
