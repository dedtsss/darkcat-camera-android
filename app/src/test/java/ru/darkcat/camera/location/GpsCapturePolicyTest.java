package ru.darkcat.camera.location;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GpsCapturePolicyTest {
    private static final long NOW = TimeUnit.SECONDS.toNanos(100L);
    private final GpsCapturePolicy gate = new GpsCapturePolicy();

    @Test
    public void strictDefaultAcceptsAccuracyAtSevenMeters() {
        CaptureDecision result = gate.evaluate(
                GpsSnapshot.running(fix(7.0f, 1_000L)),
                NOW,
                GpsPolicy.strictDefault());
        assertTrue(result.isAllowed());
        assertEquals(CaptureBlockReason.NONE, result.getBlockReason());
    }

    @Test
    public void strictDefaultRejectsAccuracyWorseThanSevenMeters() {
        CaptureDecision result = gate.evaluate(
                GpsSnapshot.running(fix(7.1f, 1_000L)),
                NOW,
                GpsPolicy.strictDefault());
        assertFalse(result.isAllowed());
        assertEquals(CaptureBlockReason.ACCURACY_TOO_LOW, result.getBlockReason());
    }

    @Test
    public void strictModeAcceptsAccurateAgingFixButRejectsStaleFix() {
        CaptureDecision aging = gate.evaluate(
                GpsSnapshot.running(fix(3.0f, 10_000L)),
                NOW,
                GpsPolicy.strictDefault());
        CaptureDecision stale = gate.evaluate(
                GpsSnapshot.running(fix(3.0f, 15_001L)),
                NOW,
                GpsPolicy.strictDefault());

        assertTrue(aging.isAllowed());
        assertEquals(GpsIndicator.YELLOW, aging.getGpsState().getIndicator());
        assertFalse(stale.isAllowed());
        assertEquals(CaptureBlockReason.STALE_FIX, stale.getBlockReason());
    }

    @Test
    public void strictModeRejectsNoFixAndUnavailableAccuracy() {
        CaptureDecision none = gate.evaluate(
                GpsSnapshot.running(null), NOW, GpsPolicy.strictDefault());
        CaptureDecision unknownAccuracy = gate.evaluate(
                GpsSnapshot.running(fix(Float.NaN, 0L)),
                NOW,
                GpsPolicy.strictDefault());
        assertEquals(CaptureBlockReason.NO_FIX, none.getBlockReason());
        assertEquals(CaptureBlockReason.ACCURACY_UNAVAILABLE, unknownAccuracy.getBlockReason());
    }

    @Test
    public void advisoryModeNeverBlocksCaptureButStillReportsRedState() {
        CaptureDecision result = gate.evaluate(
                GpsSnapshot.unavailable(GpsSourceStatus.LOCATION_DISABLED),
                NOW,
                GpsPolicy.advisoryDefault());
        assertTrue(result.isAllowed());
        assertEquals(GpsIndicator.RED, result.getGpsState().getIndicator());
    }

    private static LocationFix fix(float accuracy, long ageMillis) {
        return new LocationFix(
                1.0d,
                2.0d,
                accuracy,
                NOW - TimeUnit.MILLISECONDS.toNanos(ageMillis),
                1L,
                "gps");
    }
}
