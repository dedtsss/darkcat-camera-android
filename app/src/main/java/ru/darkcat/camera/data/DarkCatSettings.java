package ru.darkcat.camera.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import ru.darkcat.camera.crypto.SecureCredentialStore;
import ru.darkcat.camera.location.GpsLockerOwnership;

public final class DarkCatSettings {
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

    public static StorageMode storageMode(Context context) {
        return storageMode(prefs(context));
    }
    static StorageMode storageMode(SharedPreferences preferences) {
        if (preferences.contains("darkcat_storage_mode")) {
            return StorageMode.fromPreference(preferences.getString("darkcat_storage_mode", null));
        }
        return preferences.getBoolean("darkcat_secure_mode", true)
                ? StorageMode.VAULT : StorageMode.MEDIASTORE;
    }
    public static void setStorageMode(Context context, StorageMode mode) {
        StorageMode checked = mode == null ? StorageMode.VAULT : mode;
        prefs(context).edit()
                .putString("darkcat_storage_mode", checked.preferenceValue())
                // Compatibility for pre-0.5 code paths and recovery after an interrupted upgrade.
                .putBoolean("darkcat_secure_mode", checked == StorageMode.VAULT)
                .apply();
    }
    public static boolean isVaultMode(Context context) { return storageMode(context) == StorageMode.VAULT; }
    public static boolean isMediaStoreMode(Context context) { return storageMode(context) == StorageMode.MEDIASTORE; }

    /** Field captures always use Vault without changing the user's saved destination choice. */
    public static StorageMode effectiveStorageMode(Context context) {
        return effectiveStorageMode(fieldModeEnabled(context), storageMode(context));
    }
    /** Pure policy function kept separate so the Field/Vault contract is testable without Android. */
    public static StorageMode effectiveStorageMode(boolean fieldEnabled, StorageMode configured) {
        return fieldEnabled ? StorageMode.VAULT : (configured == null ? StorageMode.VAULT : configured);
    }
    public static boolean effectiveIsVaultMode(Context context) {
        return effectiveStorageMode(context) == StorageMode.VAULT;
    }
    public static boolean effectiveIsMediaStoreMode(Context context) {
        return effectiveStorageMode(context) == StorageMode.MEDIASTORE;
    }

    private static SharedPreferences prefs(Context context) { return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()); }
    /** Legacy alias retained for the encrypted Vault pipeline. */
    public static boolean isSecureMode(Context context) {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && isVaultMode(context);
    }
    public static String captureMode(Context context) { return prefs(context).getString("darkcat_capture_mode", CAPTURE_MAX_SPEED); }
    /** Compatibility aggregate. New code must preserve the two owners separately. */
    public static boolean gpsLockerEnabled(Context context) { return gpsLockerOwnership(context).isLockerRequired(); }
    public static boolean gpsLockerUserRequested(Context context) {
        SharedPreferences preferences = prefs(context);
        return preferences.getBoolean("darkcat_gps_locker_user",
                preferences.getBoolean("darkcat_gps_locker", false));
    }
    public static boolean gpsLockerFieldRequested(Context context) {
        return prefs(context).getBoolean("darkcat_gps_locker_field", false);
    }
    public static GpsLockerOwnership gpsLockerOwnership(Context context) {
        return new GpsLockerOwnership(gpsLockerUserRequested(context), gpsLockerFieldRequested(context));
    }
    public static void setGpsLockerUserRequested(Context context, boolean requested) {
        setGpsLockerOwnership(context, gpsLockerOwnership(context).withUserRequest(requested));
    }
    public static void setGpsLockerFieldRequested(Context context, boolean requested) {
        setGpsLockerOwnership(context, gpsLockerOwnership(context).withFieldRequest(requested));
    }
    public static void clearGpsLockerRequests(Context context) {
        setGpsLockerOwnership(context, gpsLockerOwnership(context).stopAll());
    }
    private static void setGpsLockerOwnership(Context context, GpsLockerOwnership ownership) {
        prefs(context).edit()
                .putBoolean("darkcat_gps_locker_user", ownership.isRequestedByUser())
                .putBoolean("darkcat_gps_locker_field", ownership.isRequestedByField())
                // Keep the old aggregate readable for a safe interrupted upgrade.
                .putBoolean("darkcat_gps_locker", ownership.isLockerRequired())
                .apply();
    }
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
    public static boolean watermarkEnabled(Context context) { return prefs(context).getBoolean("darkcat_watermark_enabled", false); }
    public static String watermarkUri(Context context) { return prefs(context).getString("darkcat_watermark_uri", ""); }
    public static String watermarkPosition(Context context) { return prefs(context).getString("darkcat_watermark_position", "bottom_right"); }
    public static float watermarkSize(Context context) { return floatValue(context, "darkcat_watermark_size", .22f); }
    public static float watermarkOpacity(Context context) { return floatValue(context, "darkcat_watermark_opacity", .75f); }
    public static boolean watermarkTiled(Context context) { return prefs(context).getBoolean("darkcat_watermark_tiled", false); }
    public static float watermarkTileStep(Context context) { return floatValue(context, "darkcat_watermark_tile_step", .32f); }
    public static float watermarkAngle(Context context) { return floatValue(context, "darkcat_watermark_angle", 0f); }
    public static String hapticSuccess(Context context) { return prefs(context).getString("darkcat_haptic_success", "MEDIUM"); }
    public static String hapticFailure(Context context) { return prefs(context).getString("darkcat_haptic_failure", "MEDIUM"); }
    public static boolean nightMode(Context context) { return prefs(context).getBoolean("darkcat_night_mode", false); }

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
