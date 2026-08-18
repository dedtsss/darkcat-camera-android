package ru.darkcat.camera.field;

/** Pure motion-to-camera-work policy for the persistent Field service. */
public final class FieldStandbyPolicy {
    public static final long STATIONARY_GRACE_MS = 3L * 60L * 1000L;

    public enum State { HOT, GRACE, STANDBY }

    private State state = State.HOT;
    private long stationarySinceMs = -1L;

    public State state() { return state; }

    public State observe(boolean moving, long nowMs) {
        if (moving) {
            state = State.HOT;
            stationarySinceMs = -1L;
            return state;
        }
        if (stationarySinceMs < 0L) stationarySinceMs = nowMs;
        if (nowMs - stationarySinceMs >= STATIONARY_GRACE_MS) state = State.STANDBY;
        else if (state != State.STANDBY) state = State.GRACE;
        return state;
    }
}
