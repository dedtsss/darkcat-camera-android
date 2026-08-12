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
}
