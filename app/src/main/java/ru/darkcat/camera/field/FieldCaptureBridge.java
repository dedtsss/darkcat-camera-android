package ru.darkcat.camera.field;

import java.lang.ref.WeakReference;

/** Process-local adapter from the foreground service to the already-open Linked Camera Activity. */
public final class FieldCaptureBridge {
    public interface Target {
        /**
         * Runs the same GPS-gated shutter path as the visible UI. Return true
         * when the trigger was handled, including a GPS rejection that already
         * emitted failure haptic; false means no feedback was emitted.
         */
        boolean requestFieldCapture();

        /** Current camera readiness; a live Activity reference alone is not sufficient. */
        boolean isFieldCaptureReady();

        /** Close a camera that was deliberately kept warm after an explicit/service stop. */
        void stopFieldCaptureSession();
    }

    private static volatile WeakReference<Target> target = new WeakReference<>(null);

    public static void attach(Target value) { target = new WeakReference<>(value); }
    public static void detach(Target value) {
        Target current = target.get();
        if (current == value) target.clear();
    }

    public static boolean requestCapture() {
        Target current = target.get();
        return current != null && current.requestFieldCapture();
    }

    public static boolean isCameraBridgeReady() {
        Target current = target.get();
        return current != null && current.isFieldCaptureReady();
    }

    public static void stopCaptureSession() {
        Target current = target.get();
        if (current != null) current.stopFieldCaptureSession();
    }
    private FieldCaptureBridge() { }
}
