package ru.darkcat.camera.capture;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

/** Sensor sampler owned by one OEM Night capture, never process-lifetime monitoring. */
public final class NightMotionSampler implements AutoCloseable {
    public static final long MAX_DURATION_MS = 30_000L;

    private final SensorManager sensorManager;
    private final Handler handler;
    private final Sensor sensor;
    private final NightMotionEvidence.Accumulator accumulator;
    private boolean running;

    private final SensorEventListener listener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            if (event == null || event.values == null || event.values.length < 3) return;
            accumulator.observe(event.values[0], event.values[1], event.values[2],
                    android.os.SystemClock.elapsedRealtime());
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private final Runnable timeout = new Runnable() {
        @Override public void run() { stop(); }
    };

    public NightMotionSampler(SensorManager sensorManager, Handler handler) {
        this.sensorManager = sensorManager;
        this.handler = handler;
        Sensor linear = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        Sensor fallback = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensor = linear != null ? linear : fallback;
        boolean linearAcceleration = linear != null;
        accumulator = new NightMotionEvidence.Accumulator(
                linear != null ? "LINEAR_ACCELERATION" : fallback != null ? "ACCELEROMETER" : "NONE",
                linearAcceleration);
    }

    /** Registers the sensor for this capture only and arms the hard upper bound. */
    public synchronized boolean start() {
        if (running || sensorManager == null || sensor == null) return false;
        accumulator.reset();
        try {
            if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)) {
                return false;
            }
            running = true;
            if (handler != null) {
                handler.removeCallbacks(timeout);
                handler.postDelayed(timeout, MAX_DURATION_MS);
            }
            return true;
        } catch (RuntimeException ignored) {
            running = false;
            return false;
        }
    }

    public NightMotionEvidence snapshot() {
        return accumulator.snapshot(android.os.SystemClock.elapsedRealtime());
    }

    public synchronized boolean isRunning() { return running; }

    /** Idempotently unregisters the listener and disarms the timeout. */
    public synchronized void stop() {
        if (handler != null) handler.removeCallbacks(timeout);
        if (!running) return;
        try { sensorManager.unregisterListener(listener); }
        catch (RuntimeException ignored) { /* Sensor teardown must not fail the capture. */ }
        running = false;
    }

    @Override public void close() { stop(); }
}
