package pl.tso.redispatch.service.model;

/**
 * Single data point in a time series with position and quantity bounds.
 * Matches OpenAPI schema: SeriesPoint
 */
public record SeriesPoint(
    int position,
    double quantityMax,
    double quantityMin
) {
}
