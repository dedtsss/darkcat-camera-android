package ru.darkcat.camera.capture;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Samples a tiny TextureView bitmap for advisory Best Frame scoring. Only candidate metadata is
 * retained; full-resolution JPEG ownership remains in the Linked/Open Camera Camera2 engine/ZSL.
 */
public final class BestFrameMonitor implements AutoCloseable {
    public static final long CANDIDATE_WINDOW_NANOS = 450_000_000L;
    private static final int WIDTH = 160;
    private static final int HEIGHT = 120;
    private static final long INTERVAL_MS = 120L;

    private final View previewView;
    private final MotionSampler motionSampler;
    private final BestFrameRingBuffer candidates = new BestFrameRingBuffer(12);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService analysis = Executors.newSingleThreadExecutor();
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private volatile boolean running;

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            if (!running) return;
            sample();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    public BestFrameMonitor(View previewView, MotionSampler motionSampler) {
        this.previewView = previewView;
        this.motionSampler = motionSampler;
    }

    public void start() {
        if (!(previewView instanceof TextureView) || running) return;
        running = true;
        handler.post(sampler);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(sampler);
    }

    public FrameCandidate best(long shutterElapsedRealtimeNanos) {
        return candidates.best(shutterElapsedRealtimeNanos, CANDIDATE_WINDOW_NANOS);
    }

    public int candidateCount() { return candidates.size(); }

    private void sample() {
        TextureView texture = (TextureView) previewView;
        if (!texture.isAvailable() || !inFlight.compareAndSet(false, true)) return;
        Bitmap bitmap;
        try {
            bitmap = texture.getBitmap(WIDTH, HEIGHT);
        } catch (RuntimeException unavailable) {
            inFlight.set(false);
            return;
        }
        if (bitmap == null) {
            inFlight.set(false);
            return;
        }
        long timestamp = android.os.SystemClock.elapsedRealtimeNanos();
        double motion = motionSampler == null ? 0.0 : motionSampler.angularSpeedRadPerSecond();
        DarkCatCameraState.Snapshot camera = DarkCatCameraState.latest();
        try {
            analysis.execute(() -> {
                try {
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    int[] pixels = new int[width * height];
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                    byte[] y = new byte[pixels.length];
                    for (int i = 0; i < pixels.length; i++) {
                        int color = pixels[i];
                        int luma = (77 * ((color >> 16) & 0xff)
                                + 150 * ((color >> 8) & 0xff)
                                + 29 * (color & 0xff)) >> 8;
                        y[i] = (byte) luma;
                    }
                    candidates.add(new FrameCandidate(timestamp,
                            SharpnessScorer.varianceOfLaplacian(y, width, height), motion,
                            camera.afState, camera.aeState, camera.awbState));
                } finally {
                    bitmap.recycle();
                    inFlight.set(false);
                }
            });
        } catch (RejectedExecutionException closedDuringSample) {
            bitmap.recycle();
            inFlight.set(false);
        }
    }

    @Override public void close() {
        stop();
        analysis.shutdownNow();
    }
}
