package ru.darkcat.camera.location;

/** Stable machine-readable reason behind a GPS indicator. */
public enum GpsIssue {
    NONE,
    STOPPED,
    SEARCHING,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    PROVIDER_UNAVAILABLE,
    SOURCE_ERROR,
    ACCURACY_UNAVAILABLE,
    POOR_ACCURACY,
    AGING_FIX,
    STALE_FIX,
    MONOTONIC_CLOCK_MISMATCH
}
