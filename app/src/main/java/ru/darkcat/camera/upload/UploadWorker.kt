package ru.darkcat.camera.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.darkcat.camera.DarkCatApplication
import ru.darkcat.camera.data.MediaRecord
import ru.darkcat.camera.data.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mediaId = inputData.getString(MEDIA_ID_KEY) ?: return@withContext Result.failure()
        val repository = (applicationContext as DarkCatApplication).vaultRepository
        val record = repository.get(mediaId) ?: return@withContext Result.failure()
        if (record.uploadStatus == UploadStatus.VERIFIED || record.uploadStatus == UploadStatus.LOCAL_DELETED) {
            return@withContext Result.success()
        }
        val currentRetryCount = record.retryCount + 1
        repository.updateUploadStatus(mediaId, UploadStatus.UPLOADING, retryCount = currentRetryCount)
        try {
            val receipt = MediaUploaderRegistry.factory(applicationContext).upload(
                UploadRequest(
                    mediaId = mediaId,
                    idempotencyKey = mediaId,
                    encryptedFile = repository.encryptedFile(record),
                    checksumSha256 = record.checksumSha256,
                    mimeType = record.mimeType,
                ),
            )
            if (receipt.checksumSha256 != record.checksumSha256) {
                throw RetryableUploadException("Server checksum confirmation did not match local checksum")
            }
            repository.updateUploadStatus(mediaId, UploadStatus.UPLOADED, uploadId = receipt.uploadId)
            repository.updateUploadStatus(mediaId, UploadStatus.VERIFIED, uploadId = receipt.uploadId)
            Result.success()
        } catch (error: RetryableUploadException) {
            repository.updateUploadStatus(mediaId, UploadStatus.FAILED_RETRYABLE, error = error.message, retryCount = currentRetryCount)
            Result.retry()
        } catch (error: PermanentUploadException) {
            repository.updateUploadStatus(mediaId, UploadStatus.FAILED_PERMANENT, error = error.message, retryCount = currentRetryCount)
            Result.failure()
        } catch (error: Throwable) {
            repository.updateUploadStatus(mediaId, UploadStatus.FAILED_RETRYABLE, error = error.message, retryCount = currentRetryCount)
            Result.retry()
        }
    }

    companion object {
        const val MEDIA_ID_KEY = "media_id"
    }
}
