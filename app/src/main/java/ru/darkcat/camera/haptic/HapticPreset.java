package ru.darkcat.camera.haptic;

/** Stable user-facing haptic strength presets, independent of Android vibrator APIs. */
public enum HapticPreset {
    WEAK(90, 38L, 100, new long[]{0L, 100L, 55L, 170L}, new int[]{0, 100, 0, 135}),
    MEDIUM(180, 55L, 220, new long[]{0L, 150L, 70L, 260L}, new int[]{0, 220, 0, 255}),
    STRONG(255, 78L, 255, new long[]{0L, 190L, 85L, 330L}, new int[]{0, 255, 0, 255});

    public final int successAmplitude;
    public final long successDurationMillis;
    public final int failureAmplitude;
    public final long[] failurePatternMillis;
    public final int[] failureAmplitudes;

    HapticPreset(int successAmplitude, long successDurationMillis, int failureAmplitude,
                 long[] failurePatternMillis, int[] failureAmplitudes) {
        this.successAmplitude = successAmplitude;
        this.successDurationMillis = successDurationMillis;
        this.failureAmplitude = failureAmplitude;
        this.failurePatternMillis = failurePatternMillis;
        this.failureAmplitudes = failureAmplitudes;
    }

    public static HapticPreset fromPreference(String value) {
        if (value != null) {
            for (HapticPreset preset : values()) {
                if (preset.name().equalsIgnoreCase(value)) return preset;
            }
        }
        return MEDIUM;
    }
}
