package ru.darkcat.camera.field;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;

import ru.darkcat.camera.haptic.CaptureHapticController;
import ru.darkcat.camera.location.CaptureDecision;
import ru.darkcat.camera.location.GpsCaptureGate;
import ru.darkcat.camera.location.LocationFix;

/**
 * Shared shutter path for on-screen and Volume+ triggers. GPS is checked before
 * invoking the camera; success haptic runs first inside the real camera callback.
 */
public final class FieldCaptureController {
    public interface Observer {
        void onCaptureBlocked(CaptureDecision decision);

        /** Start durable recovery/post-processing here; success haptic has already fired. */
        void onCameraCaptureSucceeded(LocationFix shutterLocation);

        /**
         * Called synchronously for a durable app-private JPEG before success haptic. The observer
         * must durably journal shutter metadata here; publication itself may remain asynchronous.
         */
        default void onCameraCaptureArtifact(File durableJpeg, LocationFix shutterLocation,
                                             long capturedAt) { }

        void onCameraCaptureFailed();
    }

    private final GpsCaptureGate gpsGate;
    private final CameraCapturePort camera;
    private final CaptureHapticController haptics;
    private final Observer observer;

    public FieldCaptureController(
            GpsCaptureGate gpsGate,
            CameraCapturePort camera,
            CaptureHapticController haptics,
            Observer observer) {
        this.gpsGate = Objects.requireNonNull(gpsGate, "gpsGate");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.haptics = Objects.requireNonNull(haptics, "haptics");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    public CaptureStartResult requestCapture() {
        CaptureDecision decision = gpsGate.getCaptureDecision();
        if (!decision.isAllowed()) {
            haptics.onPreCaptureRejected();
            observer.onCaptureBlocked(decision);
            return new CaptureStartResult(CaptureStartStatus.BLOCKED_BY_GPS, decision);
        }

        LocationFix shutterLocation = decision.getGpsState().getFix();
        AtomicBoolean completed = new AtomicBoolean(false);
        CameraCapturePort.Callback callback = new CameraCapturePort.Callback() {
            @Override
            public void onCameraCaptureSucceeded() {
                if (completed.compareAndSet(false, true)) {
                    haptics.onCameraCaptureSucceeded();
                    observer.onCameraCaptureSucceeded(shutterLocation);
                }
            }

            @Override
            public void onCameraCaptureSucceeded(File durableJpeg, long capturedAt) {
                if (completed.compareAndSet(false, true)) {
                    // A process-kill after the user receives success feedback must still find the
                    // exact shutter-time sidecar, not reconstruct metadata from a later fix.
                    observer.onCameraCaptureArtifact(durableJpeg, shutterLocation, capturedAt);
                    haptics.onCameraCaptureSucceeded();
                    observer.onCameraCaptureSucceeded(shutterLocation);
                }
            }

            @Override
            public void onCameraCaptureFailed() {
                if (completed.compareAndSet(false, true)) {
                    haptics.onCameraCaptureFailed();
                    observer.onCameraCaptureFailed();
                }
            }
        };

        final boolean accepted;
        try {
            accepted = camera.requestCapture(callback);
        } catch (RuntimeException cameraFailure) {
            rejectCameraRequest(completed);
            return new CaptureStartResult(CaptureStartStatus.REJECTED_BY_CAMERA, decision);
        }
        if (!accepted) {
            rejectCameraRequest(completed);
            return new CaptureStartResult(CaptureStartStatus.REJECTED_BY_CAMERA, decision);
        }
        return new CaptureStartResult(CaptureStartStatus.STARTED, decision);
    }

    private void rejectCameraRequest(AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            haptics.onCameraCaptureFailed();
            observer.onCameraCaptureFailed();
        }
    }
}
