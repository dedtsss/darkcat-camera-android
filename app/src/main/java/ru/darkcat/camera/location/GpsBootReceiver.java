package ru.darkcat.camera.location;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import ru.darkcat.camera.data.DarkCatSettings;

/** Restores location only; camera Field Mode is never auto-started after boot. */
public final class GpsBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        // Field Mode never auto-starts camera after boot. Only the user's explicit persistent
        // GPS Locker may be restored here.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || !DarkCatSettings.gpsLockerUserRequested(context)) return;
        boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fine && background) {
            try { ContextCompat.startForegroundService(context, new Intent(context, GpsLockerService.class)
                    .setAction(GpsLockerService.ACTION_START_USER)); }
            catch (RuntimeException deniedByPlatform) { /* user can restore from the visible app */ }
        }
    }
}
