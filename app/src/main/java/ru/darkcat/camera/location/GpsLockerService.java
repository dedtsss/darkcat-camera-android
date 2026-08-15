package ru.darkcat.camera.location;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.content.ContextCompat;

import com.linkedcamera.app.MainActivity;
import com.linkedcamera.app.R;

import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.field.FieldModeService;

/** Visible, Google-free, continuous GNSS foreground service. */
public final class GpsLockerService extends Service implements GpsLockerController.Listener {
    public static final String ACTION_START_USER = "ru.darkcat.camera.location.START_USER";
    public static final String ACTION_START_FIELD = "ru.darkcat.camera.location.START_FIELD";
    /** Legacy stop action is the explicit-user stop action. */
    public static final String ACTION_STOP = "ru.darkcat.camera.location.STOP";
    public static final String ACTION_STOP_USER = "ru.darkcat.camera.location.STOP_USER";
    public static final String ACTION_STOP_FIELD = "ru.darkcat.camera.location.STOP_FIELD";
    public static final String ACTION_STOP_ALL = "ru.darkcat.camera.location.STOP_ALL";
    private static final String CHANNEL = "darkcat_gps_locker";
    private static final int NOTIFICATION_ID = 7302;
    private static volatile GpsLockerController activeController;

    private GpsLockerController controller;

    /** Starts the independently requested, persistent GPS Locker. */
    public static void startForUserFromVisibleContext(Context context) {
        startFromVisibleContext(context, ACTION_START_USER, true);
    }

    /** Starts GPS solely because the user enabled Field Mode. */
    public static void startForFieldFromVisibleContext(Context context) {
        startFromVisibleContext(context, ACTION_START_FIELD, false);
    }

    /** Compatibility alias retained for older product call sites. */
    public static void startFromVisibleContext(Context context) {
        startForUserFromVisibleContext(context);
    }

