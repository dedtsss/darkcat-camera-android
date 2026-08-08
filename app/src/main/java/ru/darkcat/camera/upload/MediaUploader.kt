package ru.darkcat.camera.upload

import android.content.Context
import ru.darkcat.camera.crypto.Hashing
import ru.darkcat.camera.data.MediaRecord
import java.io.File

data class UploadRequest(
    val mediaId: String,
    val idempotencyKey: String,
    val encryptedFile: File,
    val checksumSha256: String,
    val mimeType: String,
)

data class UploadReceipt(val uploadId: String, val checksumSha256: String)

class RetryableUploadException(message: String, cause: Throwable? = null) : Exception(message, cause)
class PermanentUploadException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface MediaUploader {
    suspend fun upload(request: UploadRequest): UploadReceipt
}

/** Deterministic local uploader used until the DarkCat API contract is selected. */
class FakeMediaUploader : MediaUploader {
    override suspend fun upload(request: UploadRequest): UploadReceipt {
        if (!request.encryptedFile.exists()) throw PermanentUploadException("Encrypted media is missing")
        val checksum = runCatching { Hashing.sha256(request.encryptedFile) }
            .getOrElse { throw RetryableUploadException("Unable to read encrypted media", it) }
        return UploadReceipt("fake-${request.idempotencyKey}", checksum)
    }
}

object MediaUploaderRegistry {
    @Volatile
    var factory: (Context) -> MediaUploader = { FakeMediaUploader() }
}
