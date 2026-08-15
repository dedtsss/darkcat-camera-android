package ru.darkcat.camera.upload;

import org.junit.Test;

import ru.darkcat.camera.data.MediaRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class UploadStateMachineTest {
    @Test public void acceptsValidatedRuntimePath() {
        assertTrue(UploadStateMachine.canTransition(MediaRecord.UploadStatus.ENCRYPTED, MediaRecord.UploadStatus.QUEUED));
        assertTrue(UploadStateMachine.canTransition(MediaRecord.UploadStatus.QUEUED, MediaRecord.UploadStatus.UPLOADING));
        assertTrue(UploadStateMachine.canTransition(MediaRecord.UploadStatus.UPLOADING, MediaRecord.UploadStatus.UPLOADED));
        assertTrue(UploadStateMachine.canTransition(MediaRecord.UploadStatus.UPLOADED, MediaRecord.UploadStatus.VERIFIED));
        assertTrue(UploadStateMachine.canTransition(MediaRecord.UploadStatus.VERIFIED, MediaRecord.UploadStatus.LOCAL_DELETE_PENDING));
        assertTrue(UploadStateMachine.canTransition(MediaRecord.UploadStatus.LOCAL_DELETE_PENDING, MediaRecord.UploadStatus.LOCAL_DELETED));
    }

    @Test public void rejectsSkippingUploadAndVerification() {
        assertFalse(UploadStateMachine.canTransition(MediaRecord.UploadStatus.ENCRYPTED, MediaRecord.UploadStatus.VERIFIED));
        assertFalse(UploadStateMachine.canTransition(MediaRecord.UploadStatus.UPLOADING, MediaRecord.UploadStatus.VERIFIED));
        assertFalse(UploadStateMachine.canTransition(MediaRecord.UploadStatus.UPLOADED, MediaRecord.UploadStatus.LOCAL_DELETE_PENDING));
        assertThrows(IllegalStateException.class, () -> UploadStateMachine.requireTransition(
                MediaRecord.UploadStatus.QUEUED, MediaRecord.UploadStatus.LOCAL_DELETED));
    }

    @Test public void keepLocalIsDefaultAndDeleteRequiresVerifiedOptIn() {
        assertFalse(UploadStateMachine.shouldDeleteLocal(MediaRecord.UploadStatus.VERIFIED, false));
        assertFalse(UploadStateMachine.shouldDeleteLocal(MediaRecord.UploadStatus.UPLOADED, true));
        assertTrue(UploadStateMachine.shouldDeleteLocal(MediaRecord.UploadStatus.VERIFIED, true));
    }

    @Test public void retryLimitBecomesPermanent() {
        assertEquals(MediaRecord.UploadStatus.FAILED_RETRYABLE, UploadStateMachine.failureStatus(1));
        assertEquals(MediaRecord.UploadStatus.FAILED_PERMANENT,
                UploadStateMachine.failureStatus(UploadStateMachine.MAX_RETRY_ATTEMPTS));
    }
}
