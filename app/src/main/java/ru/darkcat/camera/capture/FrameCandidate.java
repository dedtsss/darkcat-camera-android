package ru.darkcat.camera.capture;

/** Lightweight preview-analysis result; full-resolution frames are deliberately not retained. */
public final class FrameCandidate {
    public final long timestampNanos;
    public final double sharpness;
    public final double angularSpeedRadPerSecond;
    public final Integer afState;
    public final Integer aeState;
    public final Integer awbState;

    public FrameCandidate(long timestampNanos, double sharpness, double angularSpeedRadPerSecond,
                          Integer afState, Integer aeState, Integer awbState) {
        this.timestampNanos = timestampNanos;
        this.sharpness = Math.max(0.0, sharpness);
        this.angularSpeedRadPerSecond = Math.max(0.0, angularSpeedRadPerSecond);
        this.afState = afState;
        this.aeState = aeState;
        this.awbState = awbState;
    }
}
