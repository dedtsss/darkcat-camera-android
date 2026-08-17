package ru.darkcat.camera.field;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FieldStandbyPolicyTest {
    @Test public void stationaryGraceThenStandbyAndMotionReturnsHot() {
        FieldStandbyPolicy policy = new FieldStandbyPolicy();
        assertEquals(FieldStandbyPolicy.State.GRACE, policy.observe(false, 100L));
        assertEquals(FieldStandbyPolicy.State.GRACE,
                policy.observe(false, 100L + FieldStandbyPolicy.STATIONARY_GRACE_MS - 1L));
        assertEquals(FieldStandbyPolicy.State.STANDBY,
                policy.observe(false, 100L + FieldStandbyPolicy.STATIONARY_GRACE_MS));
        assertEquals(FieldStandbyPolicy.State.HOT, policy.observe(true, 500_000L));
    }

    @Test public void repeatedMotionKeepsHotWithoutAccumulatingStationaryTime() {
        FieldStandbyPolicy policy = new FieldStandbyPolicy();
        assertEquals(FieldStandbyPolicy.State.HOT, policy.observe(true, 1L));
        assertEquals(FieldStandbyPolicy.State.GRACE, policy.observe(false, 10L));
        assertEquals(FieldStandbyPolicy.State.HOT, policy.observe(true, 10_000L));
        assertEquals(FieldStandbyPolicy.State.GRACE, policy.observe(false, 20_000L));
    }
}
