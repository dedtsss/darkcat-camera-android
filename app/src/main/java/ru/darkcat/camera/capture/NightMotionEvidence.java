package ru.darkcat.camera.capture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, privacy-safe motion evidence for one OEM Night capture. */
public final class NightMotionEvidence {
    public static final float MOVING_THRESHOLD_MPS2 = 1.25f;

    private final String sensorMode;
    private final String state;
    private final boolean moving;
    private final long lastSampleElapsedMs;
    private final long windowMs;
    private final long sampleCount;
    private final long movingSampleCount;
    private final long stableSampleCount;
    private final float meanDeltaMps2;
    private final float peakDeltaMps2;

    private NightMotionEvidence(String sensorMode, String state, boolean moving,
                                long lastSampleElapsedMs, long windowMs, long sampleCount,
                                long movingSampleCount, long stableSampleCount,
                                float meanDeltaMps2, float peakDeltaMps2) {
        this.sensorMode = sensorMode;
        this.state = state;
        this.moving = moving;
        this.lastSampleElapsedMs = lastSampleElapsedMs;
        this.windowMs = windowMs;
        this.sampleCount = sampleCount;
        this.movingSampleCount = movingSampleCount;
        this.stableSampleCount = stableSampleCount;
        this.meanDeltaMps2 = meanDeltaMps2;
        this.peakDeltaMps2 = peakDeltaMps2;
    }

    public String sensorMode() { return sensorMode; }
    public String state() { return state; }
    public boolean moving() { return moving; }
    public long lastSampleElapsedMs() { return lastSampleElapsedMs; }

    public Map<String, Object> attributes() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("motion_sensor_mode", sensorMode);
        values.put("motion_sample_count", sampleCount);
        values.put("motion_moving_count", movingSampleCount);
        values.put("motion_stable_count", stableSampleCount);
        values.put("motion_mean_delta_mps2", meanDeltaMps2);
        values.put("motion_peak_delta_mps2", peakDeltaMps2);
        values.put("motion_window_ms", windowMs);
        return Collections.unmodifiableMap(values);
    }

    /** Mutable per-capture reducer. Callers may safely observe it from another callback thread. */
    public static final class Accumulator {
        private final String sensorMode;
        private final boolean linearAcceleration;
        private boolean hasGravity;
        private float gravityX;
        private float gravityY;
        private float gravityZ;
        private String state = "UNKNOWN";
        private boolean moving;
        private long firstSampleElapsedMs;
        private long lastSampleElapsedMs;
        private long sampleCount;
        private long movingSampleCount;
        private long stableSampleCount;
        private double deltaSum;
        private float peakDeltaMps2;

        public Accumulator(String sensorMode, boolean linearAcceleration) {
            this.sensorMode = sensorMode == null ? "NONE" : sensorMode;
            this.linearAcceleration = linearAcceleration;
        }

        public synchronized void reset() {
            hasGravity = false;
            gravityX = 0.0f;
            gravityY = 0.0f;
            gravityZ = 0.0f;
            state = "UNKNOWN";
            moving = false;
            firstSampleElapsedMs = 0L;
            lastSampleElapsedMs = 0L;
            sampleCount = 0L;
            movingSampleCount = 0L;
            stableSampleCount = 0L;
            deltaSum = 0.0d;
            peakDeltaMps2 = 0.0f;
        }

        public synchronized void observe(float x, float y, float z, long elapsedMs) {
            float deltaX = x;
            float deltaY = y;
            float deltaZ = z;
            if (!linearAcceleration) {
                // Remove gravity from the raw accelerometer so a still phone is STABLE.
                if (!hasGravity) {
                    gravityX = x;
                    gravityY = y;
                    gravityZ = z;
                    hasGravity = true;
                } else {
                    final float alpha = 0.8f;
                    gravityX = alpha * gravityX + (1.0f - alpha) * x;
                    gravityY = alpha * gravityY + (1.0f - alpha) * y;
                    gravityZ = alpha * gravityZ + (1.0f - alpha) * z;
                }
                deltaX = x - gravityX;
                deltaY = y - gravityY;
                deltaZ = z - gravityZ;
            }
            float delta = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
            boolean sampleMoving = delta >= MOVING_THRESHOLD_MPS2;
            state = sampleMoving ? "MOVING" : "STABLE";
            moving = sampleMoving;
            if (firstSampleElapsedMs == 0L) firstSampleElapsedMs = elapsedMs;
            lastSampleElapsedMs = elapsedMs;
            sampleCount++;
            if (sampleMoving) movingSampleCount++;
            else stableSampleCount++;
            deltaSum += delta;
            if (delta > peakDeltaMps2) peakDeltaMps2 = delta;
        }

        public synchronized NightMotionEvidence snapshot(long nowElapsedMs) {
            long window = firstSampleElapsedMs == 0L ? 0L
                    : Math.max(0L, nowElapsedMs - firstSampleElapsedMs);
            float mean = sampleCount == 0L ? 0.0f : (float) (deltaSum / sampleCount);
            return new NightMotionEvidence(sensorMode, state, moving, lastSampleElapsedMs, window,
                    sampleCount, movingSampleCount, stableSampleCount, mean, peakDeltaMps2);
        }
    }

    private NightMotionEvidence() { throw new AssertionError(); }
}
