package ru.darkcat.camera.field;

import java.util.Objects;

/**
 * Process-local trigger endpoint owned by {@link FieldModeService}.
 *
 * <p>This is intentionally a strong, explicit endpoint rather than a WeakReference to an
 * Activity. The service owns the locked-screen camera session; the visible Activity is only a
 * UI/preview client and is never required for a background trigger.</p>
 */
public final class FieldCaptureBridge {
    public interface Endpoint {
        /** Returns true when the request was handled or deliberately rejected with feedback. */
        boolean requestCapture();

        boolean isCameraReady();

        void stopCaptureSession();
    }

    private static volatile Endpoint endpoint;

    public static void attach(Endpoint value) {
        endpoint = Objects.requireNonNull(value, "value");
    }

    public static void detach(Endpoint value) {
        if (endpoint == value) endpoint = null;
    }

    public static boolean requestCapture() {
        Endpoint value = endpoint;
        return value != null && value.requestCapture();
    }

    public static boolean isCameraBridgeReady() {
        Endpoint value = endpoint;
        return value != null && value.isCameraReady();
    }

    public static void stopCaptureSession() {
        Endpoint value = endpoint;
        if (value != null) value.stopCaptureSession();
    }

    private FieldCaptureBridge() { }
}
