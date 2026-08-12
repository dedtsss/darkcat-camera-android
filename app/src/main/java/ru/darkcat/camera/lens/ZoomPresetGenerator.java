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
        if (physicalWideAvailable && ratios[0] < .98f) add(result, new Preset(0, ratios[0]));
        for (float target : new float[]{1f, 2f, 3f, 5f}) {
            int index = closest(ratios, target);
            if (index >= 0) add(result, new Preset(index, ratios[index]));
        }
        if (result.isEmpty()) add(result, new Preset(closest(ratios, 1f), ratios[closest(ratios, 1f)]));
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
        if (candidate.index < 0) return;
        for (Preset present : presets) if (present.index == candidate.index) return;
        presets.add(candidate);
    }

    private ZoomPresetGenerator() { }
}
