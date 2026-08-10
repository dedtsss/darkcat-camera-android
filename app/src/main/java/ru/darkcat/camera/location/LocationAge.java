package ru.darkcat.camera.location;

/** Age bucket calculated only from Android's monotonic elapsed-realtime clock. */
public enum LocationAge {
    FRESH,
    AGING,
    STALE
}
