package ru.darkcat.camera.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NightExtensionLifecycleTest {
    @Test public void staleSessionCannotBecomeReadyOrCompleteCapture() {
        NightExtensionLifecycle lifecycle = new NightExtensionLifecycle();
        NightExtensionLifecycle.SessionHandle oldSession = lifecycle.requestSession(1, true);
        Object oldCameraSession = new Object();
        assertTrue(lifecycle.configured(oldSession, oldCameraSession));
        NightExtensionLifecycle.CaptureHandle oldCapture = lifecycle.requestCapture(oldSession, oldCameraSession, new Object());
        assertTrue(lifecycle.bindSequence(oldCapture, 7));

        NightExtensionLifecycle.SessionHandle newSession = lifecycle.requestSession(1, true);
        Object newCameraSession = new Object();
        assertTrue(lifecycle.configured(newSession, newCameraSession));
        assertFalse(lifecycle.completed(oldCameraSession, 7));
        assertFalse(lifecycle.closed(oldSession, oldCameraSession));
        assertTrue(lifecycle.state() == NightExtensionLifecycle.SessionState.READY);
    }

    @Test public void progressAndSequenceMustMatchRequestAndSession() {
        NightExtensionLifecycle lifecycle = new NightExtensionLifecycle();
        NightExtensionLifecycle.SessionHandle session = lifecycle.requestSession(1, true);
        Object cameraSession = new Object();
        Object request = new Object();
        assertTrue(lifecycle.configured(session, cameraSession));
        NightExtensionLifecycle.CaptureHandle capture = lifecycle.requestCapture(session, cameraSession, request);
        assertFalse(lifecycle.acceptsProgress(cameraSession, new Object()));
        assertTrue(lifecycle.captureStarted(cameraSession, request));
        assertTrue(lifecycle.acceptsProgress(cameraSession, request));
        assertTrue(lifecycle.bindSequence(capture, 12));
        assertFalse(lifecycle.completed(cameraSession, 11));
        assertTrue(lifecycle.completed(cameraSession, 12));
        assertFalse(lifecycle.acceptsProgress(cameraSession, request));
    }

    @Test public void failureInvalidatesPendingCapture() {
        NightExtensionLifecycle lifecycle = new NightExtensionLifecycle();
        NightExtensionLifecycle.SessionHandle session = lifecycle.requestSession(1, true);
        Object cameraSession = new Object();
        assertTrue(lifecycle.configured(session, cameraSession));
        NightExtensionLifecycle.CaptureHandle capture = lifecycle.requestCapture(session, cameraSession, new Object());
        assertTrue(lifecycle.failed(session));
        assertFalse(lifecycle.bindSequence(capture, 3));
        assertFalse(lifecycle.hasCapture());
    }
}
