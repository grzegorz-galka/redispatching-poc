package pl.tso.redispatch.client.model;

import java.util.List;
import java.util.UUID;

public record RedispatchOrderItem(
    UUID redispatchingObjectMrid,
    String measurementUnit,
    String curveType,
    List<SeriesPeriod> seriesPeriods
) {
}
