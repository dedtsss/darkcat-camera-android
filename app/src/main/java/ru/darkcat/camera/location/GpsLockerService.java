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

/** Visible, Google-free, continuous GNSS foreground service. */
public final class GpsLockerService extends Service implements GpsLockerController.Listener {
    public static final String ACTION_STOP = "ru.darkcat.camera.location.STOP";
    private static final String CHANNEL = "darkcat_gps_locker";
    private static final int NOTIFICATION_ID = 7302;
    private static volatile GpsLockerController activeController;

    private GpsLockerController controller;

    public static void startFromVisibleContext(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("FINE_LOCATION_PERMISSION_REQUIRED");
        }
        DarkCatSettings.set(context, "darkcat_gps_locker", true);
        try {
            ContextCompat.startForegroundService(context, new Intent(context, GpsLockerService.class));
        } catch (RuntimeException deniedByPlatform) {
            DarkCatSettings.set(context, "darkcat_gps_locker", false);
            throw deniedByPlatform;
        }
    }

    public static void stop(Context context) {
        DarkCatSettings.set(context, "darkcat_gps_locker", false);
        context.stopService(new Intent(context, GpsLockerService.class));
    }

    public static GpsState currentState(Context context) {
        GpsLockerController value = activeController;
        if (value != null) return value.getState();
        GpsPolicy policy = policy(context);
        return new GpsStateEvaluator().evaluate(GpsSnapshot.stopped(), android.os.SystemClock.elapsedRealtimeNanos(), policy);
    }

    public static CaptureDecision captureDecision(Context context) {
        GpsLockerController value = activeController;
        if (value != null) return value.getCaptureDecision();
        GpsPolicy policy = policy(context);
        return new GpsCapturePolicy().evaluate(GpsSnapshot.stopped(), android.os.SystemClock.elapsedRealtimeNanos(), policy);
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
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            DarkCatSettings.set(this, "darkcat_gps_locker", false);
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
            DarkCatSettings.set(this, "darkcat_gps_locker", false);
            LocationSnapshotStore.setLockerRunning(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            controller.setPolicy(policy(this));
            controller.start();
        } catch (RuntimeException locationUnavailable) {
            DarkCatSettings.set(this, "darkcat_gps_locker", false);
            LocationSnapshotStore.setLockerRunning(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
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
                                : " · уточнение"
                        : " · съёмка заблокирована");
                break;
            default:
                status = decision.isAllowed()
                        ? "GPS недоступен"
                        : "GPS недоступен · точная съёмка заблокирована";
                break;
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), immutableUpdate());
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, GpsLockerService.class).setAction(ACTION_STOP), immutableUpdate());
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification publicVersion = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this))
                .setSmallIcon(R.drawable.ic_gps_fixed_white_48dp)
                .setContentTitle("DarkCat Camera")
                .setContentText("GPS Locker активен")
                .build();
        return builder.setSmallIcon(R.drawable.ic_gps_fixed_white_48dp)
                .setContentTitle("DarkCat Camera · GPS Locker")
                .setContentText(status)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .addAction(R.drawable.ic_photo_camera_white_48dp, "Открыть камеру", open)
                .addAction(R.drawable.ic_gps_fixed_white_48dp, "Остановить GPS", stop)
                .build();
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
