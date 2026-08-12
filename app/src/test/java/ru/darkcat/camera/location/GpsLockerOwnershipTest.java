package ru.darkcat.camera.location;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GpsLockerOwnershipTest {
    @Test public void fieldOnlyRequiresLocker() {
        GpsLockerOwnership state = new GpsLockerOwnership(false, true);
        assertFalse(state.isRequestedByUser());
        assertTrue(state.isRequestedByField());
        assertTrue(state.isLockerRequired());
    }

    @Test public void userOnlyRequiresLocker() {
        GpsLockerOwnership state = new GpsLockerOwnership(true, false);
        assertTrue(state.isRequestedByUser());
        assertFalse(state.isRequestedByField());
        assertTrue(state.isLockerRequired());
    }

    @Test public void fieldAndUserRemainIndependent() {
        GpsLockerOwnership state = new GpsLockerOwnership(true, true);
        assertTrue(state.isRequestedByUser());
        assertTrue(state.isRequestedByField());
        assertTrue(state.isLockerRequired());
    }

    @Test public void fieldOffLeavesUserLockerRunning() {
        GpsLockerOwnership state = new GpsLockerOwnership(true, true).withFieldRequest(false);
        assertTrue(state.isRequestedByUser());
        assertFalse(state.isRequestedByField());
        assertTrue(state.isLockerRequired());
    }

    @Test public void stopAllClearsBothOwners() {
        GpsLockerOwnership state = new GpsLockerOwnership(true, true).stopAll();
        assertFalse(state.isRequestedByUser());
        assertFalse(state.isRequestedByField());
        assertFalse(state.isLockerRequired());
    }
}
