package ru.darkcat.camera.data;

import com.linkedcamera.app.PreferenceKeys;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DarkCatPreferencePolicyTest {
    @Test public void ownsEveryLegacyOverlayThatWouldDuplicateDarkCatChrome() {
        String[] keys = DarkCatPreferencePolicy.ownedUpstreamPreferenceKeys();
        assertTrue(Arrays.asList(keys).contains(PreferenceKeys.ShowTimePreferenceKey));
        assertTrue(Arrays.asList(keys).contains(PreferenceKeys.ShowFreeMemoryPreferenceKey));
        assertTrue(Arrays.asList(keys).contains(PreferenceKeys.ShowISOPreferenceKey));
        assertTrue(Arrays.asList(keys).contains(PreferenceKeys.ShowStampPreferenceKey));
        assertEquals(keys.length, new HashSet<>(Arrays.asList(keys)).size());
    }
}
