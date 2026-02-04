package pl.tso.redispatch.client.model;

import java.time.Instant;

public record TimeInterval(
    Instant startDt,
    Instant endDt
) {
}
