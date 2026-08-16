package ru.darkcat.camera.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DashboardInsetsTest {
    @Test public void edgeToEdgeParentUsesTheRuntimeSafeTopInset() {
        assertEquals(72, DashboardInsets.topMargin(72, 0));
    }

    @Test public void alreadyInsetParentDoesNotReceiveTheTopInsetTwice() {
        assertEquals(0, DashboardInsets.topMargin(72, 72));
    }

    @Test public void landscapeOrFullscreenNeverProducesANegativeMargin() {
        assertEquals(0, DashboardInsets.topMargin(0, 0));
        assertEquals(0, DashboardInsets.topMargin(24, 48));
    }
}
