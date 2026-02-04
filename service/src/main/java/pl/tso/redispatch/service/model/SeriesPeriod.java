package pl.tso.redispatch.service.model;

import java.util.List;

/**
 * Series period containing direction, resolution, and time-series data points.
 * Matches OpenAPI schema: SeriesPeriod (nested in RedispatchOrderItem)
 */
public record SeriesPeriod(
    String direction,              // G or P
    String autogenerationRedispatch, // "0" or "1"
    String resolution,             // P1D, PT60M, PT15M
    TimeInterval timeInterval,
    List<SeriesPoint> seriesPoints
) {
}
