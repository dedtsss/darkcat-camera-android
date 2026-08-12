package ru.darkcat.camera.point;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PointClustererTest {
    @Test public void closeCoordinatesAndShortIntervalsStayTogether() {
        PointClusterer clusterer = new PointClusterer(5d, 60_000L);
        List<ShootingPoint> points = clusterer.cluster(Arrays.asList(
                media("a", 1_000L, 64.000000, 30.000000),
                media("b", 3_000L, 64.000015, 30.000000),
                media("c", 5_000L, 64.000020, 30.000010)));
        assertEquals(1, points.size());
        assertEquals(3, points.get(0).media().size());
    }

    @Test public void spatialJumpStartsNewPointRegardlessOfTime() {
        List<ShootingPoint> points = new PointClusterer().cluster(Arrays.asList(
                media("a", 1_000L, 64.0, 30.0),
                media("b", 2_000L, 64.001, 30.0)));
        assertEquals(2, points.size());
    }

    @Test public void temporalGapStartsNewPointEvenWhenCoordinatesAreClose() {
        List<ShootingPoint> points = new PointClusterer(5d, 10_000L).cluster(Arrays.asList(
                media("a", 1_000L, 64.0, 30.0),
                media("b", 20_000L, 64.000001, 30.000001)));
        assertEquals(2, points.size());
    }

    @Test public void publishedPointCannotBeAutomaticallyReclustered() {
        ShootingPoint point = ShootingPoint.draft(1, Arrays.asList(media("a", 1, 64d, 30d)))
                .withLifecycle(PointLifecycle.PUBLISHED);
        assertTrue(!PointClusterer.canAutoRecluster(point));
    }

    private static PointMedia media(String id, long time, double lat, double lon) {
        return new PointMedia(id, time, lat, lon, 3f);
    }
}
