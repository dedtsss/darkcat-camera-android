package ru.darkcat.camera.vault;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ThumbnailSamplingTest {
    @Test public void smallImageIsNeverUpsampled() {
        assertEquals(1, ThumbnailSampling.inSampleSize(320, 240, 512));
    }

    @Test public void fullResolutionImageIsDecodedWithBoundedSample() {
        assertEquals(8, ThumbnailSampling.inSampleSize(8000, 6000, 512));
        assertEquals(4, ThumbnailSampling.inSampleSize(4032, 3024, 512));
    }

    @Test public void invalidBoundsFallBackSafely() {
        assertEquals(1, ThumbnailSampling.inSampleSize(-1, -1, 512));
        assertEquals(1, ThumbnailSampling.inSampleSize(4000, 3000, 0));
    }
}
