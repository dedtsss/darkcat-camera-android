package ru.darkcat.camera.upload;

import java.util.Collection;

import ru.darkcat.camera.data.MediaRecord;

/** Stable queue counters shared by the main indicator and the sync screen. */
public final class UploadQueueSummary {
    public final int inVault;
    public final int waiting;
    public final int uploading;
    public final int uploaded;
    public final int verified;
    public final int errors;
    public final int pending;

    private UploadQueueSummary(int inVault, int waiting, int uploading, int uploaded, int verified,
                               int errors, int pending) {
        this.inVault = inVault;
        this.waiting = waiting;
        this.uploading = uploading;
        this.uploaded = uploaded;
        this.verified = verified;
        this.errors = errors;
        this.pending = pending;
    }

    public static UploadQueueSummary fromCounts(int inVault, int waiting, int uploading,
                                                int uploaded, int verified, int errors, int pending) {
        return new UploadQueueSummary(nonNegative(inVault), nonNegative(waiting),
                nonNegative(uploading), nonNegative(uploaded), nonNegative(verified),
                nonNegative(errors), nonNegative(pending));
    }

    public static UploadQueueSummary fromRecords(Collection<MediaRecord> records) {
        int inVault = 0, waiting = 0, uploading = 0, uploaded = 0, verified = 0, errors = 0, pending = 0;
        if (records != null) for (MediaRecord record : records) {
            if (record == null || record.status == null) continue;
            MediaRecord.UploadStatus status = record.status;
            if (status != MediaRecord.UploadStatus.LOCAL_DELETED) inVault++;
            if (status == MediaRecord.UploadStatus.QUEUED) waiting++;
            if (status == MediaRecord.UploadStatus.UPLOADING) uploading++;
            if (status == MediaRecord.UploadStatus.UPLOADED) uploaded++;
            if (status == MediaRecord.UploadStatus.VERIFIED
                    || status == MediaRecord.UploadStatus.LOCAL_DELETE_PENDING
                    || status == MediaRecord.UploadStatus.LOCAL_DELETED) verified++;
            if (status == MediaRecord.UploadStatus.FAILED_RETRYABLE
                    || status == MediaRecord.UploadStatus.FAILED_PERMANENT) errors++;
            if (UploadStateMachine.isPending(status)) pending++;
        }
        return new UploadQueueSummary(inVault, waiting, uploading, uploaded, verified, errors, pending);
    }

    private static int nonNegative(int value) {
        if (value < 0) throw new IllegalArgumentException("queue count must be non-negative");
        return value;
    }
}
