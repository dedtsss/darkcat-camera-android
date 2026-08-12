package ru.darkcat.camera.lens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pure lens naming policy; Android Camera2 collection is kept at the UI boundary. */
public final class LensCapabilityMapper {
    public static float standardFocal(List<Float> focalLengths) {
        ArrayList<Float> values = new ArrayList<>();
        if (focalLengths != null) for (Float value : focalLengths) {
            if (value != null && value > 0f && !value.isInfinite() && !value.isNaN()) values.add(value);
        }
        if (values.isEmpty()) return 0f;
        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    public static boolean hasUltraWide(List<Float> focalLengths) {
        float standard = standardFocal(focalLengths);
        if (!(standard > 0f) || focalLengths == null) return false;
        for (Float focal : focalLengths) if (focal != null && focal > 0f && focal / standard < .80f) return true;
        return false;
    }

    public static String label(float focal, float standard, boolean front) {
        if (front) return "Фронт";
        if (!(focal > 0f) || !(standard > 0f)) return "Линза";
        float ratio = focal / standard;
        String type = ratio < .65f ? "Ультраширокоугольная"
                : ratio < .80f ? "Широкоугольная"
                : ratio > 1.25f ? "Телефото" : "Основная";
        return type + " " + String.format(Locale.US, "%.1f×", ratio);
    }

    private LensCapabilityMapper() { }
}
