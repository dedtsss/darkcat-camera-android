package ru.darkcat.camera.field;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Lifecycle foundation for a real camera/location foreground service.
 *
 * <p>Camera FGS startup is deliberately exposed only as
 * {@link #enableFromVisibleActivity(FieldModeConfig)}. Process or boot restore
 * never invokes the camera runtime automatically; it enters a state that asks
 * the user to reopen/confirm Field Mode.</p>
 */
public final class FieldModeController {
    public interface Runtime {
        /** Called only for an explicit action while the owning Activity is visible. */
        void startFromVisibleActivity(FieldModeConfig config);

        void stop();
    }

    public interface Listener {
        void onFieldModeChanged(FieldModeSnapshot snapshot);
    }

    private final Runtime runtime;
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private FieldModeSnapshot snapshot = FieldModeSnapshot.disabled();

    public FieldModeController(Runtime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public void enableFromVisibleActivity(FieldModeConfig config) {
        Objects.requireNonNull(config, "config");
        synchronized (this) {
            if (snapshot.getState() == FieldModeState.STARTING
                    || snapshot.getState() == FieldModeState.ACTIVE
                    || snapshot.getState() == FieldModeState.DEGRADED) {
                return;
            }
            snapshot = new FieldModeSnapshot(
                    FieldModeState.STARTING, config, false, false, null);
        }
        dispatch();
        try {
            runtime.startFromVisibleActivity(config);
        } catch (RuntimeException failure) {
            reportRuntimeError("FGS_START_FAILED");
        }
    }

    /** Safe recovery for process recreation: never starts a camera FGS from background. */
    public void restoreAfterProcessRecreation(boolean wasEnabled, FieldModeConfig config) {
        restoreWithoutCameraAutostart(wasEnabled, config);
    }

    /** Safe recovery for BOOT_COMPLETED: camera startup requires a new visible user action. */
    public void restoreAfterBoot(boolean wasEnabled, FieldModeConfig config) {
        restoreWithoutCameraAutostart(wasEnabled, config);
    }

    public void reportRuntimeReady(boolean cameraReady, boolean gpsLockerRunning) {
        synchronized (this) {
            FieldModeState current = snapshot.getState();
            if (current != FieldModeState.STARTING
                    && current != FieldModeState.ACTIVE
                    && current != FieldModeState.DEGRADED) {
                return;
            }
            FieldModeConfig config = snapshot.getConfig();
            boolean gpsReady = config == null
                    || !config.isGpsLockerEnabled()
                    || gpsLockerRunning;
            FieldModeState next = cameraReady && gpsReady
                    ? FieldModeState.ACTIVE
                    : FieldModeState.DEGRADED;
            snapshot = new FieldModeSnapshot(
                    next, config, cameraReady, gpsLockerRunning, null);
        }
        dispatch();
    }

    public void reportRuntimeError(String stableErrorCode) {
        Objects.requireNonNull(stableErrorCode, "stableErrorCode");
        synchronized (this) {
            if (snapshot.getState() == FieldModeState.DISABLED
                    || snapshot.getState() == FieldModeState.STOPPING) {
                return;
            }
            snapshot = new FieldModeSnapshot(
                    FieldModeState.ERROR,
                    snapshot.getConfig(),
                    false,
                    false,
                    stableErrorCode);
        }
        dispatch();
    }

    public void disable() {
        synchronized (this) {
            if (snapshot.getState() == FieldModeState.DISABLED) {
                return;
            }
            snapshot = new FieldModeSnapshot(
                    FieldModeState.STOPPING,
                    snapshot.getConfig(),
                    false,
                    false,
                    null);
        }
        dispatch();
        try {
            runtime.stop();
        } finally {
            synchronized (this) {
                snapshot = FieldModeSnapshot.disabled();
            }
            dispatch();
        }
    }

    public synchronized FieldModeSnapshot getSnapshot() {
        return snapshot;
    }

    public void addListener(Listener listener) {
        Listener checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        checked.onFieldModeChanged(getSnapshot());
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void restoreWithoutCameraAutostart(boolean wasEnabled, FieldModeConfig config) {
        if (!wasEnabled) {
            synchronized (this) {
                snapshot = FieldModeSnapshot.disabled();
            }
            dispatch();
            return;
        }
        Objects.requireNonNull(config, "config");
        synchronized (this) {
            snapshot = new FieldModeSnapshot(
                    FieldModeState.AWAITING_VISIBLE_START,
                    config,
                    false,
                    false,
                    null);
        }
        dispatch();
    }

    private void dispatch() {
        FieldModeSnapshot value = getSnapshot();
        FieldModeConfig config = value.getConfig();
        FieldModeState.updateDiagnostics(
                value.getState(),
                config != null && config.isVolumeUpShutterEnabled());
        for (Listener listener : listeners) {
            try {
                listener.onFieldModeChanged(value);
            } catch (RuntimeException ignored) {
                // Runtime lifecycle remains authoritative if a UI observer disappears.
            }
        }
    }
}
