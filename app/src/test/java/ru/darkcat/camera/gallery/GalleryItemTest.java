package ru.darkcat.camera.gallery;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class GalleryItemTest {
    @Test public void newestMediaStoreItemSortsFirstInUnifiedTimeline() {
        assertTrue(GalleryTimelineOrder.newestFirst(20L, "fresh", 10L, "old") < 0);
    }

    @Test public void stableIdBreaksEqualTimestampTies() {
        assertTrue(GalleryTimelineOrder.newestFirst(20L, "a", 20L, "b") < 0);
    }
}
