package ru.darkcat.camera.haptic;

import org.junit.Test;
import static org.junit.Assert.*;

public final class HapticPresetTest {
    @Test public void unknownPreferenceUsesSafeMedium() { assertEquals(HapticPreset.MEDIUM, HapticPreset.fromPreference("bad")); }
    @Test public void strongIsLongerThanWeak() { assertTrue(HapticPreset.STRONG.successDurationMillis > HapticPreset.WEAK.successDurationMillis); }
}
