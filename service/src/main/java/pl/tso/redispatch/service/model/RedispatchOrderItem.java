package pl.tso.redispatch.service.model;

import java.util.List;
import java.util.UUID;

/**
 * Technical redispatch order item with measurement details and time series.
 * Matches OpenAPI schema: RedispatchOrderItem
 */
public record RedispatchOrderItem(
    UUID redispatchingObjectMrid,
    String measurementUnit,  // MAW
    String curveType,        // A01
    List<SeriesPeriod> seriesPeriods
) {
}
