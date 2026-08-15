package ru.darkcat.camera.location;

/** Immutable quality and freshness policy shared by UI indication and capture gating. */
public final class GpsPolicy {
    public static final float DEFAULT_MAX_ACCURACY_METERS = 7.0f;
    public static final long DEFAULT_FRESH_AGE_MILLIS = 5_000L;
    public static final long DEFAULT_STALE_AGE_MILLIS = 15_000L;

    private final boolean strictCapture;
    private final float maxAccuracyMeters;
    private final long freshAgeMillis;
    private final long staleAgeMillis;

    public GpsPolicy(
            boolean strictCapture,
            float maxAccuracyMeters,
            long freshAgeMillis,
            long staleAgeMillis) {
        if (Float.isNaN(maxAccuracyMeters)
                || Float.isInfinite(maxAccuracyMeters)
                || maxAccuracyMeters <= 0.0f) {
            throw new IllegalArgumentException("maxAccuracyMeters must be positive");
        }
        if (freshAgeMillis < 0L) {
            throw new IllegalArgumentException("freshAgeMillis must be non-negative");
        }
        if (staleAgeMillis <= freshAgeMillis) {
            throw new IllegalArgumentException("staleAgeMillis must exceed freshAgeMillis");
        }
        this.strictCapture = strictCapture;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.freshAgeMillis = freshAgeMillis;
        this.staleAgeMillis = staleAgeMillis;
    }

    public static GpsPolicy strictDefault() {
        return new GpsPolicy(
                true,
                DEFAULT_MAX_ACCURACY_METERS,
                DEFAULT_FRESH_AGE_MILLIS,
                DEFAULT_STALE_AGE_MILLIS);
    }

    public static GpsPolicy advisoryDefault() {
        return new GpsPolicy(
                false,
                DEFAULT_MAX_ACCURACY_METERS,
                DEFAULT_FRESH_AGE_MILLIS,
                DEFAULT_STALE_AGE_MILLIS);
    }

    public boolean isStrictCapture() {
        return strictCapture;
    }

    public float getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public long getFreshAgeMillis() {
        return freshAgeMillis;
    }

    public long getStaleAgeMillis() {
        return staleAgeMillis;
    }

    public GpsPolicy withStrictCapture(boolean strict) {
        return new GpsPolicy(strict, maxAccuracyMeters, freshAgeMillis, staleAgeMillis);
    }

    public GpsPolicy withMaxAccuracyMeters(float meters) {
        return new GpsPolicy(strictCapture, meters, freshAgeMillis, staleAgeMillis);
    }
}
