package ru.darkcat.camera.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import ru.darkcat.camera.crypto.SecureCredentialStore;

public final class DarkCatSettings {
    public static final String MODE_FAST = "fast";
    public static final String MODE_EDIT = "edit";
    public static final String CAPTURE_MAX_SPEED = "max_speed";
    public static final String CAPTURE_SHARP = "sharp_priority";
    public static final String CROSSHAIR_OFF = "off";
    public static final String CROSSHAIR_PREVIEW = "preview";
    public static final String CROSSHAIR_STAMP = "stamp";
    public static final String PROVIDER_LOCAL = "local";
    public static final String PROVIDER_OFF = "off";
    public static final String PROVIDER_NEXTCLOUD = "nextcloud";
    public static final String PROVIDER_WEBDAV = "webdav";
    public static final String PROVIDER_DARKCAT_API = "darkcat_api";

    private static SharedPreferences prefs(Context context) { return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()); }
    public static boolean isSecureMode(Context context) { return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && prefs(context).getBoolean("darkcat_secure_mode", true); }
    public static String workflow(Context context) { return prefs(context).getString("darkcat_workflow", MODE_FAST); }
    public static String captureMode(Context context) { return prefs(context).getString("darkcat_capture_mode", CAPTURE_MAX_SPEED); }
    public static boolean gpsLockerEnabled(Context context) { return prefs(context).getBoolean("darkcat_gps_locker", false); }
    public static boolean strictGps(Context context) { return prefs(context).getBoolean("darkcat_strict_gps", true); }
    public static float maxGpsAccuracyMeters(Context context) { return floatValue(context, "darkcat_gps_max_accuracy", 7.0f); }
    public static long locationFreshMs(Context context) { return longValue(context, "darkcat_location_fresh_ms", 5_000L); }
    /** Upper edge of the aging interval; retained as a settings compatibility alias. */
    public static long locationAgingMs(Context context) { return longValue(context, "darkcat_location_aging_ms", 15_000L); }
    public static long locationStaleMs(Context context) { return longValue(context, "darkcat_location_stale_ms", 15_000L); }
    public static boolean fieldModeEnabled(Context context) { return prefs(context).getBoolean("darkcat_field_mode", false); }
    public static boolean volumeShutterEnabled(Context context) { return prefs(context).getBoolean("darkcat_volume_shutter", true); }
    public static boolean sequenceEnabled(Context context) { return prefs(context).getBoolean("darkcat_sequence_enabled", true); }
    public static int currentPhotoSequence(Context context) { return SequenceAllocator.peekNextPhoto(context); }
    public static void setCurrentPhotoSequence(Context context, int next) { SequenceAllocator.setNextPhoto(context, next); }
    public static void resetPhotoSequence(Context context) { SequenceAllocator.resetPhoto(context); }
    public static boolean stampCoordinates(Context context) { return prefs(context).getBoolean("darkcat_stamp_coordinates", true); }
    public static boolean stampAccuracy(Context context) { return prefs(context).getBoolean("darkcat_stamp_accuracy", true); }
    public static boolean stampSequence(Context context) { return prefs(context).getBoolean("darkcat_stamp_sequence", true); }
    public static boolean stampTags(Context context) { return prefs(context).getBoolean("darkcat_stamp_tags", true); }
    public static boolean stampCustomText(Context context) { return prefs(context).getBoolean("darkcat_stamp_custom_text_enabled", false); }
    public static String customStampText(Context context) { return prefs(context).getString("darkcat_stamp_custom_text", ""); }
    public static String crosshair(Context context) { return prefs(context).getString("darkcat_crosshair", CROSSHAIR_OFF); }
    public static int crosshairSize(Context context) { return prefs(context).getInt("darkcat_crosshair_size", 36); }
    public static int crosshairThickness(Context context) { return prefs(context).getInt("darkcat_crosshair_thickness", 2); }
    public static int crosshairColor(Context context) { return prefs(context).getInt("darkcat_crosshair_color", 0xffffffff); }
    public static boolean autoUpload(Context context) { return prefs(context).getBoolean("darkcat_auto_upload", false); }
    public static boolean wifiOnly(Context context) { return prefs(context).getBoolean("darkcat_wifi_only", false); }
    public static boolean deleteAfterVerified(Context context) { return prefs(context).getBoolean("darkcat_delete_after_verified", false); }
    /** KEEP LOCAL is deliberately the default retention policy. */
    public static boolean keepLocal(Context context) { return !deleteAfterVerified(context); }
    public static String provider(Context context) { return prefs(context).getString("darkcat_provider", PROVIDER_OFF); }
    public static String remoteFolder(Context context) { return prefs(context).getString("darkcat_remote_folder", "DarkCat Camera"); }
    public static String baseUrl(Context context) { return prefs(context).getString("darkcat_webdav_base", ""); }
    public static String nextcloudShare(Context context) { return SecureCredentialStore.get(context, "nextcloud_share"); }
    public static long lastSuccessfulSync(Context context) { return longValue(context, "darkcat_last_sync_success", 0L); }
    public static boolean storageBlocked(Context context) { return prefs(context).getBoolean("darkcat_storage_blocked", false); }

    public static void set(Context context, String key, Object value) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else editor.putString(key, String.valueOf(value));
        editor.apply();
    }

    private static float floatValue(Context context, String key, float fallback) {
        Object value = prefs(context).getAll().get(key);
        if (value instanceof Number) return ((Number) value).floatValue();
        if (value instanceof String) try { return Float.parseFloat((String) value); } catch (NumberFormatException ignored) { }
        return fallback;
    }

    private static long longValue(Context context, String key, long fallback) {
        Object value = prefs(context).getAll().get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) try { return Long.parseLong((String) value); } catch (NumberFormatException ignored) { }
        return fallback;
    }
    private DarkCatSettings() { }
}
