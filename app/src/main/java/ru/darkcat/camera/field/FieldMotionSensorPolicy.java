package ru.darkcat.camera.field;

/** Chooses the lowest-power available motion source for Field standby. */
public final class FieldMotionSensorPolicy {
    public enum Mode {
        TRIGGER_STATIONARY_AND_SIGNIFICANT,
        TRIGGER_STATIONARY_ONLY,
        TRIGGER_SIGNIFICANT_ONLY,
        ACCELEROMETER_FALLBACK
    }

    public static Mode choose(boolean stationaryDetector, boolean significantMotion) {
        if (stationaryDetector && significantMotion) return Mode.TRIGGER_STATIONARY_AND_SIGNIFICANT;
        if (stationaryDetector) return Mode.TRIGGER_STATIONARY_ONLY;
        if (significantMotion) return Mode.TRIGGER_SIGNIFICANT_ONLY;
        return Mode.ACCELEROMETER_FALLBACK;
    }

    private FieldMotionSensorPolicy() { }
}
