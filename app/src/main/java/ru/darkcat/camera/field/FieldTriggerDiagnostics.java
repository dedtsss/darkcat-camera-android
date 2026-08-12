package ru.darkcat.camera.field;

import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight trigger telemetry for hardware validation; no key payloads or media are stored. */
public final class FieldTriggerDiagnostics {
    private static final AtomicInteger total = new AtomicInteger();
    private static final AtomicLong lastElapsed = new AtomicLong();
    private static volatile String lastSource = "none";
    private static volatile int lastKeyCode = -1;

    public static void record(String source, int keyCode) {
        lastSource = source == null ? "unknown" : source;
        lastKeyCode = keyCode;
        lastElapsed.set(android.os.SystemClock.elapsedRealtime());
        total.incrementAndGet();
        Log.i("DarkCatFieldInput", "trigger source=" + lastSource + " key=" + keyCode);
    }

    public static Snapshot snapshot() {
        return new Snapshot(total.get(), lastSource, lastKeyCode, lastElapsed.get());
    }

    public static final class Snapshot {
        public final int total;
        public final String lastSource;
        public final int lastKeyCode;
        public final long lastElapsedRealtime;

        private Snapshot(int total, String lastSource, int lastKeyCode, long lastElapsedRealtime) {
            this.total = total;
            this.lastSource = lastSource;
            this.lastKeyCode = lastKeyCode;
            this.lastElapsedRealtime = lastElapsedRealtime;
        }
    }

    private FieldTriggerDiagnostics() { }
}
