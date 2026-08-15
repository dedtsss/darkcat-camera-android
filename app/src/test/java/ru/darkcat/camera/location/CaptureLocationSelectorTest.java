package ru.darkcat.camera.location;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class CaptureLocationSelectorTest {
    private final GpsStateEvaluator evaluator = new GpsStateEvaluator();
    private final GpsPolicy policy = GpsPolicy.advisoryDefault();

    @Test public void keepsFreshAndAgingFixEvenWhenAdvisoryAccuracyIsPoor() {
        LocationFix fix = new LocationFix(64.5, 30.5, 20f, 1_000_000_000L, 1L, "gps");
        GpsState fresh = evaluator.evaluate(GpsSnapshot.running(fix), 2_000_000_000L, policy);
        GpsState aging = evaluator.evaluate(GpsSnapshot.running(fix), 8_000_000_000L, policy);
        assertSame(fix, CaptureLocationSelector.select(fresh));
        assertSame(fix, CaptureLocationSelector.select(aging));
    }

    @Test public void rejectsStaleOrUnavailableFixInsteadOfMislabelingCapture() {
        LocationFix fix = new LocationFix(64.5, 30.5, 4f, 1_000_000_000L, 1L, "gps");
        GpsState stale = evaluator.evaluate(GpsSnapshot.running(fix), 20_000_000_000L, policy);
        GpsState stopped = evaluator.evaluate(GpsSnapshot.stopped(), 2_000_000_000L, policy);
        assertNull(CaptureLocationSelector.select(stale));
        assertNull(CaptureLocationSelector.select(stopped));
    }

    @Test public void revalidatesTheOriginalShutterFixWithoutSubstitutingALaterFix() {
        LocationFix fix = new LocationFix(64.5, 30.5, 4f, 1_000_000_000L, 1L, "gps");
        assertSame(fix, CaptureLocationSelector.validateAtCapture(fix, 15_000_000_000L, 15_000L));
        assertNull(CaptureLocationSelector.validateAtCapture(fix, 17_000_000_000L, 15_000L));
        assertNull(CaptureLocationSelector.validateAtCapture(fix, 500_000_000L, 15_000L));
    }
}
