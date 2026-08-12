package ru.darkcat.camera.lens;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public final class LensCapabilityMapperTest {
    @Test public void mapsRealFocalRatiosWithoutFixedCameraIds() {
        assertEquals(4.0f, LensCapabilityMapper.standardFocal(Arrays.asList(1.8f, 4f, 8f)), .001f);
        assertTrue(LensCapabilityMapper.hasUltraWide(Arrays.asList(1.8f, 4f, 8f)));
        assertTrue(LensCapabilityMapper.label(8f, 4f, false).startsWith("Телефото"));
    }
}
