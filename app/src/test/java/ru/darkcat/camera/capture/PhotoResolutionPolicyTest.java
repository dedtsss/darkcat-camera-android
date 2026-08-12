package ru.darkcat.camera.capture;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public final class PhotoResolutionPolicyTest {
    @Test public void prefersLargestFourByThreeInsideCapabilityLimit() {
        PhotoResolutionPolicy.SizeValue selected = PhotoResolutionPolicy.chooseDefault(Arrays.asList(
                new PhotoResolutionPolicy.SizeValue(4000, 2250),
                new PhotoResolutionPolicy.SizeValue(4000, 3000),
                new PhotoResolutionPolicy.SizeValue(8000, 6000)), 13_000_000L);
        assertEquals(4000, selected.width); assertEquals(3000, selected.height);
    }
    @Test public void fallsBackToSmallestOverLimitWhenNothingFits() {
        PhotoResolutionPolicy.SizeValue selected = PhotoResolutionPolicy.chooseDefault(Arrays.asList(
                new PhotoResolutionPolicy.SizeValue(6000, 4000), new PhotoResolutionPolicy.SizeValue(8000, 6000)), 10_000_000L);
        assertEquals(6000, selected.width);
    }
    @Test public void keepsSupportedUserSelectionAndFallsBackWhenLensDoesNotOfferIt() {
        PhotoResolutionPolicy.SizeValue twelve = new PhotoResolutionPolicy.SizeValue(4032, 3024);
        PhotoResolutionPolicy.SizeValue eight = new PhotoResolutionPolicy.SizeValue(3264, 2448);
        assertSame(eight, PhotoResolutionPolicy.chooseSupported(Arrays.asList(twelve, eight), eight, Long.MAX_VALUE));
        assertSame(eight, PhotoResolutionPolicy.chooseSupported(Arrays.asList(eight), twelve, Long.MAX_VALUE));
    }
    @Test public void formatsOnlyProductFacingResolutionDetails() {
        assertEquals("4032 × 3024 · 12.2 МП · 4:3",
                PhotoResolutionPolicy.label(new PhotoResolutionPolicy.SizeValue(4032, 3024)));
    }
}
