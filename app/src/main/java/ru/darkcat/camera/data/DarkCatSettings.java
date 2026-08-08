package ru.darkcat.camera.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import ru.darkcat.camera.crypto.SecureCredentialStore;

public final class DarkCatSettings {
    public static final String MODE_FAST = "fast";
    public static final String MODE_EDIT = "edit";
    public static final String CROSSHAIR_OFF = "off";
    public static final String CROSSHAIR_PREVIEW = "preview";
    public static final String CROSSHAIR_STAMP = "stamp";
    public static final String PROVIDER_LOCAL = "local";
    public static final String PROVIDER_NEXTCLOUD = "nextcloud";
    public static final String PROVIDER_WEBDAV = "webdav";
    public static final String PROVIDER_DARKCAT_API = "darkcat_api";

    private static SharedPreferences prefs(Context context) { return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()); }
    public static boolean isSecureMode(Context context) { return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && prefs(context).getBoolean("darkcat_secure_mode", true); }
    public static String workflow(Context context) { return prefs(context).getString("darkcat_workflow", MODE_FAST); }
    public static String crosshair(Context context) { return prefs(context).getString("darkcat_crosshair", CROSSHAIR_OFF); }
    public static int crosshairSize(Context context) { return prefs(context).getInt("darkcat_crosshair_size", 36); }
    public static int crosshairThickness(Context context) { return prefs(context).getInt("darkcat_crosshair_thickness", 2); }
    public static int crosshairColor(Context context) { return prefs(context).getInt("darkcat_crosshair_color", 0xffffcc00); }
    public static boolean autoUpload(Context context) { return prefs(context).getBoolean("darkcat_auto_upload", false); }
    public static boolean wifiOnly(Context context) { return prefs(context).getBoolean("darkcat_wifi_only", false); }
    public static boolean deleteAfterVerified(Context context) { return prefs(context).getBoolean("darkcat_delete_after_verified", false); }
    public static String provider(Context context) { return prefs(context).getString("darkcat_provider", PROVIDER_LOCAL); }
    public static String remoteFolder(Context context) { return prefs(context).getString("darkcat_remote_folder", "DarkCat Camera"); }
    public static String baseUrl(Context context) { return prefs(context).getString("darkcat_webdav_base", ""); }
    public static String nextcloudShare(Context context) { return SecureCredentialStore.get(context, "nextcloud_share"); }

    public static void set(Context context, String key, Object value) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else editor.putString(key, String.valueOf(value));
        editor.apply();
    }
    private DarkCatSettings() { }
}
