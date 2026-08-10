package ru.darkcat.camera.haptic;

import java.util.Objects;

/**
 * Encodes the product rule: camera success is success immediately; failures in
 * stamp/encryption/database/upload must never masquerade as a missed capture.
 */
public final class CaptureHapticController {
    private final CaptureHaptics haptics;

    public CaptureHapticController(CaptureHaptics haptics) {
        this.haptics = Objects.requireNonNull(haptics, "haptics");
    }

    public void onCameraCaptureSucceeded() {
        try {
            haptics.signalCaptureSuccess();
        } catch (RuntimeException ignored) {
            // Feedback is best effort and cannot invalidate an actual capture.
        }
    }

    public void onPreCaptureRejected() {
        signalFailureSafely();
    }

    public void onCameraCaptureFailed() {
        signalFailureSafely();
    }

    /** Intentionally a no-op: a successful camera capture remains successful. */
    public void onPostCapturePipelineFailed() {
        // Storage/sync UI owns this error; do not emit the "frame not captured" signal.
    }

    private void signalFailureSafely() {
        try {
            haptics.signalCaptureFailure();
        } catch (RuntimeException ignored) {
            // A broken vibrator cannot break camera control flow.
        }
    }
}
