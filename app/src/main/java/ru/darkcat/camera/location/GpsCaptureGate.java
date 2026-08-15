package ru.darkcat.camera.location;

/** Narrow integration port for any shutter source (UI, volume key, or notification action). */
public interface GpsCaptureGate {
    CaptureDecision getCaptureDecision();
}
