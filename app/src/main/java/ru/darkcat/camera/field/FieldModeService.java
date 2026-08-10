package ru.darkcat.camera.field;

import android.annotation.SuppressLint;
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
import android.media.AudioManager;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

import com.linkedcamera.app.MainActivity;
import com.linkedcamera.app.R;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.haptic.AndroidCaptureHaptics;
import ru.darkcat.camera.location.CaptureDecision;
import ru.darkcat.camera.location.GpsIssue;
import ru.darkcat.camera.location.GpsState;
import ru.darkcat.camera.location.GpsLockerService;
import ru.darkcat.camera.upload.UploadScheduler;

/** User-started camera FGS. It never unlocks or draws over the system lockscreen. */
public final class FieldModeService extends Service {
    public static final String ACTION_STOP = "ru.darkcat.camera.field.STOP";
    public static final String ACTION_SYNC = "ru.darkcat.camera.field.SYNC";
    private static final String CHANNEL = "darkcat_field_mode";
    private static final int NOTIFICATION_ID = 7301;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSession mediaSession;
    private PowerManager.WakeLock wakeLock;

    private final Runnable notificationTicker = new Runnable() {
        @Override public void run() {
            updateRuntimeState();
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                try {
                    manager.notify(NOTIFICATION_ID, notification(true));
                } catch (RuntimeException notificationFailure) {
                    // The running FGS/camera path must survive notification permission changes.
                }
            }
            handler.postDelayed(this, 2_000L);
        }
    };

    public static void startFromVisibleActivity(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("CAMERA_PERMISSION_REQUIRED");
        }
        DarkCatSettings.set(context, "darkcat_field_mode", true);
        FieldModeState.updateDiagnostics(FieldModeState.STARTING, false);
        try {
            ContextCompat.startForegroundService(context, new Intent(context, FieldModeService.class));
        } catch (RuntimeException deniedByPlatform) {
            DarkCatSettings.set(context, "darkcat_field_mode", false);
            FieldModeState.updateDiagnostics(FieldModeState.ERROR, false);
            throw deniedByPlatform;
        }
        if (DarkCatSettings.gpsLockerEnabled(context)) GpsLockerService.startFromVisibleContext(context);
    }

    public static void stop(Context context) {
        DarkCatSettings.set(context, "darkcat_field_mode", false);
        context.stopService(new Intent(context, FieldModeService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            DarkCatSettings.set(this, "darkcat_field_mode", false);
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification initial = notification(false);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            } else {
                startForeground(NOTIFICATION_ID, initial);
            }
        } catch (RuntimeException promotionDenied) {
            DarkCatSettings.set(this, "darkcat_field_mode", false);
            FieldModeState.updateDiagnostics(FieldModeState.ERROR, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            ensureRuntimeStarted();
        } catch (RuntimeException runtimeUnavailable) {
            DarkCatSettings.set(this, "darkcat_field_mode", false);
            FieldModeState.updateDiagnostics(FieldModeState.ERROR, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_SYNC.equals(action)) {
            try { UploadScheduler.enqueueAllPending(this); }
            catch (RuntimeException ignored) { /* Sync can never terminate camera readiness. */ }
        }
        updateRuntimeState();
        handler.removeCallbacks(notificationTicker);
        handler.postDelayed(notificationTicker, 2_000L);
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(notificationTicker);
        FieldModeState.updateDiagnostics(FieldModeState.DISABLED, false);
        FieldCaptureBridge.stopCaptureSession();
        releaseMediaSession();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void triggerFromVolume() {
        if (!DarkCatSettings.volumeShutterEnabled(this)) return;
        if (!FieldCaptureBridge.requestCapture()) {
            new AndroidCaptureHaptics(this).signalCaptureFailure();
        }
    }

    private void updateRuntimeState() {
        FieldModeState.updateDiagnostics(
                FieldCaptureBridge.isCameraBridgeReady()
                        ? FieldModeState.ACTIVE
                        : FieldModeState.DEGRADED,
                DarkCatSettings.volumeShutterEnabled(this));
    }

    private void ensureRuntimeStarted() {
        acquireWakeLock();
        if (!DarkCatSettings.volumeShutterEnabled(this)) {
            releaseMediaSession();
            return;
        }
        if (mediaSession != null) return;
        mediaSession = new MediaSession(this, "DarkCatFieldMode");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        PlaybackState state = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 0L, 0f)
                .build();
        mediaSession.setPlaybackState(state);
        mediaSession.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
            @Override public void onAdjustVolume(int direction) {
                if (direction == AudioManager.ADJUST_RAISE) handler.post(FieldModeService.this::triggerFromVolume);
            }
        });
        mediaSession.setActive(true);
    }

    private void releaseMediaSession() {
        if (mediaSession == null) return;
        mediaSession.setActive(false);
        mediaSession.release();
        mediaSession = null;
    }

    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        if (manager == null) return;
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DarkCatCamera:FieldMode");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private Notification notification(boolean includeQueue) {
        GpsState gpsState = GpsLockerService.currentState(this);
        CaptureDecision gpsDecision = GpsLockerService.captureDecision(this);
        String gps;
        if (gpsState.hasAccuracy()) {
            gps = "GPS " + gpsState.getAccuracyLabel();
        } else if (gpsState.getIssue() == GpsIssue.STOPPED) {
            gps = "GPS выключен";
        } else {
            gps = "GPS: поиск";
        }
        if (!gpsDecision.isAllowed()) gps += " · съёмка заблокирована";
        int queue = includeQueue ? DarkCatDatabase.get(this).queueCount() : 0;
        String state = DarkCatSettings.storageBlocked(this)
                ? "Ошибка хранилища · съёмка заблокирована"
                : FieldCaptureBridge.isCameraBridgeReady()
                        ? "Камера готова" : "Откройте камеру для восстановления";
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), immutableUpdate());
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, FieldModeService.class).setAction(ACTION_STOP), immutableUpdate());
        PendingIntent sync = PendingIntent.getService(this, 2,
                new Intent(this, FieldModeService.class).setAction(ACTION_SYNC), immutableUpdate());
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification publicVersion = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this))
                .setSmallIcon(R.drawable.ic_photo_camera_white_48dp)
                .setContentTitle("DarkCat Camera · Полевой режим")
                .setContentText("Активен")
                .build();
        return builder.setSmallIcon(R.drawable.ic_photo_camera_white_48dp)
                .setContentTitle("DarkCat Camera · Полевой режим")
                .setContentText(state + " · " + gps
                        + (includeQueue ? " · очередь " + queue : ""))
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .addAction(R.drawable.ic_photo_camera_white_48dp, "Открыть камеру", open)
                .addAction(R.drawable.ic_photo_camera_white_48dp, "Отправить очередь", sync)
                .addAction(R.drawable.ic_photo_camera_white_48dp, "Остановить", stop)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "DarkCat Полевой режим", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Камера и аппаратная кнопка готовы после блокировки экрана");
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static int immutableUpdate() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}
