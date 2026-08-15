package ru.darkcat.camera.location;

import java.util.Objects;

/** Result returned synchronously before asking the camera engine to capture. */
public final class CaptureDecision {
    private final boolean allowed;
    private final CaptureBlockReason blockReason;
    private final GpsState gpsState;

    private CaptureDecision(boolean allowed, CaptureBlockReason blockReason, GpsState gpsState) {
        this.allowed = allowed;
        this.blockReason = Objects.requireNonNull(blockReason, "blockReason");
        this.gpsState = Objects.requireNonNull(gpsState, "gpsState");
    }

    public static CaptureDecision allowed(GpsState gpsState) {
        return new CaptureDecision(true, CaptureBlockReason.NONE, gpsState);
    }

    public static CaptureDecision blocked(CaptureBlockReason reason, GpsState gpsState) {
        if (reason == CaptureBlockReason.NONE) {
            throw new IllegalArgumentException("blocked decision requires a reason");
        }
        return new CaptureDecision(false, reason, gpsState);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public CaptureBlockReason getBlockReason() {
        return blockReason;
    }

    public GpsState getGpsState() {
        return gpsState;
    }
}
