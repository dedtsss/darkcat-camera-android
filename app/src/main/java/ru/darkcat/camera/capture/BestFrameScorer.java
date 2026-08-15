package ru.darkcat.camera.capture;

/** Deterministic technical-quality ranking; it intentionally uses no ML model. */
public final class BestFrameScorer {
    // Camera2 state values are kept as integers so this class remains a plain JVM-testable component.
    private static final int AF_PASSIVE_FOCUSED = 2;
    private static final int AF_FOCUSED_LOCKED = 4;
    private static final int AF_PASSIVE_UNFOCUSED = 6;
    private static final int AE_CONVERGED = 2;
    private static final int AE_LOCKED = 3;
    private static final int AE_FLASH_REQUIRED = 4;
    private static final int AWB_CONVERGED = 2;
    private static final int AWB_LOCKED = 3;

    public static double score(FrameCandidate candidate, long shutterTimestampNanos) {
        if (candidate == null) return Double.NEGATIVE_INFINITY;
        double sharpness = Math.log1p(candidate.sharpness) / 8.0;
        double stability = MotionScorer.stability(candidate.angularSpeedRadPerSecond);
        double temporalDistanceMs = Math.abs(candidate.timestampNanos - shutterTimestampNanos) / 1_000_000.0;
        double temporal = Math.max(0.0, 1.0 - temporalDistanceMs / 350.0);
        double af = state(candidate.afState, AF_PASSIVE_FOCUSED, AF_FOCUSED_LOCKED, AF_PASSIVE_UNFOCUSED);
        double ae = state(candidate.aeState, AE_CONVERGED, AE_LOCKED, AE_FLASH_REQUIRED);
        double awb = state(candidate.awbState, AWB_CONVERGED, AWB_LOCKED, Integer.MIN_VALUE);
        return 0.46 * sharpness + 0.27 * stability + 0.14 * temporal + 0.08 * af + 0.03 * ae + 0.02 * awb;
    }

    public static FrameCandidate choose(Iterable<FrameCandidate> candidates, long shutterTimestampNanos) {
        FrameCandidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        if (candidates == null) return null;
        for (FrameCandidate candidate : candidates) {
            double score = score(candidate, shutterTimestampNanos);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static double state(Integer value, int goodA, int goodB, int knownBad) {
        if (value == null) return 0.45;
        if (value == goodA || value == goodB) return 1.0;
        if (value == knownBad) return 0.0;
        return 0.35;
    }

    private BestFrameScorer() { }
}
