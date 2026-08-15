package ru.darkcat.camera.location;

import java.util.Objects;

/**
 * Android-free location value.  The elapsed-realtime timestamp is authoritative
 * for freshness; wall time is retained only for metadata and diagnostics.
 */
public final class LocationFix {
    public static final float ACCURACY_UNAVAILABLE = Float.NaN;

    private final double latitude;
    private final double longitude;
    private final float accuracyMeters;
    private final long elapsedRealtimeNanos;
    private final long wallTimeMillis;
    private final String provider;

    public LocationFix(
            double latitude,
            double longitude,
            float accuracyMeters,
            long elapsedRealtimeNanos,
            long wallTimeMillis,
            String provider) {
        if (!isFinite(latitude) || latitude < -90.0d || latitude > 90.0d) {
            throw new IllegalArgumentException("latitude out of range");
        }
        if (!isFinite(longitude) || longitude < -180.0d || longitude > 180.0d) {
            throw new IllegalArgumentException("longitude out of range");
        }
        if (isFinite(accuracyMeters) && accuracyMeters < 0.0f) {
            throw new IllegalArgumentException("accuracy must be non-negative");
        }
        if (elapsedRealtimeNanos < 0L) {
            throw new IllegalArgumentException("elapsedRealtimeNanos must be non-negative");
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.elapsedRealtimeNanos = elapsedRealtimeNanos;
        this.wallTimeMillis = wallTimeMillis;
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public float getAccuracyMeters() {
        return accuracyMeters;
    }

    public boolean hasAccuracy() {
        return isFinite(accuracyMeters);
    }

    public long getElapsedRealtimeNanos() {
        return elapsedRealtimeNanos;
    }

    public long getWallTimeMillis() {
        return wallTimeMillis;
    }

    public String getProvider() {
        return provider;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
