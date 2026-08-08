package ru.darkcat.camera.upload

import ru.darkcat.camera.data.UploadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadStateMachineTest {
    @Test
    fun happyPathEndsVerified() {
        var status = UploadStatus.CAPTURED
        status = UploadStateMachine.transition(status, UploadEvent.ENCRYPTED)
        status = UploadStateMachine.transition(status, UploadEvent.QUEUE)
        status = UploadStateMachine.transition(status, UploadEvent.START)
        status = UploadStateMachine.transition(status, UploadEvent.SERVER_ACCEPTED)
        status = UploadStateMachine.transition(status, UploadEvent.SERVER_VERIFIED)
        assertEquals(UploadStatus.VERIFIED, status)
    }

    @Test
    fun retryableFailureCanBeQueuedAgain() {
        var status = UploadStateMachine.transition(UploadStatus.ENCRYPTED, UploadEvent.QUEUE)
        status = UploadStateMachine.transition(status, UploadEvent.START)
        status = UploadStateMachine.transition(status, UploadEvent.RETRYABLE_FAILURE)
        status = UploadStateMachine.transition(status, UploadEvent.QUEUE)
        assertEquals(UploadStatus.QUEUED, status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun verifiedMediaCannotBeUploadedAgainByAccident() {
        UploadStateMachine.transition(UploadStatus.VERIFIED, UploadEvent.START)
    }
}
