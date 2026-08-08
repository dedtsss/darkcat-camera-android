package ru.darkcat.camera.upload;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.vault.VaultRepository;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class UploadWorker extends Worker {
    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    @NonNull @Override public Result doWork() {
        String id = getInputData().getString("media_id"); if (id == null) return Result.failure();
        DarkCatDatabase db = DarkCatDatabase.get(getApplicationContext()); MediaRecord record = db.get(id); if (record == null) return Result.failure();
        int retry = record.retryCount + 1; db.updateStatus(id, MediaRecord.UploadStatus.UPLOADING, null, retry);
        try {
            File file = new VaultRepository(getApplicationContext()).vaultFile(record.vaultFileName);
            UploadProvider.UploadResult upload = UploadProviders.forContext(getApplicationContext()).upload(getApplicationContext(), record, file);
            if (!upload.accepted) { db.updateStatus(id, MediaRecord.UploadStatus.FAILED_RETRYABLE, upload.message, retry); return Result.retry(); }
            if (upload.verified) {
                db.updateStatus(id, MediaRecord.UploadStatus.VERIFIED, upload.message, retry);
                if (ru.darkcat.camera.data.DarkCatSettings.deleteAfterVerified(getApplicationContext())) {
                    db.updateStatus(id, MediaRecord.UploadStatus.LOCAL_DELETE_PENDING, null, retry);
                    new VaultRepository(getApplicationContext()).deleteFiles(record);
                    db.updateStatus(id, MediaRecord.UploadStatus.LOCAL_DELETED, null, retry);
                }
            } else db.updateStatus(id, MediaRecord.UploadStatus.UPLOADED, upload.message, retry);
            return Result.success();
        } catch (Exception error) {
            db.updateStatus(id, MediaRecord.UploadStatus.FAILED_RETRYABLE, error.getClass().getSimpleName(), retry); return Result.retry();
        }
    }
}
