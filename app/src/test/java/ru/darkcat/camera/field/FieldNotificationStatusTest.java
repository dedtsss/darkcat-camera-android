package ru.darkcat.camera.field;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class FieldNotificationStatusTest {
    @Test public void reportsServiceOwnerReadinessBeforeActivityVisibility() {
        assertEquals("Камера готова", FieldNotificationStatus.cameraState(false, true, true));
    }

    @Test public void distinguishesVisibleActivityAndStartingService() {
        assertEquals("Камера используется экраном", FieldNotificationStatus.cameraState(false, false, true));
        assertEquals("Камера инициализируется", FieldNotificationStatus.cameraState(false, false, false));
    }

    @Test public void neverUsesObsoleteOpenCameraRecoveryPrompt() {
        assertFalse(FieldNotificationStatus.cameraState(false, false, false).contains("Откройте"));
    }
}
