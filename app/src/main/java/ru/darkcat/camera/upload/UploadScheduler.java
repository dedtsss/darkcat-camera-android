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
    public static void enqueue(Context context, String mediaId) {
        DarkCatDatabase.get(context).updateStatus(mediaId, MediaRecord.UploadStatus.QUEUED, null, DarkCatDatabase.get(context).get(mediaId) == null ? 0 : DarkCatDatabase.get(context).get(mediaId).retryCount);
        Constraints.Builder constraints = new Constraints.Builder().setRequiredNetworkType(DarkCatSettings.wifiOnly(context) ? NetworkType.UNMETERED : NetworkType.CONNECTED);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UploadWorker.class).setInputData(new Data.Builder().putString("media_id", mediaId).build())
                .setConstraints(constraints.build()).setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork("darkcat-upload-" + mediaId, ExistingWorkPolicy.REPLACE, request);
    }
    private UploadScheduler() { }
}
