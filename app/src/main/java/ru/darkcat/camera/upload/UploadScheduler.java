package ru.darkcat.camera.upload;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;

import java.util.concurrent.TimeUnit;

public final class UploadScheduler {
    public static void enqueueAllPending(Context context) {
        if (DarkCatSettings.PROVIDER_OFF.equals(DarkCatSettings.provider(context))) return;
        for (MediaRecord record : DarkCatDatabase.get(context).list()) {
            if (UploadStateMachine.canEnqueue(record.status)) enqueue(context, record.id);
        }
    }

    public static void enqueue(Context context, String mediaId) {
        if (DarkCatSettings.PROVIDER_OFF.equals(DarkCatSettings.provider(context))) return;
        DarkCatDatabase database = DarkCatDatabase.get(context);
        MediaRecord record = database.get(mediaId);
        if (record == null || !UploadStateMachine.canEnqueue(record.status)) return;
        int retryCount = record.status == MediaRecord.UploadStatus.FAILED_PERMANENT ? 0 : record.retryCount;
        database.transitionStatus(mediaId, MediaRecord.UploadStatus.QUEUED, null, retryCount);
        Constraints.Builder constraints = new Constraints.Builder().setRequiredNetworkType(DarkCatSettings.wifiOnly(context) ? NetworkType.UNMETERED : NetworkType.CONNECTED);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UploadWorker.class).setInputData(new Data.Builder().putString("media_id", mediaId).build())
                .setConstraints(constraints.build()).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork("darkcat-upload-" + mediaId, ExistingWorkPolicy.REPLACE, request);
    }
    private UploadScheduler() { }
}
