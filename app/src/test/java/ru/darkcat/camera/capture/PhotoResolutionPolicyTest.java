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
}
