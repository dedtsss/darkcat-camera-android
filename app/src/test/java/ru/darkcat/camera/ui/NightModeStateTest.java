package ru.darkcat.camera.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NightModeStateTest {
    @Test public void restorePreservesTheActualPreNightMode() {
        assertEquals("preference_photo_mode_hdr", NightModeState.preNightMode("preference_photo_mode_hdr"));
        assertEquals("preference_photo_mode_hdr", NightModeState.restoreMode("preference_photo_mode_hdr"));
    }

    @Test public void staleNightModeNeverRestoresNightAgain() {
        assertEquals(NightModeState.STANDARD, NightModeState.restoreMode(NightModeState.X_NIGHT));
        assertEquals(NightModeState.STANDARD, NightModeState.preNightMode(null));
    }

    @Test public void idempotentTransitionDoesNotRequestAnotherCameraChange() {
        assertFalse(NightModeState.needsChange(NightModeState.X_NIGHT, NightModeState.X_NIGHT));
        assertTrue(NightModeState.needsChange(NightModeState.STANDARD, NightModeState.X_NIGHT));
    }
}
