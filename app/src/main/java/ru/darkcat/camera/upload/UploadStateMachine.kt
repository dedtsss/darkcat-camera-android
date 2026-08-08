package ru.darkcat.camera.upload

import ru.darkcat.camera.data.UploadStatus

enum class UploadEvent {
    ENCRYPTED,
    QUEUE,
    START,
    SERVER_ACCEPTED,
    SERVER_VERIFIED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    DELETE_LOCAL,
}

object UploadStateMachine {
    fun transition(state: UploadStatus, event: UploadEvent): UploadStatus = when (event) {
        UploadEvent.ENCRYPTED -> requireState(state, UploadStatus.CAPTURED, UploadStatus.ENCRYPTED)
            .let { UploadStatus.ENCRYPTED }
        UploadEvent.QUEUE -> requireState(state, UploadStatus.ENCRYPTED, UploadStatus.FAILED_RETRYABLE, UploadStatus.FAILED_PERMANENT)
            .let { UploadStatus.QUEUED }
        UploadEvent.START -> requireState(state, UploadStatus.QUEUED, UploadStatus.FAILED_RETRYABLE)
            .let { UploadStatus.UPLOADING }
        UploadEvent.SERVER_ACCEPTED -> requireState(state, UploadStatus.UPLOADING)
            .let { UploadStatus.UPLOADED }
        UploadEvent.SERVER_VERIFIED -> requireState(state, UploadStatus.UPLOADED)
            .let { UploadStatus.VERIFIED }
        UploadEvent.RETRYABLE_FAILURE -> requireState(state, UploadStatus.UPLOADING, UploadStatus.QUEUED)
            .let { UploadStatus.FAILED_RETRYABLE }
        UploadEvent.PERMANENT_FAILURE -> requireState(state, UploadStatus.UPLOADING, UploadStatus.QUEUED)
            .let { UploadStatus.FAILED_PERMANENT }
        UploadEvent.DELETE_LOCAL -> requireState(state, UploadStatus.VERIFIED)
            .let { UploadStatus.LOCAL_DELETE_PENDING }
    }

    private fun requireState(state: UploadStatus, vararg allowed: UploadStatus): UploadStatus {
        require(state in allowed) { "Invalid upload transition from $state" }
        return state
    }
}
