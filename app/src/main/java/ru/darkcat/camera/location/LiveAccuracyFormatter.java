package ru.darkcat.camera.location;

import java.util.Locale;

/** Formats the exact accuracy reported by Location; no UI buckets or synthetic timer values. */
public final class LiveAccuracyFormatter {
    public static String format(float meters) {
        if (Float.isNaN(meters) || Float.isInfinite(meters) || meters < 0f) return "±— м";
        if (meters < 10f) return String.format(Locale.US, "±%.1f м", meters).replace('.', ',');
        return String.format(Locale.US, "±%.0f м", meters);
    }

    private LiveAccuracyFormatter() { }
}
