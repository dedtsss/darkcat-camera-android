package ru.darkcat.camera.location;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Single process-local source of location truth for visible Camera, Field Mode, stamps and metadata.
 * The GPS Locker wins while it is running; otherwise the normal camera listener supplies the state.
 */
public final class LocationRepository {
    public interface Listener {
        void onLocationStateChanged(GpsState state);
    }

    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile GpsSnapshot cameraSnapshot = GpsSnapshot.stopped();
    private static volatile GpsSnapshot lockerSnapshot = GpsSnapshot.stopped();
    private static volatile boolean lockerRunning;

    public static void setCameraTracking(boolean tracking) {
        if (!tracking) cameraSnapshot = GpsSnapshot.stopped();
        else if (cameraSnapshot.getSourceStatus() == GpsSourceStatus.STOPPED)
            cameraSnapshot = GpsSnapshot.running(null);
        notifyListeners(null);
    }

    public static void publishCameraLocation(Location location) {
        if (location == null) return;
        publishCameraFix(toFix(location));
    }

    public static void publishCameraFix(LocationFix fix) {
        if (fix == null) return;
        cameraSnapshot = GpsSnapshot.running(fix);
        LocationSnapshotStore.update(fix);
        notifyListeners(null);
    }

    public static void publishCameraUnavailable(GpsSourceStatus status) {
        cameraSnapshot = status == GpsSourceStatus.RUNNING ? GpsSnapshot.running(null)
                : GpsSnapshot.unavailable(status);
        notifyListeners(null);
    }

    public static void publishLockerSnapshot(GpsSnapshot snapshot, boolean running) {
        lockerSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        lockerRunning = running;
        LocationSnapshotStore.setLockerRunning(running);
        if (snapshot.getFix() != null) LocationSnapshotStore.update(snapshot.getFix());
        notifyListeners(null);
    }

    public static boolean isLockerRunning() {
        return lockerRunning;
    }

    public static GpsSnapshot currentSnapshot() {
        return lockerRunning ? lockerSnapshot : cameraSnapshot;
    }

    public static GpsState currentState(Context context) {
        return new GpsStateEvaluator().evaluate(currentSnapshot(), SystemClock.elapsedRealtimeNanos(),
                GpsLockerService.policy(context));
    }

    public static CaptureDecision captureDecision(Context context) {
        return new GpsCapturePolicy().evaluate(currentSnapshot(), SystemClock.elapsedRealtimeNanos(),
                GpsLockerService.policy(context));
    }

    public static LocationFix latestFix() {
        GpsSnapshot snapshot = currentSnapshot();
        return snapshot == null ? null : snapshot.getFix();
    }

    public static Location asAndroidLocation() {
        LocationFix fix = latestFix();
        if (fix == null) return null;
        Location location = new Location(fix.getProvider().isEmpty() ? LocationManager.GPS_PROVIDER : fix.getProvider());
        location.setLatitude(fix.getLatitude());
        location.setLongitude(fix.getLongitude());
        if (fix.hasAccuracy()) location.setAccuracy(fix.getAccuracyMeters());
        location.setTime(fix.getWallTimeMillis());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
            location.setElapsedRealtimeNanos(fix.getElapsedRealtimeNanos());
        return location;
    }

    public static void addListener(Listener listener) {
        Listener checked = Objects.requireNonNull(listener, "listener");
        LISTENERS.add(checked);
        try { checked.onLocationStateChanged(null); } catch (RuntimeException ignored) { }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyListeners(GpsState ignoredState) {
        for (Listener listener : LISTENERS) {
            try { listener.onLocationStateChanged(ignoredState); } catch (RuntimeException ignored) { }
        }
    }

    private static LocationFix toFix(Location location) {
        return new LocationFix(location.getLatitude(), location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : LocationFix.ACCURACY_UNAVAILABLE,
                location.getElapsedRealtimeNanos(), location.getTime(),
                location.getProvider() == null ? "" : location.getProvider());
    }

    static void resetForTests() {
        cameraSnapshot = GpsSnapshot.stopped();
        lockerSnapshot = GpsSnapshot.stopped();
        lockerRunning = false;
        LISTENERS.clear();
    }

    private LocationRepository() { }
}
