package ru.darkcat.camera.location;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class GpsStateEvaluatorTest {
    private static final long NOW = TimeUnit.SECONDS.toNanos(100L);
    private final GpsPolicy policy = GpsPolicy.strictDefault();
    private final GpsStateEvaluator evaluator = new GpsStateEvaluator();

    @Test
    public void runningWithoutFixIsYellowSearching() {
        GpsState state = evaluator.evaluate(GpsSnapshot.running(null), NOW, policy);
        assertEquals(GpsIndicator.YELLOW, state.getIndicator());
        assertEquals(GpsIssue.SEARCHING, state.getIssue());
        assertNull(state.getLocationAge());
    }

    @Test
    public void freshAccurateFixIsGreenAndAccuracyIsDynamic() {
        GpsState first = evaluator.evaluate(
                GpsSnapshot.running(fix(4.2f, 1_000L)), NOW, policy);
        GpsState second = evaluator.evaluate(
                GpsSnapshot.running(fix(6.6f, 1_000L)), NOW, policy);

        assertEquals(GpsIndicator.GREEN, first.getIndicator());
        assertEquals(LocationAge.FRESH, first.getLocationAge());
        assertEquals(4.2f, first.getAccuracyMeters(), 0.001f);
        assertEquals("±4 м", first.getAccuracyLabel());
        assertEquals("±7 м", second.getAccuracyLabel());
    }

    @Test
    public void poorAccuracyIsYellow() {
        GpsState state = evaluator.evaluate(
                GpsSnapshot.running(fix(7.01f, 500L)), NOW, policy);
        assertEquals(GpsIndicator.YELLOW, state.getIndicator());
        assertEquals(GpsIssue.POOR_ACCURACY, state.getIssue());
    }

    @Test
    public void accurateFixTransitionsFreshAgingStaleUsingElapsedRealtime() {
        GpsSnapshot snapshot = GpsSnapshot.running(new LocationFix(
                64.5d, 30.5d, 4.0f, NOW, 1L, "gps"));

        GpsState fresh = evaluator.evaluate(snapshot, NOW + millis(5_000L), policy);
        GpsState aging = evaluator.evaluate(snapshot, NOW + millis(5_001L), policy);
        GpsState lastAging = evaluator.evaluate(snapshot, NOW + millis(15_000L), policy);
        GpsState stale = evaluator.evaluate(snapshot, NOW + millis(15_001L), policy);

        assertEquals(LocationAge.FRESH, fresh.getLocationAge());
        assertEquals(GpsIndicator.GREEN, fresh.getIndicator());
        assertEquals(LocationAge.AGING, aging.getLocationAge());
        assertEquals(GpsIndicator.YELLOW, aging.getIndicator());
        assertEquals(LocationAge.AGING, lastAging.getLocationAge());
        assertEquals(LocationAge.STALE, stale.getLocationAge());
        assertEquals(GpsIndicator.RED, stale.getIndicator());
        assertEquals(GpsIssue.STALE_FIX, stale.getIssue());
    }

    @Test
    public void permissionDisabledAndUnavailableAreRed() {
        assertRed(GpsSourceStatus.PERMISSION_DENIED, GpsIssue.PERMISSION_DENIED);
        assertRed(GpsSourceStatus.LOCATION_DISABLED, GpsIssue.LOCATION_DISABLED);
        assertRed(GpsSourceStatus.PROVIDER_UNAVAILABLE, GpsIssue.PROVIDER_UNAVAILABLE);
        assertRed(GpsSourceStatus.ERROR, GpsIssue.SOURCE_ERROR);
        assertRed(GpsSourceStatus.STOPPED, GpsIssue.STOPPED);
    }

    @Test
    public void futureMonotonicTimestampCannotBecomeGreen() {
        LocationFix future = new LocationFix(1.0d, 2.0d, 1.0f, NOW + 1L, 1L, "gps");
        GpsState state = evaluator.evaluate(GpsSnapshot.running(future), NOW, policy);
        assertEquals(GpsIndicator.RED, state.getIndicator());
        assertEquals(LocationAge.STALE, state.getLocationAge());
        assertEquals(GpsIssue.MONOTONIC_CLOCK_MISMATCH, state.getIssue());
    }

    private void assertRed(GpsSourceStatus status, GpsIssue issue) {
        GpsState state = evaluator.evaluate(GpsSnapshot.unavailable(status), NOW, policy);
        assertEquals(GpsIndicator.RED, state.getIndicator());
        assertEquals(issue, state.getIssue());
    }

    private static LocationFix fix(float accuracy, long ageMillis) {
        return new LocationFix(
                64.588210d,
                30.599140d,
                accuracy,
                NOW - millis(ageMillis),
                1_723_000_000_000L,
                "gps");
    }

    private static long millis(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }
}
