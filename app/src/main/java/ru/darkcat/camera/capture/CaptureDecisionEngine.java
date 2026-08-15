package ru.darkcat.camera.capture;

public final class CaptureDecisionEngine {
    public static final long MAX_ADDITIONAL_DELAY_MS = 200L;
    public static final double MOTION_DELAY_THRESHOLD_RAD_S = 0.32;

    public static Decision decide(CaptureMode mode, double angularSpeedRadPerSecond,
                                  boolean goodCandidateAlreadyAvailable) {
        if (mode == CaptureMode.MAX_SPEED || goodCandidateAlreadyAvailable
                || angularSpeedRadPerSecond < MOTION_DELAY_THRESHOLD_RAD_S) {
            return new Decision(0L, "immediate");
        }
        double excess = Math.min(1.0, (angularSpeedRadPerSecond - MOTION_DELAY_THRESHOLD_RAD_S) / 1.2);
        long delay = 100L + Math.round(100L * excess);
        return new Decision(Math.min(MAX_ADDITIONAL_DELAY_MS, delay), "brief_stabilisation_window");
    }

    public static final class Decision {
        public final long delayMillis;
        public final String reason;
        Decision(long delayMillis, String reason) { this.delayMillis = delayMillis; this.reason = reason; }
        public boolean isImmediate() { return delayMillis == 0L; }
    }

    private CaptureDecisionEngine() { }
}
