package ru.darkcat.camera.location;

/** Injectable monotonic clock used for fix-age decisions. */
public interface ElapsedRealtimeClock {
    long nowNanos();
}
