package ru.darkcat.camera.catlog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Owns the local session identity and clean-stop/interruption marker. */
final class CatSessionManager {
    private static final String PREFS = "darkcat_cat_log_session";
    private static final String ACTIVE_ID = "active_id";
    private static final String ACTIVE = "active";
    private final Context context;
    private final SharedPreferences preferences;
    private final String sessionId;
    private final File sessionDirectory;
    private final boolean recovered;

    CatSessionManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String oldId = preferences.getString(ACTIVE_ID, null);
        boolean wasActive = preferences.getBoolean(ACTIVE, false);
        recovered = wasActive && oldId != null;
        sessionId = recovered ? oldId : newSessionId();
        sessionDirectory = new File(new File(this.context.getFilesDir(), "cat-log/sessions"), sessionId);
        if (!sessionDirectory.exists()) sessionDirectory.mkdirs();
        preferences.edit().putString(ACTIVE_ID, sessionId).putBoolean(ACTIVE, true).apply();
    }

    String id() { return sessionId; }
    File directory() { return sessionDirectory; }
    boolean recovered() { return recovered; }
    boolean isActive() { return preferences.getBoolean(ACTIVE, true); }

    void markStopped() { preferences.edit().putBoolean(ACTIVE, false).apply(); }
    void markStarted() { preferences.edit().putBoolean(ACTIVE, true).apply(); }
    void clear() { deleteRecursively(sessionDirectory); }

    String appInfo() {
        String version = "unknown";
        int build = 0;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = info.versionName;
            build = info.versionCode;
        } catch (Exception ignored) { }
        return new org.json.JSONObject().toString();
    }

    org.json.JSONObject appInfoJson() throws org.json.JSONException {
        String version = "unknown";
        long build = 0;
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = info.versionName;
            build = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) { }
        return new org.json.JSONObject().put("package", context.getPackageName())
                .put("version", version).put("build", build).put("schema_version", CatEvent.SCHEMA_VERSION);
    }

    org.json.JSONObject deviceInfoJson() throws org.json.JSONException {
        return new org.json.JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("device", Build.DEVICE).put("android_release", Build.VERSION.RELEASE)
                .put("sdk_int", Build.VERSION.SDK_INT).put("security_patch", Build.VERSION.SECURITY_PATCH);
    }

    private static String newSessionId() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
