package ru.darkcat.camera.catlog;

/** Small, deterministic preview health model used by the Camera2 callback thread. */
public final class PreviewHealthTracker {
    public static final long STALL_AFTER_MS = 1_500L;
    public static final long REPORT_EVERY_MS = 5_000L;

    public static final class Snapshot {
        public final long frames;
        public final long maxFrameGapMs;
        public final long frameAgeMs;
        public final double effectiveFps;
        public final boolean stalled;

        private Snapshot(long frames, long maxFrameGapMs, long frameAgeMs,
                         double effectiveFps, boolean stalled) {
            this.frames = frames;
            this.maxFrameGapMs = maxFrameGapMs;
            this.frameAgeMs = frameAgeMs;
            this.effectiveFps = effectiveFps;
            this.stalled = stalled;
        }
    }

    private boolean started;
    private long firstFrameMs;
    private long lastFrameMs;
    private long lastReportMs;
    private long frames;
    private long maxFrameGapMs;
    private boolean stalled;

    public synchronized void start(long nowMs) {
        started = true;
        firstFrameMs = 0L;
        lastFrameMs = 0L;
        lastReportMs = nowMs;
        frames = 0L;
        maxFrameGapMs = 0L;
        stalled = false;
    }

    public synchronized Snapshot onFrame(long nowMs) {
        if (!started) return null;
        if (firstFrameMs == 0L) firstFrameMs = nowMs;
        if (lastFrameMs != 0L) {
            long gap = Math.max(0L, nowMs - lastFrameMs);
            maxFrameGapMs = Math.max(maxFrameGapMs, gap);
        }
        lastFrameMs = nowMs;
        frames++;
        boolean wasStalled = stalled;
        stalled = false;
        if (wasStalled || nowMs - lastReportMs >= REPORT_EVERY_MS) return snapshot(nowMs);
        return null;
    }

    public synchronized Snapshot onWatchdog(long nowMs) {
        if (!started || lastFrameMs == 0L) return null;
        boolean shouldStall = nowMs - lastFrameMs >= STALL_AFTER_MS;
        boolean stateChanged = shouldStall != stalled;
        stalled = shouldStall;
        if (stateChanged || nowMs - lastReportMs >= REPORT_EVERY_MS) return snapshot(nowMs);
        return null;
    }

    public synchronized void stop() { started = false; stalled = false; }

    private Snapshot snapshot(long nowMs) {
        lastReportMs = nowMs;
        long durationMs = firstFrameMs == 0L ? 0L : Math.max(1L, nowMs - firstFrameMs);
        double fps = durationMs == 0L ? 0.0d : (frames * 1000.0d) / durationMs;
        long age = lastFrameMs == 0L ? 0L : Math.max(0L, nowMs - lastFrameMs);
        return new Snapshot(frames, maxFrameGapMs, age, fps, stalled);
    }
}
