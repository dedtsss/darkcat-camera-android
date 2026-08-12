package ru.darkcat.camera.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.linkedcamera.app.PreferenceKeys;

/**
 * Owns the boundary between DarkCat product controls and inherited Linked Camera preferences.
 * It is deliberately idempotent: rotation/recreate cannot re-enable a legacy overlay.
 */
public final class DarkCatPreferencePolicy {
    public static final String DARKCAT_MEDIASTORE_FOLDER = "DarkCat";

    public static void normalize(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext());
        SharedPreferences.Editor editor = preferences.edit();

        // 0.4 stored only a boolean. Migrate it once without changing a user's chosen destination.
        if (!preferences.contains("darkcat_storage_mode")) {
            editor.putString("darkcat_storage_mode", preferences.getBoolean("darkcat_secure_mode", true)
                    ? StorageMode.VAULT.preferenceValue() : StorageMode.MEDIASTORE.preferenceValue());
        }
        editor.putBoolean("darkcat_secure_mode",
                DarkCatSettings.storageMode(preferences) == StorageMode.VAULT);

        // 0.5 initially used one flag for both an explicit locker and the Field-owned locker.
        // Preserve an existing explicit request while all new Field starts get their own owner.
        if (!preferences.contains("darkcat_gps_locker_user") && preferences.contains("darkcat_gps_locker")) {
            editor.putBoolean("darkcat_gps_locker_user",
                    preferences.getBoolean("darkcat_gps_locker", false));
        }
        if (!preferences.contains("darkcat_gps_locker_field")) {
            editor.putBoolean("darkcat_gps_locker_field", false);
        }

        // Auto-editor was a 0.4 capture mode. Editing is now an explicit viewer action.
        editor.remove("darkcat_workflow");

        // DarkCat has one readable status/stamp layer. Upstream overlays must never race it.
        editor.putBoolean(PreferenceKeys.ShowTimePreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowFreeMemoryPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowISOPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowCameraIDPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowZoomPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowBatteryPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowStoreLocationPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowStampPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowTextStampPreferenceKey, false);
        editor.putString(PreferenceKeys.StampPreferenceKey, "preference_stamp_no");
        editor.putString(PreferenceKeys.TextStampPreferenceKey, "");
        editor.putBoolean(PreferenceKeys.ShowZoomSliderControlsPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.UsingSAFPreferenceKey, false);
        editor.putString(PreferenceKeys.SaveLocationPreferenceKey, DARKCAT_MEDIASTORE_FOLDER);
        editor.putString(PreferenceKeys.QualityPreferenceKey, "100");
        editor.apply();
    }

    /** Pure list used by regression tests/documentation of the one-owner rule. */
    public static String[] ownedUpstreamPreferenceKeys() {
        return new String[]{
                PreferenceKeys.ShowTimePreferenceKey,
                PreferenceKeys.ShowFreeMemoryPreferenceKey,
                PreferenceKeys.ShowISOPreferenceKey,
                PreferenceKeys.ShowCameraIDPreferenceKey,
                PreferenceKeys.ShowZoomPreferenceKey,
                PreferenceKeys.ShowBatteryPreferenceKey,
                PreferenceKeys.ShowStoreLocationPreferenceKey,
                PreferenceKeys.ShowStampPreferenceKey,
                PreferenceKeys.ShowTextStampPreferenceKey,
                PreferenceKeys.StampPreferenceKey,
                PreferenceKeys.TextStampPreferenceKey,
                PreferenceKeys.ShowZoomSliderControlsPreferenceKey
        };
    }

    private DarkCatPreferencePolicy() { }
}
