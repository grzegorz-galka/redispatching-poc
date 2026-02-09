package pl.tso.redispatch.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import pl.tso.redispatch.service.model.RedispatchOrder;
import pl.tso.redispatch.service.model.event.ConnectedEvent;
import pl.tso.redispatch.service.model.event.HeartbeatEvent;
import pl.tso.redispatch.service.model.event.RedispatchOrderIssuedEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for managing SSE connections and event streaming.
 */
@Service
public class SseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterService.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration ORDER_MIN_INTERVAL = Duration.ofSeconds(20);
    private static final Duration ORDER_MAX_INTERVAL = Duration.ofSeconds(40);

    private final RedispatchOrderService orderService;
    private final ObjectMapper objectMapper;
    private final AtomicLong eventIdGenerator = new AtomicLong(1);
    private final Map<String, List<StoredEvent>> eventStore = new ConcurrentHashMap<>();

    public SseEmitterService(RedispatchOrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an SSE event stream for the given entity.
     * Supports event replay via lastEventId parameter.
     * Event data is serialized as JSON strings for client compatibility.
     */
    public Flux<ServerSentEvent<?>> createEventStream(String entityId, String lastEventId) {
        log.info("Creating SSE stream for entity: {}, lastEventId: {}", entityId, lastEventId);

        eventStore.putIfAbsent(entityId, new ArrayList<>());

        // Replay missed events if lastEventId is provided
        Flux<ServerSentEvent<?>> replayFlux = Flux.empty();
        if (lastEventId != null && !lastEventId.isEmpty()) {
            try {
                long lastId = Long.parseLong(lastEventId);
                replayFlux = replayEvents(entityId, lastId);
            } catch (NumberFormatException e) {
                log.warn("Invalid lastEventId format: {}", lastEventId);
            }
        }

        // Send connected event immediately
        ConnectedEvent connectedEvent = ConnectedEvent.create();
        ServerSentEvent<?> connectedSse = createSse(connectedEvent, "connected", entityId);

        // Heartbeat events every 30 seconds
        Flux<ServerSentEvent<?>> heartbeatFlux = Flux.interval(HEARTBEAT_INTERVAL)
            .map(tick -> {
                HeartbeatEvent heartbeat = HeartbeatEvent.create();
                return createSse(heartbeat, "heartbeat", entityId);
            });

        // Order issued events with random intervals (60-90 seconds)
        Flux<ServerSentEvent<?>> orderFlux = Flux.interval(
            randomDuration(),
            randomDuration()
        ).map(tick -> {
            RedispatchOrder order = orderService.generateMockOrder(entityId);
            RedispatchOrderIssuedEvent event = RedispatchOrderIssuedEvent.create(
                order.redispatchOrderId(),
                order.entityId()
            );
            return createSse(event, "ORDER_ISSUED", entityId);
        });

        // Combine all streams
        return Flux.concat(
            replayFlux,
            Flux.just(connectedSse),
            Flux.merge(heartbeatFlux, orderFlux)
        );
    }

    private ServerSentEvent<?> createSse(Object data, String eventType, String entityId) {
        String eventId = String.valueOf(eventIdGenerator.getAndIncrement());

        // Store event for replay
        storeEvent(entityId, eventId, data, eventType);

        // Convert to Map for proper JSON serialization without double-encoding
        try {
            // Convert the object to a Map structure that Jackson will serialize inline
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = objectMapper.convertValue(data, Map.class);
            return ServerSentEvent.builder(dataMap)
                .id(eventId)
                .event(eventType)
                .build();
        } catch (Exception e) {
            log.error("Failed to convert SSE event data to Map", e);
            // Fallback to original data if conversion fails
            return ServerSentEvent.builder(data)
                .id(eventId)
                .event(eventType)
                .build();
        }
    }

    private void storeEvent(String entityId, String eventId, Object data, String eventType) {
        List<StoredEvent> events = eventStore.get(entityId);
        if (events != null) {
            synchronized (events) {
                events.add(new StoredEvent(Long.parseLong(eventId), data, eventType));
                // Keep only last 100 events per entity to prevent memory issues
                if (events.size() > 100) {
                    events.remove(0);
                }
            }
        }
    }

    private Flux<ServerSentEvent<?>> replayEvents(String entityId, long lastEventId) {
        List<StoredEvent> events = eventStore.get(entityId);
        if (events == null || events.isEmpty()) {
            return Flux.empty();
        }

        List<ServerSentEvent<?>> replayEvents = new ArrayList<>();
        synchronized (events) {
            for (StoredEvent stored : events) {
                if (stored.id > lastEventId) {
                    try {
                        // Convert to Map for proper JSON serialization
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dataMap = objectMapper.convertValue(stored.data, Map.class);
                        replayEvents.add(ServerSentEvent.builder(dataMap)
                            .id(String.valueOf(stored.id))
                            .event(stored.eventType)
                            .build());
                    } catch (Exception e) {
                        log.error("Failed to convert replay event data to Map", e);
                        // Fallback to original data
                        replayEvents.add(ServerSentEvent.builder(stored.data)
                            .id(String.valueOf(stored.id))
                            .event(stored.eventType)
                            .build());
                    }
                }
            }
        }

        log.info("Replaying {} events for entity {} after event ID {}", replayEvents.size(), entityId, lastEventId);
        return Flux.fromIterable(replayEvents);
    }

    private Duration randomDuration() {
        long seconds = ThreadLocalRandom.current().nextLong(
            ORDER_MIN_INTERVAL.getSeconds(),
            ORDER_MAX_INTERVAL.getSeconds() + 1
        );
        return Duration.ofSeconds(seconds);
    }

    private record StoredEvent(long id, Object data, String eventType) {
    }
}
