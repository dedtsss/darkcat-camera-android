package ru.darkcat.camera.vault;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VaultShareCachePolicyTest {
    @Test public void keepsFreshShareFileForReceivingApp() {
        long now = VaultShareCachePolicy.TTL_MILLIS + 1_000_000L;
        assertFalse(VaultShareCachePolicy.shouldDelete(now - VaultShareCachePolicy.TTL_MILLIS + 1L, now));
    }
    @Test public void cleansExpiredShareFileAfterRestartScan() {
        long now = 1_000_000L;
        assertTrue(VaultShareCachePolicy.shouldDelete(now - VaultShareCachePolicy.TTL_MILLIS, now));
        assertTrue(VaultShareCachePolicy.shouldDelete(0L, now));
    }
    @Test public void clockRollbackDoesNotEagerlyDeletePreparedShare() {
        assertFalse(VaultShareCachePolicy.shouldDelete(2_000L, 1_000L));
    }
}
