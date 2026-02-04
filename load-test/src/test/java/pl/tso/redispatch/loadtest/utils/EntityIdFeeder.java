package pl.tso.redispatch.loadtest.utils;

import pl.tso.redispatch.loadtest.config.LoadTestConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Provides entity IDs for load test scenarios.
 */
public class EntityIdFeeder {

    /**
     * Creates an infinite iterator that generates random entity IDs.
     */
    public static Iterator<Map<String, Object>> random() {
        return Stream.generate(() -> {
            int entityNumber = ThreadLocalRandom.current().nextInt(
                LoadTestConfig.MIN_ENTITY_ID,
                LoadTestConfig.MAX_ENTITY_ID + 1
            );
            String entityId = "ENT" + String.format("%02d", entityNumber);

            Map<String, Object> map = new HashMap<>();
            map.put("entityId", entityId);
            return map;
        }).iterator();
    }

    /**
     * Creates an infinite iterator that generates sequential entity IDs.
     */
    public static Iterator<Map<String, Object>> sequential() {
        return Stream.iterate(LoadTestConfig.MIN_ENTITY_ID, i -> {
            int next = i + 1;
            return next > LoadTestConfig.MAX_ENTITY_ID ? LoadTestConfig.MIN_ENTITY_ID : next;
        }).map(entityNumber -> {
            String entityId = "ENT" + String.format("%02d", entityNumber);
            Map<String, Object> map = new HashMap<>();
            map.put("entityId", entityId);
            return map;
        }).iterator();
    }

    private EntityIdFeeder() {
        // Utility class
    }
}
