package ru.darkcat.camera.point;

import java.util.Objects;

/** Immutable capture facts used for derived shooting-point grouping. */
public final class PointMedia {
    public final String mediaId;
    public final long timestampMillis;
    public final Double latitude;
    public final Double longitude;
    public final Float accuracyMeters;

    public PointMedia(String mediaId, long timestampMillis, Double latitude, Double longitude,
                      Float accuracyMeters) {
        this.mediaId = requireId(mediaId);
        if (timestampMillis < 0L) throw new IllegalArgumentException("timestampMillis must be non-negative");
        if (latitude != null && (latitude.isNaN() || latitude.isInfinite() || latitude < -90 || latitude > 90))
            throw new IllegalArgumentException("latitude out of range");
        if (longitude != null && (longitude.isNaN() || longitude.isInfinite() || longitude < -180 || longitude > 180))
            throw new IllegalArgumentException("longitude out of range");
        if (accuracyMeters != null && (accuracyMeters.isNaN() || accuracyMeters.isInfinite() || accuracyMeters < 0))
            throw new IllegalArgumentException("accuracy out of range");
        this.timestampMillis = timestampMillis;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
    }

    public boolean hasCoordinates() { return latitude != null && longitude != null; }

    private static String requireId(String value) {
        String id = Objects.requireNonNull(value, "mediaId").trim();
        if (id.isEmpty()) throw new IllegalArgumentException("mediaId must not be empty");
        return id;
    }
}
