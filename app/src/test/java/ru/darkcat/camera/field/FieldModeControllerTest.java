package ru.darkcat.camera.field;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FieldModeControllerTest {
    @Test
    public void explicitVisibleEnableStartsRuntimeAndReachesActive() {
        FakeRuntime runtime = new FakeRuntime();
        FieldModeController controller = new FieldModeController(runtime);
        controller.enableFromVisibleActivity(FieldModeConfig.defaults());
        assertEquals(1, runtime.startCount);
        assertEquals(FieldModeState.STARTING, controller.getSnapshot().getState());
        org.junit.Assert.assertTrue(FieldModeState.isRunning());
        org.junit.Assert.assertFalse(FieldModeState.isVolumeTriggerActive());

        controller.reportRuntimeReady(true, true);
        assertEquals(FieldModeState.ACTIVE, controller.getSnapshot().getState());
        org.junit.Assert.assertTrue(FieldModeState.isVolumeTriggerActive());

        controller.disable();
        assertEquals(1, runtime.stopCount);
        assertEquals(FieldModeState.DISABLED, controller.getSnapshot().getState());
        org.junit.Assert.assertFalse(FieldModeState.isRunning());
    }

    @Test
    public void bootAndProcessRestoreNeverAutostartCameraRuntime() {
        FakeRuntime runtime = new FakeRuntime();
        FieldModeController controller = new FieldModeController(runtime);

        controller.restoreAfterBoot(true, FieldModeConfig.defaults());
        assertEquals(FieldModeState.AWAITING_VISIBLE_START, controller.getSnapshot().getState());
        assertEquals(0, runtime.startCount);

        controller.restoreAfterProcessRecreation(true, FieldModeConfig.defaults());
        assertEquals(FieldModeState.AWAITING_VISIBLE_START, controller.getSnapshot().getState());
        assertEquals(0, runtime.startCount);
    }

    @Test
    public void missingGpsRuntimeIsReportedAsDegraded() {
        FieldModeController controller = new FieldModeController(new FakeRuntime());
        controller.enableFromVisibleActivity(FieldModeConfig.defaults());
        controller.reportRuntimeReady(true, false);
        assertEquals(FieldModeState.DEGRADED, controller.getSnapshot().getState());
    }

    private static final class FakeRuntime implements FieldModeController.Runtime {
        int startCount;
        int stopCount;

        @Override
        public void startFromVisibleActivity(FieldModeConfig config) {
            startCount++;
        }

        @Override
        public void stop() {
            stopCount++;
        }
    }
}
