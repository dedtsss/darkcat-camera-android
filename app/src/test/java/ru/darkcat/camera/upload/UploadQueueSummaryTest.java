package ru.darkcat.camera.upload;

import org.junit.Test;

import java.util.Arrays;

import ru.darkcat.camera.data.MediaRecord;

import static org.junit.Assert.assertEquals;

public final class UploadQueueSummaryTest {
    @Test public void countsQueueAndErrorsWithoutCallingStoredOnlyMediaPending() {
        UploadQueueSummary summary = UploadQueueSummary.fromRecords(Arrays.asList(
                record("encrypted", MediaRecord.UploadStatus.ENCRYPTED),
                record("queued", MediaRecord.UploadStatus.QUEUED),
                record("uploading", MediaRecord.UploadStatus.UPLOADING),
                record("uploaded", MediaRecord.UploadStatus.UPLOADED),
                record("verified", MediaRecord.UploadStatus.VERIFIED),
                record("retry", MediaRecord.UploadStatus.FAILED_RETRYABLE),
                record("permanent", MediaRecord.UploadStatus.FAILED_PERMANENT),
                record("deleted", MediaRecord.UploadStatus.LOCAL_DELETED)));

        assertEquals(7, summary.inVault);
        assertEquals(1, summary.waiting);
        assertEquals(1, summary.uploading);
        assertEquals(1, summary.uploaded);
        assertEquals(2, summary.verified);
        assertEquals(2, summary.errors);
        assertEquals(3, summary.pending);
    }

    private static MediaRecord record(String id, MediaRecord.UploadStatus status) {
        return new MediaRecord(id, 1, "image/jpeg", id + ".dcv", null, id + ".jpg",
                1, 1, "sha", "{}", status, 0, null);
    }
}
