package pl.tso.redispatch.client.model;

import java.time.Instant;

public record RedispatchOrderPeriod(
    Instant startDt,
    Instant endDt
) {
}
