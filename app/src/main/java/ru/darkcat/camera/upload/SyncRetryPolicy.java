package ru.darkcat.camera.upload;

/** Pure bounded backoff used only for user-visible diagnostics; WorkManager remains the scheduler. */
public final class SyncRetryPolicy {
    public static long diagnosticDelayMillis(int attempt) {
        int exponent = Math.min(10, Math.max(0, attempt - 1));
        return Math.min(6L * 60L * 60L * 1000L, 30_000L * (1L << exponent));
    }

    private SyncRetryPolicy() { }
}
