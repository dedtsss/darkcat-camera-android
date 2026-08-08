package ru.darkcat.camera.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import ru.darkcat.camera.crypto.FileCrypto
import ru.darkcat.camera.ui.ImageStamper
import ru.darkcat.camera.upload.UploadScheduler
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val database: MediaDatabase,
) {
    private val crypto = FileCrypto(context)
    private val vaultDirectory = File(context.filesDir, "vault").apply { mkdirs() }
    private val thumbnailDirectory = File(context.filesDir, "vault-thumbnails").apply { mkdirs() }

    init {
        TempFiles.cleanupStale(context)
    }

    @Synchronized
    fun storeImage(source: File, metadata: CaptureMetadata): MediaRecord =
        store(source, "image/jpeg", metadata, durationMs = 0L)

    @Synchronized
    fun storeVideo(source: File, metadata: CaptureMetadata, durationMs: Long): MediaRecord =
        store(source, "video/mp4", metadata, durationMs)

    fun list(): List<MediaRecord> = database.list()

    fun get(id: String): MediaRecord? = database.get(id)

    fun encryptedFile(record: MediaRecord): File = File(vaultDirectory, record.internalFileName)

    fun thumbnail(record: MediaRecord): ByteArray? = record.thumbnailFileName?.let {
        val file = File(thumbnailDirectory, it)
        if (file.exists()) runCatching { crypto.decryptBytes(file) }.getOrNull() else null
    }

    fun decryptToBytes(record: MediaRecord): ByteArray = crypto.decryptBytes(encryptedFile(record))

    fun createEditingTemp(record: MediaRecord): File = TempFiles.create(context, "edit", ".${extension(record.mimeType)}").also {
        it.writeBytes(decryptToBytes(record))
    }

    fun queueUpload(id: String) {
        val current = database.get(id) ?: return
        if (current.uploadStatus == UploadStatus.VERIFIED || current.uploadStatus == UploadStatus.LOCAL_DELETED) return
        database.updateStatus(id, UploadStatus.QUEUED, error = null)
        UploadScheduler(context).schedule(id)
    }

    fun retryUpload(id: String) = queueUpload(id)

    fun delete(id: String): Boolean {
        val record = database.get(id) ?: return false
        val mediaDeleted = encryptedFile(record).delete() || !encryptedFile(record).exists()
        val thumbDeleted = record.thumbnailFileName?.let { File(thumbnailDirectory, it).delete() || !File(thumbnailDirectory, it).exists() } ?: true
        return mediaDeleted && thumbDeleted && database.delete(id)
    }

    fun updateUploadStatus(id: String, status: UploadStatus, uploadId: String? = null, error: String? = null, retryCount: Int? = null) =
        database.updateStatus(id, status, uploadId, error, retryCount)

    private fun store(source: File, mimeType: String, metadata: CaptureMetadata, durationMs: Long): MediaRecord {
        require(source.exists() && source.length() > 0) { "Capture source is missing or empty" }
        val id = UUID.randomUUID().toString()
        val sequence = database.nextSequence()
        val internalFileName = "$id.dcv"
        val thumbnailFileName = "$id.thumb.dcv"
        val encrypted = File(vaultDirectory, internalFileName)
        val partial = File(vaultDirectory, "$internalFileName.partial")
        val thumbnail = File(thumbnailDirectory, thumbnailFileName)
        val thumbnailPartial = File(thumbnailDirectory, "$thumbnailFileName.partial")
        var stampedSource: File? = null
        try {
            val sourceForStorage = if (metadata.stampEnabled && mimeType == "image/jpeg") {
                TempFiles.create(context, "stamp", ".jpg").also {
                    ImageStamper.stamp(source, it, metadata, sequence)
                    stampedSource = it
                }
            } else source

            val encryption = crypto.encryptFile(sourceForStorage, partial)
            check(partial.renameTo(encrypted)) { "Unable to commit encrypted media" }

            createThumbnail(sourceForStorage, mimeType)?.let { bytes ->
                crypto.encryptBytes(bytes, thumbnailPartial)
                check(thumbnailPartial.renameTo(thumbnail)) { "Unable to commit encrypted thumbnail" }
            }

            val dimensions = imageDimensions(sourceForStorage, mimeType)
            val record = MediaRecord(
                id = id,
                sequenceNumber = sequence,
                mimeType = mimeType,
                internalFileName = internalFileName,
                thumbnailFileName = if (thumbnail.exists()) thumbnailFileName else null,
                metadata = metadata,
                width = dimensions.first,
                height = dimensions.second,
                durationMs = durationMs,
                encryptedFileSize = encryption.ciphertextSize,
                checksumSha256 = encryption.sha256,
            )
            database.insert(record)
            return record
        } catch (error: Throwable) {
            partial.delete()
            encrypted.delete()
            thumbnailPartial.delete()
            thumbnail.delete()
            throw error
        } finally {
            stampedSource?.delete()
        }
    }

    private fun createThumbnail(source: File, mimeType: String): ByteArray? {
        val bitmap = if (mimeType.startsWith("image/")) {
            BitmapFactory.decodeFile(source.absolutePath)
        } else {
            val retriever = MediaMetadataRetriever()
            val frame = runCatching {
                retriever.setDataSource(source.absolutePath)
                retriever.getFrameAtTime(0)
            }.getOrNull()
            runCatching { retriever.release() }
            frame
        } ?: return null
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 72, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun imageDimensions(source: File, mimeType: String): Pair<Int, Int> {
        if (mimeType.startsWith("image/")) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, options)
            return options.outWidth to options.outHeight
        }
        return 0 to 0
    }

    private fun extension(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> "jpg"
        "video/mp4" -> "mp4"
        else -> "bin"
    }
}
