package ru.darkcat.camera.data;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class StorageModeTest {
    @Test public void unknownLegacyValueFallsBackToVault() { assertEquals(StorageMode.VAULT, StorageMode.fromPreference("unknown")); }
    @Test public void mediaStoreRoundTrips() { assertEquals(StorageMode.MEDIASTORE, StorageMode.fromPreference(StorageMode.MEDIASTORE.preferenceValue())); }
}
