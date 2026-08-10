package ru.darkcat.camera.capture;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Callback-based gyro sampler used only to decide whether a sub-200 ms wait could help. */
public final class MotionSampler implements SensorEventListener {
    private final SensorManager manager;
    private final Sensor gyroscope;
    private volatile double angularSpeed;
    private volatile long sampleElapsedNanos;

    public MotionSampler(Context context) {
        manager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        gyroscope = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public void start() {
        if (manager != null && gyroscope != null) manager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() { if (manager != null) manager.unregisterListener(this); }
    public boolean isSupported() { return gyroscope != null; }
    public double angularSpeedRadPerSecond() { return angularSpeed; }
    public long sampleElapsedNanos() { return sampleElapsedNanos; }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values.length < 3) return;
        double sample = MotionScorer.angularSpeed(event.values[0], event.values[1], event.values[2]);
        angularSpeed = angularSpeed == 0.0 ? sample : 0.68 * angularSpeed + 0.32 * sample;
        sampleElapsedNanos = event.timestamp;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
