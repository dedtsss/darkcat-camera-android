package ru.darkcat.camera.location;

import android.os.SystemClock;

/** Platform monotonic clock. Wall-clock changes cannot make a stale fix fresh. */
public final class AndroidElapsedRealtimeClock implements ElapsedRealtimeClock {
    @Override
    public long nowNanos() {
        return SystemClock.elapsedRealtimeNanos();
    }
}
