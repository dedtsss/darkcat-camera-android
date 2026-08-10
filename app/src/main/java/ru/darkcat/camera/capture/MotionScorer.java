package ru.darkcat.camera.capture;

public final class MotionScorer {
    public static double angularSpeed(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** 1 is still, 0 is strongly moving. */
    public static double stability(double angularSpeedRadPerSecond) {
        double speed = Math.max(0.0, angularSpeedRadPerSecond);
        return 1.0 / (1.0 + 4.0 * speed * speed);
    }

    private MotionScorer() { }
}
