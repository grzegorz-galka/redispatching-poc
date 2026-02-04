package pl.tso.redispatch.client.model;

import java.util.List;

public record SeriesPeriod(
    String direction,
    String autogenerationRedispatch,
    String resolution,
    TimeInterval timeInterval,
    List<SeriesPoint> seriesPoints
) {
}
