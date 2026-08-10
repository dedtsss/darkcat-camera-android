package ru.darkcat.camera.location;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class LocationSnapshotStoreTest {
    @After
    public void reset() {
        LocationSnapshotStore.resetForTests();
    }

    @Test
    public void exposesOnlyNonSensitiveFixDiagnosticsWithMonotonicAge() {
        LocationSnapshotStore.setLockerRunning(true);
        LocationSnapshotStore.update(new LocationFix(
                64.588210d,
                30.599140d,
                4.0f,
                TimeUnit.MILLISECONDS.toNanos(1_000L),
                1L,
                "gps"));

        LocationSnapshotStore.Snapshot snapshot = LocationSnapshotStore.latest();
        assertTrue(LocationSnapshotStore.isLockerRunning());
        assertNotNull(snapshot);
        assertEquals("gps", snapshot.provider);
        assertEquals(4.0f, snapshot.accuracyMeters, 0.001f);
        assertEquals(500L, snapshot.ageMillis(1_500L));
        assertEquals(Long.MAX_VALUE, snapshot.ageMillis(999L));

        LocationSnapshotStore.setLockerRunning(false);
        assertFalse(LocationSnapshotStore.isLockerRunning());
    }
}
