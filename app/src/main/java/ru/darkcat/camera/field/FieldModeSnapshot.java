package ru.darkcat.camera.field;

import java.util.Objects;

/** Safe status value for UI/diagnostics; contains no coordinates or media details. */
public final class FieldModeSnapshot {
    private final FieldModeState state;
    private final FieldModeConfig config;
    private final boolean cameraReady;
    private final boolean gpsLockerRunning;
    private final String errorCode;

    FieldModeSnapshot(
            FieldModeState state,
            FieldModeConfig config,
            boolean cameraReady,
            boolean gpsLockerRunning,
            String errorCode) {
        this.state = Objects.requireNonNull(state, "state");
        this.config = config;
        this.cameraReady = cameraReady;
        this.gpsLockerRunning = gpsLockerRunning;
        this.errorCode = errorCode;
    }

    public static FieldModeSnapshot disabled() {
        return new FieldModeSnapshot(FieldModeState.DISABLED, null, false, false, null);
    }

    public FieldModeState getState() {
        return state;
    }

    public FieldModeConfig getConfig() {
        return config;
    }

    public boolean isCameraReady() {
        return cameraReady;
    }

    public boolean isGpsLockerRunning() {
        return gpsLockerRunning;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
