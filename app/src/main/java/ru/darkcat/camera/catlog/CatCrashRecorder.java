package ru.darkcat.camera.catlog;

import android.app.ApplicationExitInfo;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.util.List;
import org.json.JSONObject;
import java.util.Map;

/** Best-effort own-process crash and previous-exit evidence; never swallows a crash. */
final class CatCrashRecorder {
    private final Context context;
    private Thread previous;

    CatCrashRecorder(Context context) { this.context = context.getApplicationContext(); }

    void install() {
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            CatLog.event("crash", "process.uncaught_exception", null, null, null,
                    CatPrivacy.error(error), null);
            CatLog.flush(500L);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    JSONObject collectPreviousExit() {
        JSONObject result = new JSONObject();
        try { result.put("available", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R); }
        catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return result;
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return result;
        try {
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 3);
            if (exits == null || exits.isEmpty()) return result;
            ApplicationExitInfo exit = exits.get(0);
            Map<String, Object> attributes = new java.util.HashMap<>();
            attributes.put("exit_reason", reason(exit.getReason()));
            attributes.put("exit_description", exit.getDescription());
            CatLog.event("process", "process.previous_exit", null, null, null, null, attributes);
            result.put("reason", reason(exit.getReason()));
            result.put("description", CatPrivacy.text(exit.getDescription()));
            result.put("timestamp_ms", exit.getTimestamp());
        } catch (RuntimeException ignored) { }
        catch (Exception ignored) { }
        return result;
    }

    private static String reason(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_CRASH: return "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            default: return String.valueOf(reason);
        }
    }
}
