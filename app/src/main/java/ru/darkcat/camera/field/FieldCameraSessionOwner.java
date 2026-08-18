package ru.darkcat.camera.field;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import ru.darkcat.camera.capture.PhotoResolutionPolicy;

/**
 * Camera2 owner for Field Mode while the Activity is backgrounded or the real lockscreen is up.
 * It deliberately has no Activity, View, SurfaceView or WeakReference dependency.
 */
public final class FieldCameraSessionOwner implements CameraCapturePort {
    private static final String TAG = "DarkCatFieldCamera";
    private static final int MAX_JPEG_PIXELS = 12_000_000;
    private static final long RETRY_DELAY_MS = 750L;

    private final Context context;
    private final CameraManager cameraManager;
    private final HandlerThread cameraThread = new HandlerThread("DarkCatFieldCamera");
    private final Handler cameraHandler;
    private final AtomicBoolean captureInFlight = new AtomicBoolean(false);

    private volatile boolean started;
    private volatile long generation;
    private volatile boolean standby;
    private volatile boolean ready;
    private volatile CameraCapturePort.Callback pendingCallback;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader jpegReader;
    private ImageReader previewReader;
    private CaptureRequest previewRequest;
    private String cameraId;

    public FieldCameraSessionOwner(Context context) {
        this.context = context.getApplicationContext();
        cameraManager = (CameraManager) this.context.getSystemService(Context.CAMERA_SERVICE);
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    public void start(long ownerGeneration) {
        started = true;
        generation = ownerGeneration;
        cameraHandler.post(() -> openIfNeeded(ownerGeneration));
    }

    /** Keep the Camera2 session available for a trigger while removing idle repeating work. */
    public void setStandby(boolean standby) {
        this.standby = standby;
        long expectedGeneration = generation;
        cameraHandler.post(() -> {
            if (!isGenerationActive(expectedGeneration) || session == null || camera == null || !ready) return;
            try {
                if (standby) {
                    session.stopRepeating();
                } else if (previewRequest != null) {
                    session.setRepeatingRequest(previewRequest, null, cameraHandler);
                }
            } catch (CameraAccessException failure) {
                Log.w(TAG, "unable to change Field repeating state", failure);
            }
        });
    }

    public void stop(long ownerGeneration) {
        started = false;
        generation = ownerGeneration;
        cameraHandler.post(() -> {
            if (generation != ownerGeneration) return;
            failPendingCapture();
            closeCamera();
        });
    }

    public void shutdown() {
        final long shutdownGeneration = generation + 1L;
        started = false;
        generation = shutdownGeneration;
        cameraHandler.post(() -> {
            failPendingCapture();
            closeCamera();
            cameraThread.quitSafely();
        });
    }

    public boolean isReady() {
        return ready && session != null && camera != null;
    }

    @Override
    public boolean requestCapture(Callback callback) {
        if (callback == null || !started || !isReady()
                || !captureInFlight.compareAndSet(false, true)) return false;
        long expectedGeneration = generation;
        pendingCallback = callback;
        cameraHandler.post(() -> captureStill(expectedGeneration, callback));
        return true;
    }

    private void openIfNeeded(long expectedGeneration) {
        if (!isGenerationActive(expectedGeneration) || ready || camera != null) return;
        if (cameraManager == null || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "camera permission/manager unavailable");
            scheduleRetry(expectedGeneration);
            return;
        }
        try {
            cameraId = chooseBackCamera();
            if (cameraId == null) {
                Log.w(TAG, "no camera id available; will retry");
                scheduleRetry(expectedGeneration);
                return;
            }
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Size jpegSize = chooseJpegSize(characteristics);
            Size previewSize = choosePreviewSize(characteristics);
            jpegReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(),
                    ImageFormat.JPEG, 2);
            previewReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            jpegReader.setOnImageAvailableListener(this::onJpegAvailable, cameraHandler);
            previewReader.setOnImageAvailableListener(reader -> closeLatest(reader), cameraHandler);
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice opened) {
                    if (!isGenerationActive(expectedGeneration)) {
                        opened.close();
                        return;
                    }
                    camera = opened;
                    configureSession(expectedGeneration, opened);
                }

                @Override public void onDisconnected(CameraDevice disconnected) {
                    if (camera != disconnected || generation != expectedGeneration) {
                        disconnected.close();
                        return;
                    }
                    camera = null;
                    disconnected.close();
                    ready = false;
                    closeSessionOnly();
                    scheduleRetry(expectedGeneration);
                }

