package ru.darkcat.camera.location;

/**
 * Location source owned by a foreground-location service (or a visible Activity
 * during setup). Starting this object does not itself satisfy Android FGS rules.
 */
public interface GpsLocker {
    interface Listener {
        void onGpsSnapshot(GpsSnapshot snapshot);
    }

    void start();

    void stop();

    boolean isStarted();

    GpsSnapshot getSnapshot();

    void addListener(Listener listener);

    void removeListener(Listener listener);
}
