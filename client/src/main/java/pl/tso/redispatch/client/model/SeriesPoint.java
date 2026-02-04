package pl.tso.redispatch.client.model;

public record SeriesPoint(
    int position,
    double quantityMax,
    double quantityMin
) {
}
