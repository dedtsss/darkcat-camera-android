package ru.darkcat.camera.haptic;

/** Hardware-independent capture feedback port. */
public interface CaptureHaptics {
    /** One short pulse, called from the actual successful camera capture callback. */
    void signalCaptureSuccess();

    /** Clearly longer feedback for a rejection before capture or a camera error. */
    void signalCaptureFailure();
}
