package ru.darkcat.camera.lens;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class ZoomPresetGeneratorTest {
    @Test public void doesNotOfferSubOnePresetWithoutPhysicalWideCapability() {
        List<ZoomPresetGenerator.Preset> presets = ZoomPresetGenerator.generate(new float[]{.6f, 1f, 2f}, false);
        for (ZoomPresetGenerator.Preset preset : presets) assertTrue(preset.ratio >= 1f);
    }
    @Test public void usesActualAvailableRatioForWidePreset() {
        List<ZoomPresetGenerator.Preset> presets = ZoomPresetGenerator.generate(new float[]{.67f, 1f, 1.9f}, true);
        assertEquals(.67f, presets.get(0).ratio, .001f);
    }
    @Test public void avoidsMeaninglessNearOneScatterAndKeepsUsefulCount() {
        List<ZoomPresetGenerator.Preset> presets = ZoomPresetGenerator.generate(
                new float[]{1f, 1.1f, 1.2f, 1.3f}, false);
        assertEquals(1, presets.size());

        List<ZoomPresetGenerator.Preset> useful = ZoomPresetGenerator.generate(
                new float[]{.5f, 1f, 1.9f, 4.8f}, true);
        assertTrue(useful.size() <= 4);
        assertEquals(1f, useful.get(1).ratio, .001f);
    }
}
