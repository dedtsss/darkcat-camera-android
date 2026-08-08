package ru.darkcat.camera.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MediaDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE media (
                id TEXT PRIMARY KEY NOT NULL,
                sequence_number INTEGER NOT NULL UNIQUE,
                mime_type TEXT NOT NULL,
                internal_file_name TEXT NOT NULL UNIQUE,
                thumbnail_file_name TEXT,
                metadata_json TEXT NOT NULL,
                original_capture_timestamp INTEGER NOT NULL,
                latitude REAL,
                longitude REAL,
                accuracy_meters REAL,
                altitude_meters REAL,
                orientation_degrees INTEGER NOT NULL,
                tags TEXT NOT NULL,
                comment TEXT NOT NULL,
                crm_object_id TEXT,
                inspection_id TEXT,
                task_id TEXT,
                user_id TEXT,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                encrypted_file_size INTEGER NOT NULL,
                checksum_sha256 TEXT NOT NULL,
                upload_status TEXT NOT NULL,
                upload_id TEXT,
                retry_count INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX media_upload_status_idx ON media(upload_status)")
        db.execSQL("CREATE INDEX media_capture_time_idx ON media(original_capture_timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE media ADD COLUMN last_error TEXT")
        }
    }

    @Synchronized
    fun nextSequence(): Int {
        readableDatabase.rawQuery("SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM media", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 1
        }
    }

    @Synchronized
    fun insert(record: MediaRecord) {
        writableDatabase.insertOrThrow(TABLE, null, record.toContentValues())
    }

    @Synchronized
    fun get(id: String): MediaRecord? = readableDatabase.query(
        TABLE,
        null,
        "id = ?",
        arrayOf(id),
        null,
        null,
        "original_capture_timestamp DESC",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toRecord() else null }

    @Synchronized
    fun list(): List<MediaRecord> = readableDatabase.query(
        TABLE,
        null,
        null,
        null,
        null,
        null,
        "original_capture_timestamp DESC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toRecord()) } }

    @Synchronized
    fun updateStatus(
        id: String,
        status: UploadStatus,
        uploadId: String? = null,
        error: String? = null,
        retryCount: Int? = null,
    ): Boolean {
        val values = ContentValues().apply {
            put("upload_status", status.name)
            put("updated_at", System.currentTimeMillis())
            uploadId?.let { put("upload_id", it) }
            if (error == null) putNull("last_error") else put("last_error", error)
            retryCount?.let { put("retry_count", it) }
        }
        return writableDatabase.update(TABLE, values, "id = ?", arrayOf(id)) == 1
    }

    @Synchronized
    fun delete(id: String): Boolean = writableDatabase.delete(TABLE, "id = ?", arrayOf(id)) == 1

    private fun MediaRecord.toContentValues() = ContentValues().apply {
        put("id", id)
        put("sequence_number", sequenceNumber)
        put("mime_type", mimeType)
        put("internal_file_name", internalFileName)
        thumbnailFileName?.let { put("thumbnail_file_name", it) } ?: putNull("thumbnail_file_name")
        put("metadata_json", CaptureMetadataCodec.encode(metadata))
        put("original_capture_timestamp", metadata.originalCaptureTimestamp)
        metadata.latitude?.let { put("latitude", it) } ?: putNull("latitude")
        metadata.longitude?.let { put("longitude", it) } ?: putNull("longitude")
        metadata.accuracyMeters?.let { put("accuracy_meters", it) } ?: putNull("accuracy_meters")
        metadata.altitudeMeters?.let { put("altitude_meters", it) } ?: putNull("altitude_meters")
        put("orientation_degrees", metadata.orientationDegrees)
        put("tags", metadata.tags.joinToString(","))
        put("comment", metadata.comment)
        metadata.context.crmObjectId?.let { put("crm_object_id", it) } ?: putNull("crm_object_id")
        metadata.context.inspectionId?.let { put("inspection_id", it) } ?: putNull("inspection_id")
        metadata.context.taskId?.let { put("task_id", it) } ?: putNull("task_id")
        metadata.context.userId?.let { put("user_id", it) } ?: putNull("user_id")
        put("width", width)
        put("height", height)
        put("duration_ms", durationMs)
        put("encrypted_file_size", encryptedFileSize)
        put("checksum_sha256", checksumSha256)
        put("upload_status", uploadStatus.name)
        uploadId?.let { put("upload_id", it) } ?: putNull("upload_id")
        put("retry_count", retryCount)
        lastError?.let { put("last_error", it) } ?: putNull("last_error")
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun android.database.Cursor.toRecord(): MediaRecord {
        val index = { name: String -> getColumnIndexOrThrow(name) }
        val metadata = CaptureMetadataCodec.decode(getString(index("metadata_json")))
        return MediaRecord(
            id = getString(index("id")),
            sequenceNumber = getInt(index("sequence_number")),
            mimeType = getString(index("mime_type")),
            internalFileName = getString(index("internal_file_name")),
            thumbnailFileName = getString(index("thumbnail_file_name")),
            metadata = metadata,
            width = getInt(index("width")),
            height = getInt(index("height")),
            durationMs = getLong(index("duration_ms")),
            encryptedFileSize = getLong(index("encrypted_file_size")),
            checksumSha256 = getString(index("checksum_sha256")),
            uploadStatus = UploadStatus.valueOf(getString(index("upload_status"))),
            uploadId = getString(index("upload_id")),
            retryCount = getInt(index("retry_count")),
            lastError = getString(index("last_error")),
            createdAt = getLong(index("created_at")),
            updatedAt = getLong(index("updated_at")),
        )
    }

    companion object {
        private const val DATABASE_NAME = "darkcat_media.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE = "media"

        @Volatile private var instance: MediaDatabase? = null

        fun getInstance(context: Context): MediaDatabase = instance ?: synchronized(this) {
            instance ?: MediaDatabase(context).also { instance = it }
        }
    }
}
