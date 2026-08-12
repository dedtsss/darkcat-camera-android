package ru.darkcat.camera.diagnostics;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.LocationManager;
import android.os.Build;
import android.util.Size;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.Set;

import ru.darkcat.camera.field.FieldCaptureBridge;
import ru.darkcat.camera.field.FieldModeState;
import ru.darkcat.camera.field.FieldTriggerDiagnostics;
import ru.darkcat.camera.location.LocationSnapshotStore;

/** Produces a media-free capability report suitable for Pixel hardware validation. */
public final class CameraDiagnosticsExporter {
    public static File export(Context context, String selectedCamera) throws Exception {
        JSONObject report = build(context, selectedCamera);
        File external = context.getExternalFilesDir(null);
        File directory = new File(external == null ? context.getFilesDir() : external, "diagnostics");
        if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("diagnostics directory");
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File output = new File(directory, "darkcat-camera-diagnostics-" + timestamp + ".json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
        return output;
    }

    public static JSONObject build(Context context, String selectedCamera) throws Exception {
        JSONObject root = new JSONObject();
        root.put("generatedAt", System.currentTimeMillis());
        root.put("manufacturer", Build.MANUFACTURER);
        root.put("model", Build.MODEL);
        root.put("device", Build.DEVICE);
        root.put("androidRelease", Build.VERSION.RELEASE);
        root.put("sdkInt", Build.VERSION.SDK_INT);
        root.put("securityPatch", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? Build.VERSION.SECURITY_PATCH : JSONObject.NULL);
        root.put("selectedCamera", selectedCamera == null ? JSONObject.NULL : selectedCamera);
        root.put("cameraApiDefault", "Camera2 with capability fallback");
        root.put("fieldModeRunning", FieldModeState.isRunning());
        root.put("gpsLockerRunning", LocationSnapshotStore.isLockerRunning());
        root.put("bluetoothVolumeTrigger", FieldModeState.isVolumeTriggerActive());
        root.put("fieldServiceCameraReady", FieldCaptureBridge.isCameraBridgeReady());
        FieldTriggerDiagnostics.Snapshot trigger = FieldTriggerDiagnostics.snapshot();
        JSONObject triggerReport = new JSONObject();
        triggerReport.put("total", trigger.total);
        triggerReport.put("lastSource", trigger.lastSource);
        triggerReport.put("lastKeyCode", trigger.lastKeyCode);
        triggerReport.put("lastElapsedRealtime", trigger.lastElapsedRealtime);
        root.put("fieldTriggerDiagnostics", triggerReport);

        LocationSnapshotStore.Snapshot location = LocationSnapshotStore.latest();
        JSONObject gps = new JSONObject();
        gps.put("provider", location == null ? JSONObject.NULL : location.provider);
        gps.put("accuracyMeters", location == null ? JSONObject.NULL : location.accuracyMeters);
        gps.put("ageMillis", location == null ? JSONObject.NULL : location.ageMillis(android.os.SystemClock.elapsedRealtime()));
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        gps.put("gpsProviderEnabled", locationManager != null && safeProviderEnabled(locationManager, LocationManager.GPS_PROVIDER));
        root.put("gps", gps);

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        JSONArray cameras = new JSONArray();
        if (manager != null) for (String id : manager.getCameraIdList()) cameras.put(camera(manager, id));
        root.put("cameras", cameras);
        return root;
    }

    private static JSONObject camera(CameraManager manager, String id) throws Exception {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        JSONObject camera = new JSONObject();
        camera.put("id", id);
        camera.put("lensFacing", value(c.get(CameraCharacteristics.LENS_FACING)));
        camera.put("hardwareLevel", hardwareLevel(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)));
        camera.put("flashAvailable", value(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)));
        camera.put("focalLengthsMm", array(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)));
        camera.put("sensorPhysicalSizeMm", String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)));
        camera.put("afModes", array(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)));
        camera.put("oisModes", array(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)));
        int[] capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        camera.put("capabilities", array(capabilities));
        boolean privateReprocessing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING);
        boolean yuvReprocessing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING);
        camera.put("privateReprocessing", privateReprocessing);
        camera.put("yuvReprocessing", yuvReprocessing);
        boolean zslRequestKey = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && hasKey(c.getAvailableCaptureRequestKeys(), CaptureRequest.CONTROL_ENABLE_ZSL);
        boolean zslResultKey = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && hasKey(c.getAvailableCaptureResultKeys(), CaptureResult.CONTROL_ENABLE_ZSL);
        camera.put("zslRequestKeyAvailable", zslRequestKey);
        camera.put("zslResultKeyAvailable", zslResultKey);
        camera.put("zslReprocessingCapable", privateReprocessing || yuvReprocessing);
        camera.put("zslRequestAvailable", zslRequestKey);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Set<String> physicalIds = c.getPhysicalCameraIds();
            camera.put("physicalCameraIds", new JSONArray(physicalIds));
            camera.put("logicalMultiCamera", contains(capabilities, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA));
            JSONArray physical = new JSONArray();
            for (String physicalId : physicalIds) {
                try {
                    physical.put(physicalCamera(manager, physicalId));
                } catch (Exception inaccessible) {
                    physical.put(new JSONObject().put("id", physicalId)
                            .put("characteristicsError", inaccessible.getClass().getSimpleName()));
                }
            }
            camera.put("physicalCameras", physical);
        }
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        JSONObject streams = new JSONObject();
        if (map != null) {
            streams.put("jpeg", sizes(map.getOutputSizes(ImageFormat.JPEG)));
            streams.put("yuv420", sizes(map.getOutputSizes(ImageFormat.YUV_420_888)));
            streams.put("private", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? sizes(map.getOutputSizes(ImageFormat.PRIVATE)) : new JSONArray());
        }
        camera.put("streams", streams);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                CameraExtensionCharacteristics extensions = manager.getCameraExtensionCharacteristics(id);
                camera.put("extensionModes", new JSONArray(extensions.getSupportedExtensions()));
            } catch (RuntimeException unsupported) {
                camera.put("extensionModes", new JSONArray());
            }
        }
        return camera;
    }

    private static JSONObject physicalCamera(CameraManager manager, String id) throws Exception {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        JSONObject camera = new JSONObject();
        camera.put("id", id);
        camera.put("lensFacing", value(c.get(CameraCharacteristics.LENS_FACING)));
        camera.put("hardwareLevel", hardwareLevel(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)));
        camera.put("flashAvailable", value(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)));
        camera.put("focalLengthsMm", array(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)));
        camera.put("sensorPhysicalSizeMm", String.valueOf(c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)));
        camera.put("afModes", array(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)));
        camera.put("oisModes", array(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)));
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            JSONObject streams = new JSONObject();
            streams.put("jpeg", sizes(map.getOutputSizes(ImageFormat.JPEG)));
            streams.put("yuv420", sizes(map.getOutputSizes(ImageFormat.YUV_420_888)));
            camera.put("streams", streams);
        }
        return camera;
    }

    private static JSONArray sizes(Size[] sizes) {
        JSONArray result = new JSONArray();
        if (sizes == null) return result;
        int count = 0;
        for (Size size : sizes) {
            if (count++ >= 40) break;
            result.put(size.getWidth() + "x" + size.getHeight());
        }
        return result;
    }

    private static JSONArray array(int[] values) {
        JSONArray result = new JSONArray();
        if (values != null) for (int value : values) result.put(value);
        return result;
    }

    private static JSONArray array(float[] values) {
        JSONArray result = new JSONArray();
        if (values != null) for (float value : values) {
            if (!Float.isNaN(value) && !Float.isInfinite(value)) {
                try { result.put((double) value); } catch (org.json.JSONException ignored) { }
            }
        }
        return result;
    }

    private static boolean contains(int[] values, int wanted) {
        if (values != null) for (int value : values) if (value == wanted) return true;
        return false;
    }

    private static boolean hasKey(List<?> keys, Object wanted) {
        return keys != null && keys.contains(wanted);
    }

    private static String hardwareLevel(Integer level) {
        if (level == null) return "UNKNOWN";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) return "LEGACY";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) return "LIMITED";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL) return "FULL";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3) return "LEVEL_3";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL) return "EXTERNAL";
        return String.valueOf(level);
    }

    private static Object value(Object value) { return value == null ? JSONObject.NULL : value; }
    private static boolean safeProviderEnabled(LocationManager manager, String provider) {
        try { return manager.isProviderEnabled(provider); } catch (RuntimeException ignored) { return false; }
    }

    private CameraDiagnosticsExporter() { }
}
