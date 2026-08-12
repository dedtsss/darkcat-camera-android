package ru.darkcat.camera.upload;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.vault.VaultRepository;

/** Executes encrypted-media upload without ever gating capture. */
public final class UploadWorker extends Worker {
    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        SyncDiagnostics.recordStart(getApplicationContext());
        String id = getInputData().getString("media_id");
        if (id == null) return Result.failure();
        DarkCatDatabase database = DarkCatDatabase.get(getApplicationContext());
        MediaRecord record = database.get(id);
        if (record == null) return Result.failure();

        if (record.status == MediaRecord.UploadStatus.LOCAL_DELETE_PENDING) {
            return resumeLocalDeletion(database, record);
        }
        if (record.status == MediaRecord.UploadStatus.VERIFIED
                || record.status == MediaRecord.UploadStatus.LOCAL_DELETED) { SyncDiagnostics.recordSuccess(getApplicationContext()); return Result.success(); }
        if (!UploadStateMachine.canStartUpload(record.status)) return Result.failure();

        int attempt = record.retryCount + 1;
        try {
            record = database.transitionStatus(id, MediaRecord.UploadStatus.UPLOADING, null, attempt);
            File file = new VaultRepository(getApplicationContext()).vaultFile(record.vaultFileName);
            if (!file.isFile() || file.length() != record.encryptedSize) {
                return recordFailure(database, id, attempt, "Encrypted vault file is missing or has changed");
            }
            UploadProvider.UploadResult upload = UploadProviders.forContext(getApplicationContext())
                    .upload(getApplicationContext(), record, file);
            if (!upload.accepted) return recordFailure(database, id, attempt, upload.message);

            record = database.transitionStatus(id, MediaRecord.UploadStatus.UPLOADED, upload.message, attempt);
            DarkCatSettings.set(getApplicationContext(), "darkcat_last_sync_success",
                    System.currentTimeMillis());
            if (!upload.verified) { SyncDiagnostics.recordSuccess(getApplicationContext()); return Result.success(); }

            record = database.transitionStatus(id, MediaRecord.UploadStatus.VERIFIED, upload.message, attempt);
            if (!UploadStateMachine.shouldDeleteLocal(record.status,
                    DarkCatSettings.deleteAfterVerified(getApplicationContext()))) { SyncDiagnostics.recordSuccess(getApplicationContext()); return Result.success(); }
            record = database.transitionStatus(id, MediaRecord.UploadStatus.LOCAL_DELETE_PENDING, null, attempt);
            return resumeLocalDeletion(database, record);
        } catch (Exception error) {
            SyncDiagnostics.recordFailure(getApplicationContext(), attempt, safeError(error));
            MediaRecord latest = database.get(id);
            if (latest != null && latest.status == MediaRecord.UploadStatus.LOCAL_DELETE_PENDING) {
                try { database.transitionStatus(id, latest.status, safeError(error), latest.retryCount); }
                catch (Exception ignored) { }
                return Result.retry();
            }
            if (latest != null && (latest.status == MediaRecord.UploadStatus.UPLOADING
                    || latest.status == MediaRecord.UploadStatus.QUEUED
                    || latest.status == MediaRecord.UploadStatus.FAILED_RETRYABLE)) {
                return recordFailure(database, id, attempt, safeError(error));
            }
            // UPLOADED is intentionally retained if only verification/state persistence failed.
            return Result.retry();
        }
    }

    private Result resumeLocalDeletion(DarkCatDatabase database, MediaRecord record) {
        try {
            new VaultRepository(getApplicationContext()).deleteFilesChecked(record);
            database.transitionStatus(record.id, MediaRecord.UploadStatus.LOCAL_DELETED, null, record.retryCount);
            return Result.success();
        } catch (Exception error) {
            try { database.transitionStatus(record.id, MediaRecord.UploadStatus.LOCAL_DELETE_PENDING,
                    safeError(error), record.retryCount); }
            catch (Exception ignored) { }
            return Result.retry();
        }
    }

    private Result recordFailure(DarkCatDatabase database, String id, int attempt, String message) {
        SyncDiagnostics.recordFailure(getApplicationContext(), attempt, message);
        MediaRecord.UploadStatus failure = UploadStateMachine.failureStatus(attempt);
        try { database.transitionStatus(id, failure, message, attempt); }
        catch (Exception ignored) { return Result.retry(); }
        return failure == MediaRecord.UploadStatus.FAILED_PERMANENT ? Result.failure() : Result.retry();
    }

    private static String safeError(Exception error) {
        return error == null ? "Unknown upload error" : error.getClass().getSimpleName();
    }
}
