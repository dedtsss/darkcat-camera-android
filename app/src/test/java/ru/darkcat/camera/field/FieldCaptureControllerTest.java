package ru.darkcat.camera.field;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import ru.darkcat.camera.haptic.CaptureHapticController;
import ru.darkcat.camera.haptic.CaptureHaptics;
import ru.darkcat.camera.location.CaptureBlockReason;
import ru.darkcat.camera.location.CaptureDecision;
import ru.darkcat.camera.location.GpsCaptureGate;
import ru.darkcat.camera.location.GpsPolicy;
import ru.darkcat.camera.location.GpsSnapshot;
import ru.darkcat.camera.location.GpsStateEvaluator;
import ru.darkcat.camera.location.LocationFix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class FieldCaptureControllerTest {
    private static final long NOW = 20_000_000_000L;

    @Test
    public void gpsRejectionDoesNotInvokeCameraAndSignalsFailure() {
        FakeCamera camera = new FakeCamera();
        RecordingObserver observer = new RecordingObserver();
        RecordingHaptics haptics = new RecordingHaptics(observer.events);
        FieldCaptureController controller = new FieldCaptureController(
                () -> blockedDecision(),
                camera,
                new CaptureHapticController(haptics),
                observer);

        CaptureStartResult result = controller.requestCapture();

        assertEquals(CaptureStartStatus.BLOCKED_BY_GPS, result.getStatus());
        assertEquals(0, camera.requestCount);
        assertEquals(1, haptics.failureCount);
        assertEquals("failure-haptic", observer.events.get(0));
        assertEquals("blocked", observer.events.get(1));
    }

    @Test
    public void cameraSuccessHapticPrecedesPostCaptureObserver() {
        FakeCamera camera = new FakeCamera();
        RecordingObserver observer = new RecordingObserver();
        RecordingHaptics haptics = new RecordingHaptics(observer.events);
        FieldCaptureController controller = controller(
                allowedDecision(), camera, haptics, observer);

        CaptureStartResult result = controller.requestCapture();
        assertEquals(CaptureStartStatus.STARTED, result.getStatus());
        camera.callback.onCameraCaptureSucceeded();

        assertEquals("success-haptic", observer.events.get(0));
        assertEquals("capture-success", observer.events.get(1));
        assertEquals(1, haptics.successCount);
        assertEquals(0, haptics.failureCount);
    }

    @Test
    public void cameraRejectAndAsyncErrorUseFailureHaptic() {
        RecordingObserver rejectedObserver = new RecordingObserver();
        RecordingHaptics rejectedHaptics = new RecordingHaptics(rejectedObserver.events);
        FakeCamera rejectedCamera = new FakeCamera();
        rejectedCamera.accept = false;
        CaptureStartResult rejected = controller(
                allowedDecision(), rejectedCamera, rejectedHaptics, rejectedObserver)
                .requestCapture();
        assertEquals(CaptureStartStatus.REJECTED_BY_CAMERA, rejected.getStatus());
        assertEquals(1, rejectedHaptics.failureCount);

        RecordingObserver failedObserver = new RecordingObserver();
        RecordingHaptics failedHaptics = new RecordingHaptics(failedObserver.events);
        FakeCamera failedCamera = new FakeCamera();
        controller(allowedDecision(), failedCamera, failedHaptics, failedObserver)
                .requestCapture();
        failedCamera.callback.onCameraCaptureFailed();
        failedCamera.callback.onCameraCaptureFailed();
        assertEquals(1, failedHaptics.failureCount);
        assertEquals(1, failedObserver.failureCount);
    }

    private static FieldCaptureController controller(
            CaptureDecision decision,
            FakeCamera camera,
            RecordingHaptics haptics,
            RecordingObserver observer) {
        GpsCaptureGate gate = () -> decision;
        return new FieldCaptureController(
                gate, camera, new CaptureHapticController(haptics), observer);
    }

    private static CaptureDecision allowedDecision() {
        LocationFix fix = new LocationFix(1.0d, 2.0d, 3.0f, NOW, 1L, "gps");
        return CaptureDecision.allowed(new GpsStateEvaluator().evaluate(
                GpsSnapshot.running(fix), NOW, GpsPolicy.strictDefault()));
    }

    private static CaptureDecision blockedDecision() {
        return CaptureDecision.blocked(
                CaptureBlockReason.NO_FIX,
                new GpsStateEvaluator().evaluate(
                        GpsSnapshot.running(null), NOW, GpsPolicy.strictDefault()));
    }

    private static final class FakeCamera implements CameraCapturePort {
        int requestCount;
        boolean accept = true;
        Callback callback;

        @Override
        public boolean requestCapture(Callback callback) {
            requestCount++;
            this.callback = callback;
            return accept;
        }
    }

    private static final class RecordingHaptics implements CaptureHaptics {
        private final List<String> events;
        int successCount;
        int failureCount;

        RecordingHaptics(List<String> events) {
            this.events = events;
        }

        @Override
        public void signalCaptureSuccess() {
            successCount++;
            events.add("success-haptic");
        }

        @Override
        public void signalCaptureFailure() {
            failureCount++;
            events.add("failure-haptic");
        }
    }

    private static final class RecordingObserver implements FieldCaptureController.Observer {
        final List<String> events = new ArrayList<>();
        int failureCount;
        LocationFix lastFix;

        @Override
        public void onCaptureBlocked(CaptureDecision decision) {
            events.add("blocked");
        }

        @Override
        public void onCameraCaptureSucceeded(LocationFix shutterLocation) {
            lastFix = shutterLocation;
            events.add("capture-success");
        }

        @Override
        public void onCameraCaptureFailed() {
            failureCount++;
            events.add("camera-failed");
        }
    }
}
