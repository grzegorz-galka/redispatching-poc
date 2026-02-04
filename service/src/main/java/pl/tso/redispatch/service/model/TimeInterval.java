package pl.tso.redispatch.service.model;

import java.time.Instant;

/**
 * Time interval with start and end dates.
 * Matches OpenAPI schema: TimeInterval
 */
public record TimeInterval(
    Instant startDt,
    Instant endDt
) {
}
