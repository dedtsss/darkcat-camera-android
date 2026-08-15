package ru.darkcat.camera.upload;

import android.content.Context;
import android.content.SharedPreferences;

/** Small, non-secret operational trace for the Sync screen and diagnostics export. */
public final class SyncDiagnostics {
    private static final String PREFS = "darkcat_sync_diagnostics";
    public static void recordEnqueued(Context context) { edit(context).putLong("next_retry", System.currentTimeMillis() + 30_000L).apply(); }
    public static void recordStart(Context context) { edit(context).putLong("last_start", System.currentTimeMillis()).putString("last_error", "").apply(); }
    public static void recordSuccess(Context context) { edit(context).putLong("last_success", System.currentTimeMillis()).putLong("next_retry", 0L).putString("last_error", "").apply(); }
    public static void recordFailure(Context context, int attempt, String error) {
        long delay = SyncRetryPolicy.diagnosticDelayMillis(attempt);
        edit(context).putString("last_error", error == null ? "Unknown upload error" : error)
                .putLong("next_retry", System.currentTimeMillis() + delay).apply();
    }
    public static Snapshot snapshot(Context context) {
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Snapshot(p.getLong("last_start", 0L), p.getLong("last_success", 0L), p.getLong("next_retry", 0L), p.getString("last_error", ""));
    }
    private static SharedPreferences.Editor edit(Context context) { return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(); }
    public static final class Snapshot { public final long lastStart, lastSuccess, nextRetry; public final String lastError; Snapshot(long lastStart, long lastSuccess, long nextRetry, String lastError) { this.lastStart = lastStart; this.lastSuccess = lastSuccess; this.nextRetry = nextRetry; this.lastError = lastError; } }
    private SyncDiagnostics() { }
}
