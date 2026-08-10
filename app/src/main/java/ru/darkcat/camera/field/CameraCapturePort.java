package ru.darkcat.camera.field;

/** Adapter implemented by the Linked/Open Camera engine. */
public interface CameraCapturePort {
    interface Callback {
        /** The actual camera capture callback, not a later file/encryption callback. */
        void onCameraCaptureSucceeded();

        void onCameraCaptureFailed();
    }

    /** Returns false if the engine rejected the request before starting capture. */
    boolean requestCapture(Callback callback);
}
