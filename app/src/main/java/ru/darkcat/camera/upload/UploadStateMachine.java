package ru.darkcat.camera.upload;

import java.util.EnumSet;

import ru.darkcat.camera.data.MediaRecord;

/** Pure policy for every persisted upload-state transition. */
public final class UploadStateMachine {
    public static final int MAX_RETRY_ATTEMPTS = 8;

    public static boolean canTransition(MediaRecord.UploadStatus from, MediaRecord.UploadStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true; // Idempotent recovery after process/worker recreation.
        switch (from) {
            case CAPTURED:
                return to == MediaRecord.UploadStatus.RECOVERY_PENDING;
            case RECOVERY_PENDING:
                return to == MediaRecord.UploadStatus.ENCRYPTED;
            case ENCRYPTED:
                return to == MediaRecord.UploadStatus.QUEUED;
            case QUEUED:
                return to == MediaRecord.UploadStatus.UPLOADING || to == MediaRecord.UploadStatus.FAILED_PERMANENT;
            case UPLOADING:
                return EnumSet.of(MediaRecord.UploadStatus.UPLOADED,
                        MediaRecord.UploadStatus.FAILED_RETRYABLE,
                        MediaRecord.UploadStatus.FAILED_PERMANENT).contains(to);
            case UPLOADED:
                // Requeue/re-upload is a safe fallback when verification could not be completed.
                return to == MediaRecord.UploadStatus.VERIFIED || to == MediaRecord.UploadStatus.QUEUED
                        || to == MediaRecord.UploadStatus.UPLOADING;
            case VERIFIED:
                return to == MediaRecord.UploadStatus.LOCAL_DELETE_PENDING;
            case FAILED_RETRYABLE:
                return EnumSet.of(MediaRecord.UploadStatus.QUEUED,
                        MediaRecord.UploadStatus.UPLOADING,
                        MediaRecord.UploadStatus.FAILED_PERMANENT).contains(to);
            case FAILED_PERMANENT:
                return to == MediaRecord.UploadStatus.QUEUED;
            case LOCAL_DELETE_PENDING:
                return to == MediaRecord.UploadStatus.LOCAL_DELETED;
            case LOCAL_DELETED:
            default:
                return false;
        }
    }

    public static void requireTransition(MediaRecord.UploadStatus from, MediaRecord.UploadStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid media state transition: " + from + " -> " + to);
        }
    }

    public static boolean canEnqueue(MediaRecord.UploadStatus status) {
        return status == MediaRecord.UploadStatus.ENCRYPTED
                || status == MediaRecord.UploadStatus.QUEUED
                || status == MediaRecord.UploadStatus.UPLOADED
                || status == MediaRecord.UploadStatus.FAILED_RETRYABLE
                || status == MediaRecord.UploadStatus.FAILED_PERMANENT;
    }

    public static boolean canStartUpload(MediaRecord.UploadStatus status) {
        return status == MediaRecord.UploadStatus.QUEUED
                || status == MediaRecord.UploadStatus.UPLOADING
                || status == MediaRecord.UploadStatus.UPLOADED
                || status == MediaRecord.UploadStatus.FAILED_RETRYABLE;
    }

    public static MediaRecord.UploadStatus failureStatus(int attempt) {
        return attempt >= MAX_RETRY_ATTEMPTS
                ? MediaRecord.UploadStatus.FAILED_PERMANENT
                : MediaRecord.UploadStatus.FAILED_RETRYABLE;
    }

    /** KEEP LOCAL is the default: deletion is possible only after VERIFIED and explicit opt-in. */
    public static boolean shouldDeleteLocal(MediaRecord.UploadStatus status, boolean deleteAfterVerified) {
        return deleteAfterVerified && status == MediaRecord.UploadStatus.VERIFIED;
    }

    public static boolean isPending(MediaRecord.UploadStatus status) {
        return status == MediaRecord.UploadStatus.QUEUED
                || status == MediaRecord.UploadStatus.UPLOADING
                || status == MediaRecord.UploadStatus.FAILED_RETRYABLE;
    }

    private UploadStateMachine() { }
}
