package ru.darkcat.camera.ui;

/** Pure transition policy for the product Night toggle, independently testable without hardware. */
public final class NightModeState {
    public static final String STANDARD = "preference_photo_mode_std";
    public static final String X_NIGHT = "preference_photo_mode_x_night";

    public static String preNightMode(String current) {
        return current == null || current.isEmpty() || X_NIGHT.equals(current) ? STANDARD : current;
    }

    public static String restoreMode(String saved) {
        return preNightMode(saved);
    }

    public static boolean needsChange(String current, String target) {
        return target != null && !target.equals(current);
    }

    private NightModeState() { }
}
