package ru.darkcat.camera.upload;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SyncRetryPolicyTest {
    @Test public void startsAtWorkManagerCompatibleThirtySeconds() {
        assertEquals(30_000L, SyncRetryPolicy.diagnosticDelayMillis(1));
    }

    @Test public void growsExponentiallyAndStaysBounded() {
        assertEquals(60_000L, SyncRetryPolicy.diagnosticDelayMillis(2));
        assertEquals(6L * 60L * 60L * 1000L, SyncRetryPolicy.diagnosticDelayMillis(99));
    }
}
