package ru.darkcat.camera.capture;

/** Latest Camera2 3A observation, published without blocking the capture thread. */
public final class DarkCatCameraState {
    private static volatile Snapshot latest = new Snapshot(null, null, null, 0L);

    public static void update(Integer afState, Integer aeState, Integer awbState, long elapsedRealtimeNanos) {
        latest = new Snapshot(afState, aeState, awbState, elapsedRealtimeNanos);
    }

    public static Snapshot latest() { return latest; }

    public static final class Snapshot {
        public final Integer afState;
        public final Integer aeState;
        public final Integer awbState;
        public final long elapsedRealtimeNanos;
        Snapshot(Integer afState, Integer aeState, Integer awbState, long elapsedRealtimeNanos) {
            this.afState = afState;
            this.aeState = aeState;
            this.awbState = awbState;
            this.elapsedRealtimeNanos = elapsedRealtimeNanos;
        }

        public boolean focusAcceptable() {
            return afState == null || afState == 0 || afState == 2 || afState == 4;
        }
    }

    private DarkCatCameraState() { }
}
