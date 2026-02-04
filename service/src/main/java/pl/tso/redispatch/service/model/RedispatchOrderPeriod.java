package pl.tso.redispatch.service.model;

import java.time.Instant;

/**
 * Period of the redispatch order with start and end dates.
 * Matches OpenAPI schema: RedispatchOrderPeriod
 */
public record RedispatchOrderPeriod(
    Instant startDt,
    Instant endDt
) {
}
