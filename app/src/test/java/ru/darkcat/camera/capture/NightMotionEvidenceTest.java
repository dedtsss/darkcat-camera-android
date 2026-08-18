package ru.darkcat.camera.capture;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NightMotionEvidenceTest {
    @Test public void linearAccelerationProducesRealStatesAndAggregates() {
        NightMotionEvidence.Accumulator accumulator =
                new NightMotionEvidence.Accumulator("LINEAR_ACCELERATION", true);

        accumulator.observe(0.0f, 0.0f, 0.0f, 100L);
        accumulator.observe(2.0f, 0.0f, 0.0f, 200L);
        accumulator.observe(0.1f, 0.0f, 0.0f, 300L);
        NightMotionEvidence evidence = accumulator.snapshot(500L);
        Map<String, Object> attributes = evidence.attributes();

        assertEquals("STABLE", evidence.state());
        assertFalse(evidence.moving());
        assertEquals(3L, attributes.get("motion_sample_count"));
        assertEquals(1L, attributes.get("motion_moving_count"));
        assertEquals(2L, attributes.get("motion_stable_count"));
        assertEquals(400L, attributes.get("motion_window_ms"));
        assertEquals(2.0f, (Float) attributes.get("motion_peak_delta_mps2"), 0.001f);
        assertEquals(0.7f, (Float) attributes.get("motion_mean_delta_mps2"), 0.01f);
    }

    @Test public void rawAccelerometerRemovesGravityBeforeClassifyingMotion() {
        NightMotionEvidence.Accumulator accumulator =
                new NightMotionEvidence.Accumulator("ACCELEROMETER", false);

        accumulator.observe(0.0f, 0.0f, 9.81f, 100L);
        assertEquals("STABLE", accumulator.snapshot(100L).state());
        accumulator.observe(0.0f, 0.0f, 12.81f, 200L);

        NightMotionEvidence evidence = accumulator.snapshot(200L);
        assertEquals("MOVING", evidence.state());
        assertTrue(evidence.moving());
        assertEquals(1L, evidence.attributes().get("motion_moving_count"));
    }

    @Test public void resetStartsAFreshCaptureWindow() {
        NightMotionEvidence.Accumulator accumulator =
                new NightMotionEvidence.Accumulator("LINEAR_ACCELERATION", true);
        accumulator.observe(2.0f, 0.0f, 0.0f, 100L);
        accumulator.reset();

        NightMotionEvidence evidence = accumulator.snapshot(500L);
        assertEquals("UNKNOWN", evidence.state());
        assertEquals(0L, evidence.attributes().get("motion_sample_count"));
        assertEquals(0L, evidence.attributes().get("motion_window_ms"));
    }
}
