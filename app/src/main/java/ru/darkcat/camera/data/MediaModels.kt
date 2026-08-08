package ru.darkcat.camera.data

import com.google.gson.Gson

data class CaptureContext(
    val crmObjectId: String? = null,
    val inspectionId: String? = null,
    val taskId: String? = null,
    val userId: String? = null,
    val customTags: List<String> = emptyList(),
)

data class CaptureMetadata(
    val originalCaptureTimestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val orientationDegrees: Int = 0,
    val tags: List<String> = emptyList(),
    val comment: String = "",
    val context: CaptureContext = CaptureContext(),
    val stampEnabled: Boolean = false,
    val stampText: String = "",
)

object CaptureMetadataCodec {
    private val gson = Gson()

    fun encode(metadata: CaptureMetadata): String = gson.toJson(metadata)

    fun decode(json: String?): CaptureMetadata = if (json.isNullOrBlank()) {
        CaptureMetadata(System.currentTimeMillis())
    } else {
        runCatching { gson.fromJson(json, CaptureMetadata::class.java) }
            .getOrElse { CaptureMetadata(System.currentTimeMillis()) }
    }
}

enum class UploadStatus {
    CAPTURED,
    ENCRYPTED,
    QUEUED,
    UPLOADING,
    UPLOADED,
    VERIFIED,
    LOCAL_DELETE_PENDING,
    LOCAL_DELETED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
}

data class MediaRecord(
    val id: String,
    val sequenceNumber: Int,
    val mimeType: String,
    val internalFileName: String,
    val thumbnailFileName: String?,
    val metadata: CaptureMetadata,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val encryptedFileSize: Long,
    val checksumSha256: String,
    val uploadStatus: UploadStatus = UploadStatus.ENCRYPTED,
    val uploadId: String? = null,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
