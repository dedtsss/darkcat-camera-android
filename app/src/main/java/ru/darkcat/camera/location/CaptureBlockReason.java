package ru.darkcat.camera.location;

/** Pre-capture GPS rejection reason. */
public enum CaptureBlockReason {
    NONE,
    GPS_STOPPED,
    GPS_PERMISSION_DENIED,
    LOCATION_DISABLED,
    PROVIDER_UNAVAILABLE,
    GPS_SOURCE_ERROR,
    NO_FIX,
    STALE_FIX,
    ACCURACY_UNAVAILABLE,
    ACCURACY_TOO_LOW
}
