package ru.darkcat.camera.location;

/** Selects only a shutter-time, non-stale fix for durable capture metadata. */
public final class CaptureLocationSelector {
    public static LocationFix select(GpsState state) {
        if (state == null || state.getFix() == null) return null;
        if (state.getIndicator() == GpsIndicator.RED || state.getLocationAge() == LocationAge.STALE)
            return null;
        return state.getFix();
    }

    public static LocationFix validateAtCapture(LocationFix fix, long nowElapsedRealtimeNanos,
                                                long staleAgeMillis) {
        if (fix == null || nowElapsedRealtimeNanos < fix.getElapsedRealtimeNanos()
                || staleAgeMillis < 0L) return null;
        long ageNanos = nowElapsedRealtimeNanos - fix.getElapsedRealtimeNanos();
        long staleNanos = staleAgeMillis > Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE : staleAgeMillis * 1_000_000L;
        return ageNanos > staleNanos ? null : fix;
    }

    private CaptureLocationSelector() { }
}
