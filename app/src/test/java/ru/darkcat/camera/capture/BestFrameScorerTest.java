package ru.darkcat.camera.capture;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class BestFrameScorerTest {
    @Test public void stableSharpFocusedCandidateWins() {
        long shutter = 1_000_000_000L;
        FrameCandidate blurred = new FrameCandidate(shutter, 10, 1.4, 6, 1, 1);
        FrameCandidate sharp = new FrameCandidate(shutter - 25_000_000L, 900, 0.04, 2, 2, 2);
        assertSame(sharp, BestFrameScorer.choose(Arrays.asList(blurred, sharp), shutter));
    }

    @Test public void ringIsBoundedAndWindowed() {
        BestFrameRingBuffer ring = new BestFrameRingBuffer(2);
        ring.add(new FrameCandidate(1, 100, 0, null, null, null));
        ring.add(new FrameCandidate(2, 200, 0, null, null, null));
        ring.add(new FrameCandidate(3, 300, 0, null, null, null));
        assertEquals(2, ring.size());
        assertEquals(3, ring.best(3, 1).timestampNanos);
    }
}
