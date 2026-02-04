package pl.tso.redispatch.service.service;

import org.springframework.stereotype.Service;
import pl.tso.redispatch.service.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for generating and managing mock redispatch orders.
 */
@Service
public class RedispatchOrderService {

    private static final DateTimeFormatter ORDER_ID_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String[] REASONS = {"B", "S"};
    private static final String[] DIRECTIONS = {"G", "P"};
    private static final String[] RESOLUTIONS = {"P1D", "PT60M", "PT15M"};

    private final AtomicLong orderSequence = new AtomicLong(1);
    private final Map<String, RedispatchOrder> orderStore = new ConcurrentHashMap<>();

    /**
     * Generates a mock redispatch order for the given entity.
     */
    public RedispatchOrder generateMockOrder(String entityId) {
        String orderId = generateOrderId();
        RedispatchOrder order = createRandomOrder(orderId, entityId);
        orderStore.put(orderId, order);
        return order;
    }

    /**
     * Retrieves an order by ID.
     */
    public RedispatchOrder getOrder(String orderId) {
        return orderStore.get(orderId);
    }

    /**
     * Logs an acknowledgement (simple implementation for mock).
     */
    public void logAcknowledgement(RedispatchAcknowledgement acknowledgement) {
        System.out.println("Acknowledgement received: " + acknowledgement);
    }

    private String generateOrderId() {
        long seq = orderSequence.getAndIncrement();
        String date = LocalDate.now().format(ORDER_ID_DATE_FORMAT);
        return seq + "/I/" + date;
    }

    private RedispatchOrder createRandomOrder(String orderId, String entityId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Instant now = Instant.now();
        Instant periodStart = now.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Instant periodEnd = periodStart.plus(random.nextInt(12, 48), ChronoUnit.HOURS);

        RedispatchOrderPeriod period = new RedispatchOrderPeriod(periodStart, periodEnd);

        int itemCount = random.nextInt(1, 4); // 1-3 items
        List<RedispatchOrderItem> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            items.add(createRandomOrderItem(periodStart, periodEnd, random));
        }

        String reason = REASONS[random.nextInt(REASONS.length)];

        return new RedispatchOrder(
            orderId,
            entityId,
            now,
            reason,
            period,
            items
        );
    }

    private RedispatchOrderItem createRandomOrderItem(Instant periodStart, Instant periodEnd, ThreadLocalRandom random) {
        UUID mrid = UUID.randomUUID();

        List<SeriesPeriod> seriesPeriods = new ArrayList<>();
        seriesPeriods.add(createRandomSeriesPeriod(periodStart, periodEnd, random));

        return new RedispatchOrderItem(
            mrid,
            "MAW",
            "A01",
            seriesPeriods
        );
    }

    private SeriesPeriod createRandomSeriesPeriod(Instant start, Instant end, ThreadLocalRandom random) {
        String direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        String autogen = random.nextBoolean() ? "1" : "0";
        String resolution = RESOLUTIONS[random.nextInt(RESOLUTIONS.length)];

        TimeInterval timeInterval = new TimeInterval(start, end);

        // Generate series points based on resolution
        int pointCount = calculatePointCount(start, end, resolution);
        List<SeriesPoint> points = new ArrayList<>();
        for (int i = 1; i <= pointCount; i++) {
            double min = random.nextDouble(50.0, 150.0);
            double max = min + random.nextDouble(10.0, 50.0); // Ensure max > min
            points.add(new SeriesPoint(i, max, min));
        }

        return new SeriesPeriod(direction, autogen, resolution, timeInterval, points);
    }

    private int calculatePointCount(Instant start, Instant end, String resolution) {
        long hours = ChronoUnit.HOURS.between(start, end);
        return switch (resolution) {
            case "P1D" -> (int) Math.ceil(hours / 24.0);
            case "PT60M" -> (int) hours;
            case "PT15M" -> (int) (hours * 4);
            default -> 1;
        };
    }
}