    private static void startFromVisibleContext(Context context, String action, boolean userOwned) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("FINE_LOCATION_PERMISSION_REQUIRED");
        }
        if (userOwned) DarkCatSettings.setGpsLockerUserRequested(context, true);
        else DarkCatSettings.setGpsLockerFieldRequested(context, true);
        try {
            ContextCompat.startForegroundService(context, new Intent(context, GpsLockerService.class)
                    .setAction(action));
        } catch (RuntimeException deniedByPlatform) {
            if (userOwned) DarkCatSettings.setGpsLockerUserRequested(context, false);
            else DarkCatSettings.setGpsLockerFieldRequested(context, false);
            throw deniedByPlatform;
        }
    }

    /** Removes only the user's explicit request; Field Mode may still own the service. */
    public static void stopUser(Context context) {
        DarkCatSettings.setGpsLockerUserRequested(context, false);
        stopIfUnrequested(context);
    }

    /** Removes only Field Mode's temporary request. */
    public static void stopField(Context context) {
        DarkCatSettings.setGpsLockerFieldRequested(context, false);
        stopIfUnrequested(context);
    }

    public static void stopEverything(Context context) {
        DarkCatSettings.set(context, "darkcat_field_mode", false);
        DarkCatSettings.clearGpsLockerRequests(context);
        context.stopService(new Intent(context, FieldModeService.class));
        context.stopService(new Intent(context, GpsLockerService.class));
    }

    /** Compatibility alias: product UI now means stop the explicit persistent locker. */
    public static void stop(Context context) {
        stopUser(context);
    }

    private static void stopIfUnrequested(Context context) {
        if (!DarkCatSettings.gpsLockerEnabled(context)) {
            context.stopService(new Intent(context, GpsLockerService.class));
        }
    }

    public static GpsState currentState(Context context) {
        return LocationRepository.currentState(context);
    }

    public static CaptureDecision captureDecision(Context context) {
        return LocationRepository.captureDecision(context);
    }

    public static GpsPolicy policy(Context context) {
        boolean strict = DarkCatSettings.strictGps(context);
        try {
            return new GpsPolicy(
                    strict,
                    DarkCatSettings.maxGpsAccuracyMeters(context),
                    DarkCatSettings.locationFreshMs(context),
                    DarkCatSettings.locationStaleMs(context));
        } catch (IllegalArgumentException invalidPreference) {
            return GpsPolicy.strictDefault().withStrictCapture(strict);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        controller = new GpsLockerController(new LocationManagerGpsLocker(this), new AndroidElapsedRealtimeClock(), policy(this));
        controller.addListener(this);
        activeController = controller;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP_ALL.equals(action)) {
            stopEverything(this);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action) || ACTION_STOP_USER.equals(action)) {
            DarkCatSettings.setGpsLockerUserRequested(this, false);
            if (!DarkCatSettings.gpsLockerEnabled(this)) stopSelf();
            else onGpsStateChanged(controller.getState());
            return DarkCatSettings.gpsLockerEnabled(this) ? START_STICKY : START_NOT_STICKY;
        }
        if (ACTION_STOP_FIELD.equals(action)) {
            DarkCatSettings.setGpsLockerFieldRequested(this, false);
            if (!DarkCatSettings.gpsLockerEnabled(this)) stopSelf();
            else onGpsStateChanged(controller.getState());
            return DarkCatSettings.gpsLockerEnabled(this) ? START_STICKY : START_NOT_STICKY;
        }
        if (!DarkCatSettings.gpsLockerEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification notification = notification(controller.getState());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException promotionDenied) {
            failRequiredLockerStart(action);
            return START_NOT_STICKY;
        }
        try {
            controller.setPolicy(policy(this));
            controller.start();
        } catch (RuntimeException locationUnavailable) {
            failRequiredLockerStart(action);
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    /** A failed Field-owned location service cannot leave camera Field Mode half-alive. */
    private void failRequiredLockerStart(String action) {
        boolean fieldWasRequested = DarkCatSettings.gpsLockerFieldRequested(this);
        if (ACTION_START_USER.equals(action)) {
            DarkCatSettings.setGpsLockerUserRequested(this, false);
        } else if (ACTION_START_FIELD.equals(action)) {
            DarkCatSettings.setGpsLockerFieldRequested(this, false);
        } else {
            DarkCatSettings.clearGpsLockerRequests(this);
        }
        if (fieldWasRequested) {
            DarkCatSettings.set(this, "darkcat_field_mode", false);
            DarkCatSettings.setGpsLockerFieldRequested(this, false);
            stopService(new Intent(this, FieldModeService.class));
        }
        LocationRepository.publishLockerSnapshot(GpsSnapshot.stopped(), false);
        stopSelf();
    }

    @Override public void onGpsStateChanged(GpsState state) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) try {
            manager.notify(NOTIFICATION_ID, notification(state));
        } catch (RuntimeException notificationDenied) {
            // The platform still owns FGS visibility even if app notification display is restricted.
        }
    }

    @Override public void onDestroy() {
        if (controller != null) {
            controller.removeListener(this);
            controller.close();
        }
        if (activeController == controller) activeController = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification notification(GpsState state) {
        CaptureDecision decision = controller == null
                ? captureDecision(this)
                : controller.getCaptureDecision();
        String status;
        switch (state.getIndicator()) {
            case GREEN: status = "GPS " + state.getAccuracyLabel() + " · готов"; break;
            case YELLOW:
                status = "GPS " + state.getAccuracyLabel()
                        + (decision.isAllowed()
                        ? state.getIssue() == GpsIssue.AGING_FIX
                                ? " · fix стареет"
                                : " · точность вне допуска"
                        : " · точность вне допуска");
                break;
            default:
                status = decision.isAllowed()
                        ? "GPS недоступен"
                        : "GPS недоступен · точная съёмка заблокирована";
                break;
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), immutableUpdate());
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, GpsLockerService.class).setAction(ACTION_STOP_USER), immutableUpdate());
        boolean userOwned = DarkCatSettings.gpsLockerUserRequested(this);
        boolean fieldOwned = DarkCatSettings.gpsLockerFieldRequested(this);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification publicVersion = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this))
                .setSmallIcon(R.drawable.ic_gps_fixed_white_48dp)
                .setContentTitle("DarkCat Camera")
                .setContentText("GPS Locker активен")
                .build();
        Notification.Builder configured = builder.setSmallIcon(R.drawable.ic_gps_fixed_white_48dp)
                .setContentTitle(fieldOwned && !userOwned
                        ? "DarkCat Camera · GPS Locker для Field Mode"
                        : "DarkCat Camera · GPS Locker")
                .setContentText(status + (fieldOwned ? " · нужен Field Mode" : ""))
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .addAction(R.drawable.ic_photo_camera_white_48dp, "Открыть камеру", open);
        if (userOwned) {
            configured.addAction(R.drawable.ic_gps_fixed_white_48dp, "Остановить GPS", stop);
        } else if (fieldOwned) {
            configured.addAction(R.drawable.ic_gps_fixed_white_48dp, "GPS нужен Field Mode", open);
        }
        return configured.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "DarkCat GPS Locker", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Постоянная точная геолокация для полевой съёмки");
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static int immutableUpdate() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}
