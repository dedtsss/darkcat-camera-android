package ru.darkcat.camera.catlog;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class PreviewHealthTrackerTest {
    @Test public void reportsCadenceAndLargestGap() {
        PreviewHealthTracker tracker = new PreviewHealthTracker();
        tracker.start(0L);
        assertEquals(null, tracker.onFrame(100L));
        assertEquals(null, tracker.onFrame(200L));
        PreviewHealthTracker.Snapshot snapshot = tracker.onFrame(5_200L);
        assertNotNull(snapshot);
        assertFalse(snapshot.stalled);
        assertEquals(5_000L, snapshot.maxFrameGapMs);
        assertTrue(snapshot.effectiveFps > 0.0d);
    }

    @Test public void watchdogEmitsStallAndRecovery() {
        PreviewHealthTracker tracker = new PreviewHealthTracker();
        tracker.start(0L);
        tracker.onFrame(100L);
        PreviewHealthTracker.Snapshot stalled = tracker.onWatchdog(1_700L);
        assertNotNull(stalled);
        assertTrue(stalled.stalled);
        PreviewHealthTracker.Snapshot recovered = tracker.onFrame(1_800L);
        assertNotNull(recovered);
        assertFalse(recovered.stalled);
    }
}
