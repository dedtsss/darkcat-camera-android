package ru.darkcat.camera.capture;

public enum CaptureMode {
    MAX_SPEED,
    SHARP_PRIORITY;

    public static CaptureMode fromPreference(String value) {
        return "sharp_priority".equals(value) ? SHARP_PRIORITY : MAX_SPEED;
    }
}
