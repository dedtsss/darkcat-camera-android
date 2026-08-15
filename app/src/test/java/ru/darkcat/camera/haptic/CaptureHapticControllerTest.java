package ru.darkcat.camera.haptic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CaptureHapticControllerTest {
    @Test
    public void onlyCaptureAndPreCaptureOutcomesProduceFrameFeedback() {
        FakeHaptics fake = new FakeHaptics();
        CaptureHapticController controller = new CaptureHapticController(fake);

        controller.onCameraCaptureSucceeded();
        controller.onPreCaptureRejected();
        controller.onCameraCaptureFailed();
        controller.onPostCapturePipelineFailed();

        assertEquals(1, fake.successCount);
        assertEquals(2, fake.failureCount);
    }

    private static final class FakeHaptics implements CaptureHaptics {
        int successCount;
        int failureCount;

        @Override
        public void signalCaptureSuccess() {
            successCount++;
        }

        @Override
        public void signalCaptureFailure() {
            failureCount++;
        }
    }
}
