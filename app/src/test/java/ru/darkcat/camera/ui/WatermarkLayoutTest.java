package ru.darkcat.camera.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class WatermarkLayoutTest {
    @Test public void bottomRightUsesImageSpaceMargins() {
        WatermarkConfig config = new WatermarkConfig(true, "content://logo", WatermarkConfig.Position.BOTTOM_RIGHT,
                .2f, .8f, false, .3f, 0f);
        WatermarkLayout.Box box = WatermarkLayout.boxes(1000, 500, 2, 1, config).get(0);
        assertEquals(985f, box.right, .001f);
        assertEquals(485f, box.bottom, .001f);
    }

    @Test public void tiledModeReturnsRepeatedBoxes() {
        WatermarkConfig config = new WatermarkConfig(true, "logo", WatermarkConfig.Position.CENTER,
                .2f, 1f, true, .4f, 12f);
        assertEquals(9, WatermarkLayout.boxes(1000, 1000, 1, 1, config).size());
    }
}
