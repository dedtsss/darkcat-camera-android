package ru.darkcat.camera.field;

public enum FieldModeState {
    DISABLED,
    AWAITING_VISIBLE_START,
    STARTING,
    ACTIVE,
    DEGRADED,
    STOPPING,
    ERROR;

    private static volatile FieldModeState current = DISABLED;
    private static volatile boolean volumeTriggerActive;

    /** Process-local diagnostic flag; authoritative state remains in FieldModeController. */
    public static boolean isRunning() {
        FieldModeState value = current;
        return value == STARTING || value == ACTIVE || value == DEGRADED;
    }

    /** True only while Field Mode runs and its Volume+ adapter is configured active. */
    public static boolean isVolumeTriggerActive() {
        FieldModeState value = current;
        return (value == ACTIVE || value == DEGRADED) && volumeTriggerActive;
    }

    static void updateDiagnostics(FieldModeState state, boolean volumeEnabled) {
        current = state;
        volumeTriggerActive = volumeEnabled;
    }
}
