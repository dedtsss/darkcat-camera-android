package ru.darkcat.camera.location;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Minimal process-local bridge for diagnostics/notification code that cannot own
 * the locker controller. Coordinates are intentionally excluded.
 */
public final class LocationSnapshotStore {
    public static final class Snapshot {
        public final String provider;
        public final Float accuracyMeters;
        private final long elapsedRealtimeMillis;

        private Snapshot(String provider, Float accuracyMeters, long elapsedRealtimeMillis) {
            this.provider = Objects.requireNonNull(provider, "provider");
            this.accuracyMeters = accuracyMeters;
            this.elapsedRealtimeMillis = elapsedRealtimeMillis;
        }

        public long ageMillis(long nowElapsedRealtimeMillis) {
            if (nowElapsedRealtimeMillis < elapsedRealtimeMillis) {
                return Long.MAX_VALUE;
            }
            return nowElapsedRealtimeMillis - elapsedRealtimeMillis;
        }
    }

    private static volatile boolean lockerRunning;
    private static volatile Snapshot latest;
    private static volatile LocationFix latestFix;

    public static boolean isLockerRunning() {
        return lockerRunning;
    }

    public static Snapshot latest() {
        return latest;
    }

    /** Internal capture metadata snapshot; diagnostics deliberately exposes no coordinates. */
    public static LocationFix latestFix() { return latestFix; }

    static void setLockerRunning(boolean running) {
        lockerRunning = running;
    }

    static void update(LocationFix fix) {
        if (fix == null) {
            return;
        }
        latest = new Snapshot(
                fix.getProvider(),
                fix.hasAccuracy() ? fix.getAccuracyMeters() : null,
                TimeUnit.NANOSECONDS.toMillis(fix.getElapsedRealtimeNanos()));
        latestFix = fix;
    }

    static void resetForTests() {
        lockerRunning = false;
        latest = null;
        latestFix = null;
    }

    private LocationSnapshotStore() { }
}
