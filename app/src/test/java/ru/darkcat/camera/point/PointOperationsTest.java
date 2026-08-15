package ru.darkcat.camera.point;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class PointOperationsTest {
    @Test public void explicitMergeAndSplitKeepMediaFacts() {
        ShootingPoint left = ShootingPoint.draft(1, Arrays.asList(media("a", 1)));
        ShootingPoint right = ShootingPoint.draft(2, Arrays.asList(media("b", 2)));
        ShootingPoint merged = PointOperations.mergeAdjacent(left, right);
        assertEquals(2, merged.media().size());
        assertEquals(2, PointOperations.splitHere(merged, 1).size());
    }

    @Test(expected = IllegalStateException.class)
    public void lockedPointCannotBeMergedSilently() {
        ShootingPoint locked = ShootingPoint.draft(1, Arrays.asList(media("a", 1)))
                .withLifecycle(PointLifecycle.LOCKED);
        PointOperations.mergeAdjacent(locked, locked);
    }

    @Test(expected = IllegalStateException.class)
    public void publishedPointCannotBeReopenedAsDraft() {
        ShootingPoint published = ShootingPoint.draft(1, Arrays.asList(media("a", 1)))
                .withLifecycle(PointLifecycle.REVIEWED)
                .withLifecycle(PointLifecycle.UPLOADING)
                .withLifecycle(PointLifecycle.PUBLISHED);
        published.withLifecycle(PointLifecycle.DRAFT);
    }

    private static PointMedia media(String id, long time) {
        return new PointMedia(id, time, 64d, 30d, 2f);
    }
}
