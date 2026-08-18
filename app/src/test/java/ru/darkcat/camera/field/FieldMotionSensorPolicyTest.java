package ru.darkcat.camera.field;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FieldMotionSensorPolicyTest {
    @Test public void prefersStationaryAndSignificantTriggers() {
        assertEquals(FieldMotionSensorPolicy.Mode.TRIGGER_STATIONARY_AND_SIGNIFICANT,
                FieldMotionSensorPolicy.choose(true, true));
    }

    @Test public void significantMotionIsSecondChoice() {
        assertEquals(FieldMotionSensorPolicy.Mode.TRIGGER_SIGNIFICANT_ONLY,
                FieldMotionSensorPolicy.choose(false, true));
    }

    @Test public void stationaryTriggerStillWorksWithoutWakeSensor() {
        assertEquals(FieldMotionSensorPolicy.Mode.TRIGGER_STATIONARY_ONLY,
                FieldMotionSensorPolicy.choose(true, false));
    }

    @Test public void accelerometerIsCompatibilityFallback() {
        assertEquals(FieldMotionSensorPolicy.Mode.ACCELEROMETER_FALLBACK,
                FieldMotionSensorPolicy.choose(false, false));
    }
}
