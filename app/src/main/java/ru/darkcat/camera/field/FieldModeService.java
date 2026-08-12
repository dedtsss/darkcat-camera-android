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
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.linkedcamera.app.MainActivity;
import com.linkedcamera.app.R;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.gallery.MediaStoreCaptureStore;
import ru.darkcat.camera.haptic.AndroidCaptureHaptics;
import ru.darkcat.camera.haptic.CaptureHapticController;
import ru.darkcat.camera.location.CaptureDecision;
import ru.darkcat.camera.location.GpsIssue;
import ru.darkcat.camera.location.GpsState;
import ru.darkcat.camera.location.GpsLockerService;
import ru.darkcat.camera.location.LocationFix;
import ru.darkcat.camera.tags.TagRepository;
import ru.darkcat.camera.upload.UploadScheduler;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-started camera FGS. It never unlocks or draws over the system lockscreen. */
public final class FieldModeService extends Service implements FieldCaptureBridge.Endpoint,
        FieldCaptureController.Observer {
    public static final String ACTION_STOP = "ru.darkcat.camera.field.STOP";
    public static final String ACTION_STOP_ALL = "ru.darkcat.camera.field.STOP_ALL";
    public static final String ACTION_SYNC = "ru.darkcat.camera.field.SYNC";
    public static final String ACTION_ACTIVITY_VISIBLE = "ru.darkcat.camera.field.ACTIVITY_VISIBLE";
    public static final String ACTION_ACTIVITY_BACKGROUND = "ru.darkcat.camera.field.ACTIVITY_BACKGROUND";
    private static final String CHANNEL = "darkcat_field_mode";
    private static final int NOTIFICATION_ID = 7301;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService artifactExecutor = Executors.newSingleThreadExecutor();
    private static volatile FieldModeService activeService;
    private MediaSession mediaSession;
    private PowerManager.WakeLock wakeLock;
    private FieldCameraSessionOwner cameraOwner;
    private FieldCaptureController captureController;
    // Service startup commonly comes from DarkCat Settings while MainActivity is paused. Treat
    // that state as background until MainActivity explicitly hands ownership back in onResume.
    private volatile boolean activityVisible;
    private long lastExternalTriggerElapsedMs;

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

    public static void stopEverything(Context context) {
        DarkCatSettings.set(context, "darkcat_field_mode", false);
        DarkCatSettings.set(context, "darkcat_gps_locker", false);
        context.stopService(new Intent(context, FieldModeService.class).setAction(ACTION_STOP_ALL));
        context.stopService(new Intent(context, GpsLockerService.class));
    }

    public static void handoffToVisibleActivity(Context context) {
        FieldModeService service = activeService;
        if (service != null) service.activityBecameVisible();
        else context.startService(new Intent(context, FieldModeService.class).setAction(ACTION_ACTIVITY_VISIBLE));
    }

    public static void handoffToBackground(Context context) {
        FieldModeService service = activeService;
        if (service != null) service.activityBecameBackground();
        else context.startService(new Intent(context, FieldModeService.class).setAction(ACTION_ACTIVITY_BACKGROUND));
    }

    @Override public void onCreate() {
        super.onCreate();
        activeService = this;
        cameraOwner = new FieldCameraSessionOwner(this);
        captureController = new FieldCaptureController(
                () -> GpsLockerService.captureDecision(this),
                cameraOwner,
                new CaptureHapticController(new AndroidCaptureHaptics(this)),
                this);
        FieldCaptureBridge.attach(this);
        createChannel();
        recoverDurableFieldJpegs();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_ACTIVITY_VISIBLE.equals(action)) {
            activityBecameVisible();
            return START_NOT_STICKY;
        }
        if (ACTION_ACTIVITY_BACKGROUND.equals(action)) {
            activityBecameBackground();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_ALL.equals(action)) {
            DarkCatSettings.set(this, "darkcat_field_mode", false);
            GpsLockerService.stop(this);
            stopSelf();
            return START_NOT_STICKY;
        }
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
        FieldCaptureBridge.detach(this);
        if (cameraOwner != null) cameraOwner.shutdown();
        releaseMediaSession();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        artifactExecutor.shutdownNow();
        if (activeService == this) activeService = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void triggerFromVolume() {
        if (!DarkCatSettings.volumeShutterEnabled(this)) return;
        FieldTriggerDiagnostics.record("volume-provider", android.view.KeyEvent.KEYCODE_VOLUME_UP);
        if (!requestCapture()) new AndroidCaptureHaptics(this).signalCaptureFailure();
    }

    private void updateRuntimeState() {
        FieldModeState.updateDiagnostics(
                cameraOwner != null && cameraOwner.isReady()
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
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                android.os.Parcelable parcelable = mediaButtonIntent == null ? null
                        : mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (parcelable instanceof android.view.KeyEvent) {
                    android.view.KeyEvent event = (android.view.KeyEvent) parcelable;
                    Log.i("DarkCatFieldInput", "media-button key=" + event.getKeyCode()
                            + " action=" + event.getAction() + " repeat=" + event.getRepeatCount());
                    FieldTriggerDiagnostics.record("media-button", event.getKeyCode());
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                            && event.getRepeatCount() == 0
                            && isShutterKey(event.getKeyCode())) {
                        triggerFromVolume();
                        return true;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }
        });
        mediaSession.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
            @Override public void onAdjustVolume(int direction) {
                if (direction == AudioManager.ADJUST_RAISE) handler.post(FieldModeService.this::triggerFromVolume);
            }
        });
        mediaSession.setActive(true);
        if (!activityVisible && cameraOwner != null) cameraOwner.start();
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
        boolean ownerReady = cameraOwner != null && cameraOwner.isReady();
        String state = FieldNotificationStatus.cameraState(
                DarkCatSettings.storageBlocked(this), ownerReady, activityVisible);
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
                .addAction(R.drawable.ic_photo_camera_white_48dp, "Остановить всё", stopAllIntent())
                .addAction(R.drawable.ic_photo_camera_white_48dp, "Остановить камеру", stop)
                .build();
    }

    private PendingIntent stopAllIntent() {
        return PendingIntent.getService(this, 3,
                new Intent(this, FieldModeService.class).setAction(ACTION_STOP_ALL), immutableUpdate());
    }

    private static boolean isShutterKey(int keyCode) {
        return keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == android.view.KeyEvent.KEYCODE_CAMERA
                || keyCode == android.view.KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
    }

    private void activityBecameVisible() {
        activityVisible = true;
        if (cameraOwner != null) cameraOwner.stop();
        updateRuntimeState();
    }

    private void activityBecameBackground() {
        activityVisible = false;
        if (cameraOwner != null) cameraOwner.start();
        updateRuntimeState();
    }

    @Override public boolean requestCapture() {
        if (!DarkCatSettings.volumeShutterEnabled(this) || captureController == null) return false;
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastExternalTriggerElapsedMs != 0L && now - lastExternalTriggerElapsedMs < 300L) return true;
        lastExternalTriggerElapsedMs = now;
        CaptureStartResult result = captureController.requestCapture();
        return result.getStatus() == CaptureStartStatus.STARTED
                || result.getStatus() == CaptureStartStatus.BLOCKED_BY_GPS;
    }

    @Override public boolean isCameraReady() {
        return cameraOwner != null && cameraOwner.isReady();
    }

    @Override public void stopCaptureSession() {
        if (cameraOwner != null) cameraOwner.stop();
    }

    @Override public void onCaptureBlocked(CaptureDecision decision) {
        Log.i("DarkCatFieldInput", "capture blocked: " + decision.getBlockReason());
    }

    @Override public void onCameraCaptureSucceeded(LocationFix shutterLocation) {
        // Success haptic is emitted by FieldCaptureController before this callback.
    }

    @Override public void onCameraCaptureFailed() {
        Log.i("DarkCatFieldInput", "service-owned camera capture failed");
    }

    @Override public void onCameraCaptureArtifact(File durableJpeg, LocationFix shutterLocation,
                                                   long capturedAt) {
        if (durableJpeg == null || !durableJpeg.isFile()) return;
        artifactExecutor.execute(() -> {
            try {
                byte[] jpeg = readBytes(durableJpeg);
                ru.darkcat.camera.data.PhotoCaptureTicket ticket =
                        ru.darkcat.camera.vault.DarkCatCaptureCoordinator.enqueuePhotoCaptureSuccess(
                                this, capturedAt, shutterLocation);
                if (DarkCatSettings.isVaultMode(this)) {
                    ru.darkcat.camera.vault.DarkCatCaptureCoordinator.stageCapturedJpeg(
                            this, jpeg, ticket, new Date(capturedAt));
                } else {
                    CaptureContext base = CaptureContext.empty();
                    java.util.ArrayList<String> tags = new java.util.ArrayList<>(base.customTags);
                    for (String tag : new TagRepository(this).active()) if (!tags.contains(tag)) tags.add(tag);
                    CaptureContext context = base.withTagsAndLocation(tags, shutterLocation);
                    new MediaStoreCaptureStore().saveJpeg(this, jpeg,
                            "DarkCat-Field-" + capturedAt + ".jpg", ticket.sequenceNumber,
                            ticket.capturedAt, context);
                }
                DarkCatSettings.set(this, "darkcat_storage_blocked", false);
                if (!durableJpeg.delete()) Log.w("DarkCatFieldCamera", "unable to delete staged JPEG");
            } catch (Exception failure) {
                DarkCatSettings.set(this, "darkcat_storage_blocked", true);
                Log.e("DarkCatFieldCamera", "field JPEG remains for recovery", failure);
            }
        });
    }

    /**
     * A process death after the service-owned Camera2 write but before handoff must not discard
     * the JPEG. Files are only removed after the normal recovery pipeline accepts them.
     */
    private void recoverDurableFieldJpegs() {
        File root = new File(getFilesDir(), "darkcat-field-capture");
        File[] pending = root.listFiles((directory, name) -> name != null && name.endsWith(".jpg"));
        if (pending == null || pending.length == 0) return;
        artifactExecutor.execute(() -> {
            for (File file : pending) {
                try {
                    if (DarkCatSettings.isVaultMode(this)) {
                        ru.darkcat.camera.vault.DarkCatCaptureCoordinator.interceptFile(this, file, false);
                    } else {
                        ru.darkcat.camera.data.PhotoCaptureTicket ticket =
                                ru.darkcat.camera.vault.DarkCatCaptureCoordinator.enqueuePhotoCaptureSuccess(this);
                        new MediaStoreCaptureStore().saveJpeg(this, readBytes(file), file.getName(),
                                ticket.sequenceNumber, ticket.capturedAt, CaptureContext.empty());
                        if (!file.delete()) Log.w("DarkCatFieldCamera", "unable to delete recovered JPEG");
                    }
                } catch (Exception failure) {
                    Log.e("DarkCatFieldCamera", "durable field JPEG recovery deferred", failure);
                }
            }
        });
    }

    private static byte[] readBytes(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), 4_000_000L))) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
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
