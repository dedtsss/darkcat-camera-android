package ru.darkcat.camera.capture;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureDecisionEngineTest {
    @Test public void maxSpeedNeverWaits() {
        assertEquals(0, CaptureDecisionEngine.decide(CaptureMode.MAX_SPEED, 10.0, false).delayMillis);
    }

    @Test public void sharpPriorityWaitIsStrictlyBounded() {
        long delay = CaptureDecisionEngine.decide(CaptureMode.SHARP_PRIORITY, 10.0, false).delayMillis;
        assertTrue(delay >= 100);
        assertTrue(delay <= CaptureDecisionEngine.MAX_ADDITIONAL_DELAY_MS);
    }

    @Test public void existingGoodCandidateAvoidsWait() {
        assertEquals(0, CaptureDecisionEngine.decide(CaptureMode.SHARP_PRIORITY, 2.0, true).delayMillis);
    }

    @Test public void motionScoreIsMonotonic() {
        assertTrue(MotionScorer.stability(0.1) > MotionScorer.stability(1.0));
        assertEquals(5.0, MotionScorer.angularSpeed(3, 4, 0), 0.0001);
    }
}
