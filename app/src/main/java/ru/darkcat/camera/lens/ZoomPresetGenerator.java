package ru.darkcat.camera.lens;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Picks buttons from actual controller zoom ratios, without inventing a 0.5x capability. */
public final class ZoomPresetGenerator {
    public static final class Preset {
        public final int index; public final float ratio;
        Preset(int index, float ratio) { this.index = index; this.ratio = ratio; }
        public String label() { return String.format(Locale.US, ratio < 1f ? "%.2f×" : "%.1f×", ratio); }
    }

    public static List<Preset> generate(float[] ratios, boolean physicalWideAvailable) {
        ArrayList<Preset> result = new ArrayList<>();
        if (ratios == null || ratios.length == 0) return result;
        float minimum = min(ratios), maximum = max(ratios);
        if (physicalWideAvailable && minimum > 0f && minimum < .98f)
            add(result, new Preset(closest(ratios, minimum), minimum));
        add(result, presetNear(ratios, 1f));
        // Do not turn a 1.0/1.1/1.2 controller range into a row of misleading quick buttons.
        if (maximum >= 1.60f) add(result, presetNear(ratios, 2f));
        if (maximum >= 4.20f) add(result, presetNear(ratios, 5f));
        if (result.isEmpty()) add(result, new Preset(closest(ratios, 1f), ratios[closest(ratios, 1f)]));
        return result;
    }

    private static Preset presetNear(float[] ratios, float target) {
        int index = closest(ratios, target);
        return index < 0 ? null : new Preset(index, ratios[index]);
    }

    private static float min(float[] ratios) {
        float result = Float.POSITIVE_INFINITY;
        for (float ratio : ratios) if (ratio > 0f && ratio < result) result = ratio;
        return result == Float.POSITIVE_INFINITY ? 0f : result;
    }

    private static float max(float[] ratios) {
        float result = 0f;
        for (float ratio : ratios) if (ratio > result) result = ratio;
        return result;
    }

    private static int closest(float[] ratios, float target) {
        int selected = -1; float difference = Float.MAX_VALUE;
        for (int i = 0; i < ratios.length; i++) {
            if (!(ratios[i] > 0f)) continue;
            float candidate = Math.abs(ratios[i] - target);
            if (candidate < difference) { selected = i; difference = candidate; }
        }
        return selected;
    }

    private static void add(List<Preset> presets, Preset candidate) {
        if (candidate == null || candidate.index < 0) return;
        for (Preset present : presets) if (present.index == candidate.index) return;
        presets.add(candidate);
    }

    private ZoomPresetGenerator() { }
}
