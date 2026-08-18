package ru.darkcat.camera.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DarkCatSettingsPolicyTest {
    @Test public void fieldForcesVaultWithoutChangingConfiguredMode() {
        assertEquals(StorageMode.VAULT,
                DarkCatSettings.effectiveStorageMode(true, StorageMode.MEDIASTORE));
        assertEquals(StorageMode.MEDIASTORE,
                DarkCatSettings.effectiveStorageMode(false, StorageMode.MEDIASTORE));
    }

    @Test public void nullConfiguredModeUsesSafeVaultDefault() {
        assertEquals(StorageMode.VAULT,
                DarkCatSettings.effectiveStorageMode(false, null));
    }
}