                @Override public void onError(CameraDevice errored, int error) {
                    Log.e(TAG, "camera error=" + error);
                    if (camera != errored || generation != expectedGeneration) {
                        errored.close();
                        return;
                    }
                    camera = null;
                    errored.close();
                    ready = false;
                    closeSessionOnly();
                    scheduleRetry(expectedGeneration);
                }
            }, cameraHandler);
        } catch (Exception failure) {
            Log.w(TAG, "open failed; will retry", failure);
            closeCamera();
            scheduleRetry(expectedGeneration);
        }
    }
    private void configureSession(long expectedGeneration, CameraDevice expectedCamera) {
        if (!isGenerationActive(expectedGeneration) || camera != expectedCamera
                || jpegReader == null || previewReader == null) return;
        try {
            expectedCamera.createCaptureSession(java.util.Arrays.asList(
                    previewReader.getSurface(), jpegReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession configured) {
                            if (!isGenerationActive(expectedGeneration) || camera != expectedCamera) {
                                configured.close();
                                return;
                            }
                            session = configured;
                            try {
                                CaptureRequest.Builder preview = expectedCamera.createCaptureRequest(
                                        CameraDevice.TEMPLATE_PREVIEW);
                                preview.addTarget(previewReader.getSurface());
                                configureAuto(preview);
                                previewRequest = preview.build();
                                if (!standby) configured.setRepeatingRequest(previewRequest, null, cameraHandler);
                                ready = true;
                                Log.i(TAG, "service-owned Camera2 session ready id=" + cameraId);
                            } catch (Exception failure) {
                                Log.w(TAG, "repeating preview failed", failure);
                                closeCamera();
                                scheduleRetry(expectedGeneration);
                            }
                        }

                        @Override public void onConfigureFailed(CameraCaptureSession failed) {
                            Log.w(TAG, "Camera2 session configuration failed");
                            failed.close();
                            ready = false;
                            closeCamera();
                            scheduleRetry(expectedGeneration);
                        }
                    }, cameraHandler);
        } catch (Exception failure) {
            Log.w(TAG, "create session failed", failure);
            closeCamera();
            scheduleRetry(expectedGeneration);
        }
    }

    private void captureStill(long expectedGeneration, Callback callback) {
        if (!isGenerationActive(expectedGeneration) || session == null || camera == null || jpegReader == null) {
            finishFailure(callback);
            return;
        }
        try {
            CaptureRequest.Builder still = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(jpegReader.getSurface());
            configureAuto(still);
            still.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            session.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureFailed(CameraCaptureSession captureSession,
                                                       CaptureRequest request, CaptureFailure failure) {
                    if (generation == expectedGeneration) finishFailure(callback);
                }
            }, cameraHandler);
        } catch (Exception failure) {
            Log.w(TAG, "still capture failed to start", failure);
            finishFailure(callback);
        }
    }

    private void onJpegAvailable(ImageReader reader) {
        if (reader != jpegReader || !started) return;
        Image image = null;
        try {
            image = reader.acquireNextImage();
            if (image == null) return;
            CameraCapturePort.Callback callback = pendingCallback;
            if (callback == null || !captureInFlight.get()) return;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            File file = writeDurably(bytes);
            pendingCallback = null;
            captureInFlight.set(false);
            callback.onCameraCaptureSucceeded(file, System.currentTimeMillis());
        } catch (Exception failure) {
            Log.e(TAG, "JPEG delivery failed", failure);
            CameraCapturePort.Callback callback = pendingCallback;
            if (callback != null) finishFailure(callback);
        } finally {
            if (image != null) image.close();
        }
    }

    private File writeDurably(byte[] bytes) throws IOException {
        File root = new File(context.getFilesDir(), "darkcat-field-capture");
        if (!root.exists() && !root.mkdirs()) throw new IOException("cannot create field capture directory");
        File temporary = new File(root, UUID.randomUUID() + ".jpg.tmp");
        File destination = new File(root, temporary.getName().replace(".tmp", ""));
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        if (!temporary.renameTo(destination)) throw new IOException("cannot publish field JPEG");
        return destination;
    }

    private void finishFailure(CameraCapturePort.Callback callback) {
        if (callback == null || pendingCallback != callback
                || !captureInFlight.compareAndSet(true, false)) return;
        pendingCallback = null;
        callback.onCameraCaptureFailed();
    }

    private void failPendingCapture() {
        CameraCapturePort.Callback callback = pendingCallback;
        if (callback != null) finishFailure(callback);
    }

    private void closeCamera() {
        ready = false;
        closeSessionOnly();
        if (camera != null) {
            camera.close();
            camera = null;
        }
        if (jpegReader != null) { jpegReader.close(); jpegReader = null; }
        if (previewReader != null) { previewReader.close(); previewReader = null; }
        previewRequest = null;
    }

    private void closeSessionOnly() {
        if (session != null) { session.close(); session = null; }
    }

    private void scheduleRetry(long expectedGeneration) {
        if (isGenerationActive(expectedGeneration)) {
            cameraHandler.postDelayed(() -> openIfNeeded(expectedGeneration), RETRY_DELAY_MS);
        }
    }

    private boolean isGenerationActive(long expectedGeneration) {
        return started && generation == expectedGeneration;
    }

    private String chooseBackCamera() throws CameraAccessException {
        String fallback = null;
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
            if (fallback == null) fallback = id;
        }
        return fallback;
    }

    private static Size chooseJpegSize(CameraCharacteristics characteristics) {
        android.hardware.camera2.params.StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map == null ? null : map.getOutputSizes(ImageFormat.JPEG);
        if (sizes == null || sizes.length == 0) return new Size(1920, 1080);
        List<PhotoResolutionPolicy.SizeValue> choices = new ArrayList<>();
        for (Size size : sizes) {
            choices.add(new PhotoResolutionPolicy.SizeValue(size.getWidth(), size.getHeight()));
        }
        PhotoResolutionPolicy.SizeValue selected = PhotoResolutionPolicy.chooseDefault(choices, MAX_JPEG_PIXELS);
        return selected == null ? sizes[0] : new Size(selected.width, selected.height);
    }

    private static Size choosePreviewSize(CameraCharacteristics characteristics) {
        android.hardware.camera2.params.StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map == null ? null : map.getOutputSizes(ImageFormat.YUV_420_888);
        if (sizes == null || sizes.length == 0) return new Size(640, 480);
        Size best = null;
        for (Size size : sizes) {
            long pixels = (long) size.getWidth() * size.getHeight();
            long bestPixels = best == null ? -1L : (long) best.getWidth() * best.getHeight();
            if (pixels <= 1280L * 720L && pixels > bestPixels) best = size;
        }
        return best == null ? sizes[0] : best;
    }

    private static void configureAuto(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
    }

    private static void closeLatest(ImageReader reader) {
        Image image = null;
        try { image = reader.acquireLatestImage(); }
        finally { if (image != null) image.close(); }
    }
}
