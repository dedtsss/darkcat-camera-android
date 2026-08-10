package ru.darkcat.camera.location;

/** Runtime state of the platform location source, independent of fix quality. */
public enum GpsSourceStatus {
    STOPPED,
    RUNNING,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    PROVIDER_UNAVAILABLE,
    ERROR
}
