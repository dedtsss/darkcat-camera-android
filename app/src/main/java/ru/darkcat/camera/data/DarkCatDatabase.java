package ru.darkcat.camera.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

import ru.darkcat.camera.upload.UploadQueueSummary;
import ru.darkcat.camera.upload.UploadStateMachine;

/** Structured durable media/queue state. The encrypted file is never represented by a Set in preferences. */
public final class DarkCatDatabase extends SQLiteOpenHelper {
    private static final String NAME = "darkcat.db";
    private static final int VERSION = 2;
    private static volatile DarkCatDatabase instance;

    public static DarkCatDatabase get(Context context) {
        if (instance == null) synchronized (DarkCatDatabase.class) {
            if (instance == null) instance = new DarkCatDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private DarkCatDatabase(Context context) { super(context, NAME, null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE media (id TEXT PRIMARY KEY, seq INTEGER NOT NULL, mime TEXT NOT NULL, vault TEXT NOT NULL, thumb TEXT, display_name TEXT NOT NULL, created INTEGER NOT NULL, encrypted_size INTEGER NOT NULL, sha256 TEXT NOT NULL, metadata TEXT NOT NULL, status TEXT NOT NULL, retry_count INTEGER NOT NULL DEFAULT 0, last_error TEXT)");
        db.execSQL("CREATE INDEX media_status_idx ON media(status, created)");
        createGalleryTable(db);
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createGalleryTable(db);
    }

    public synchronized int nextSequence() {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(seq), 0) + 1 FROM media", null);
        try { return cursor.moveToFirst() ? cursor.getInt(0) : 1; } finally { cursor.close(); }
    }

    public synchronized void insert(MediaRecord record) {
        ContentValues v = values(record);
        if (getWritableDatabase().insertOrThrow("media", null, v) == -1) throw new IllegalStateException("media insert failed");
    }

    public synchronized MediaRecord get(String id) {
        Cursor cursor = getReadableDatabase().query("media", null, "id=?", new String[]{id}, null, null, null);
        try { return cursor.moveToFirst() ? fromCursor(cursor) : null; } finally { cursor.close(); }
    }

    public synchronized List<MediaRecord> list() {
        ArrayList<MediaRecord> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("media", null, null, null, null, null, "created DESC");
        try { while (cursor.moveToNext()) result.add(fromCursor(cursor)); } finally { cursor.close(); }
        return result;
    }

    public synchronized int queueCount() {
        return queueSummary().pending;
    }

    public synchronized UploadQueueSummary queueSummary() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT "
                        + "SUM(CASE WHEN status<>? THEN 1 ELSE 0 END),"
                        + "SUM(CASE WHEN status=? THEN 1 ELSE 0 END),"
                        + "SUM(CASE WHEN status=? THEN 1 ELSE 0 END),"
                        + "SUM(CASE WHEN status=? THEN 1 ELSE 0 END),"
                        + "SUM(CASE WHEN status IN (?,?,?) THEN 1 ELSE 0 END),"
                        + "SUM(CASE WHEN status IN (?,?) THEN 1 ELSE 0 END),"
                        + "SUM(CASE WHEN status IN (?,?,?) THEN 1 ELSE 0 END) FROM media",
                new String[]{
                        MediaRecord.UploadStatus.LOCAL_DELETED.name(),
                        MediaRecord.UploadStatus.QUEUED.name(),
                        MediaRecord.UploadStatus.UPLOADING.name(),
                        MediaRecord.UploadStatus.UPLOADED.name(),
                        MediaRecord.UploadStatus.VERIFIED.name(),
                        MediaRecord.UploadStatus.LOCAL_DELETE_PENDING.name(),
                        MediaRecord.UploadStatus.LOCAL_DELETED.name(),
                        MediaRecord.UploadStatus.FAILED_RETRYABLE.name(),
                        MediaRecord.UploadStatus.FAILED_PERMANENT.name(),
                        MediaRecord.UploadStatus.QUEUED.name(),
                        MediaRecord.UploadStatus.UPLOADING.name(),
                        MediaRecord.UploadStatus.FAILED_RETRYABLE.name()
                });
        try {
            if (!cursor.moveToFirst()) return UploadQueueSummary.fromCounts(0, 0, 0, 0, 0, 0, 0);
            return UploadQueueSummary.fromCounts(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2),
                    cursor.getInt(3), cursor.getInt(4), cursor.getInt(5), cursor.getInt(6));
        } finally {
            cursor.close();
        }
    }

    /** The only runtime path for changing a persisted lifecycle state. */
    public synchronized MediaRecord transitionStatus(String id, MediaRecord.UploadStatus status, String error, int retryCount) {
        MediaRecord current = get(id);
        if (current == null) throw new IllegalArgumentException("Unknown media id: " + id);
        UploadStateMachine.requireTransition(current.status, status);
        if (retryCount < 0) throw new IllegalArgumentException("retryCount must be non-negative");
        ContentValues v = new ContentValues(); v.put("status", status.name()); v.put("last_error", error); v.put("retry_count", retryCount);
        int changed = getWritableDatabase().update("media", v, "id=? AND status=?", new String[]{id, current.status.name()});
        if (changed != 1) throw new IllegalStateException("Concurrent media state update: " + id);
        return get(id);
    }

    public synchronized void delete(String id) { getWritableDatabase().delete("media", "id=?", new String[]{id}); }

    public synchronized void insertGalleryMedia(PublicMediaRecord record) {
        ContentValues values = new ContentValues();
        values.put("id", record.id);
        values.put("uri", record.contentUri);
        values.put("seq", record.sequenceNumber);
        values.put("mime", record.mimeType);
        values.put("display_name", record.displayName);
        values.put("created", record.createdAt);
        values.put("byte_size", record.byteSize);
        values.put("metadata", record.metadataJson);
        getWritableDatabase().insertWithOnConflict("gallery_media", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized PublicMediaRecord getGalleryMedia(String id) {
        Cursor cursor = getReadableDatabase().query("gallery_media", null, "id=?",
                new String[]{id}, null, null, null);
        try { return cursor.moveToFirst() ? galleryFromCursor(cursor) : null; }
        finally { cursor.close(); }
    }

    public synchronized List<PublicMediaRecord> listGalleryMedia() {
        ArrayList<PublicMediaRecord> records = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("gallery_media", null, null, null,
                null, null, "created DESC");
        try { while (cursor.moveToNext()) records.add(galleryFromCursor(cursor)); }
        finally { cursor.close(); }
        return records;
    }

    public synchronized void deleteGalleryMedia(String id) {
        getWritableDatabase().delete("gallery_media", "id=?", new String[]{id});
    }

    private static void createGalleryTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS gallery_media (id TEXT PRIMARY KEY, uri TEXT NOT NULL UNIQUE, seq INTEGER NOT NULL, mime TEXT NOT NULL, display_name TEXT NOT NULL, created INTEGER NOT NULL, byte_size INTEGER NOT NULL, metadata TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS gallery_media_created_idx ON gallery_media(created DESC)");
    }

    private static ContentValues values(MediaRecord r) {
        ContentValues v = new ContentValues();
        v.put("id", r.id); v.put("seq", r.sequenceNumber); v.put("mime", r.mimeType); v.put("vault", r.vaultFileName);
        v.put("thumb", r.thumbnailFileName); v.put("display_name", r.displayName); v.put("created", r.createdAt);
        v.put("encrypted_size", r.encryptedSize); v.put("sha256", r.sha256); v.put("metadata", r.metadataJson);
        v.put("status", r.status.name()); v.put("retry_count", r.retryCount); v.put("last_error", r.lastError);
        return v;
    }
    private static MediaRecord fromCursor(Cursor c) {
        return new MediaRecord(c.getString(c.getColumnIndexOrThrow("id")), c.getInt(c.getColumnIndexOrThrow("seq")),
                c.getString(c.getColumnIndexOrThrow("mime")), c.getString(c.getColumnIndexOrThrow("vault")),
                c.getString(c.getColumnIndexOrThrow("thumb")), c.getString(c.getColumnIndexOrThrow("display_name")),
                c.getLong(c.getColumnIndexOrThrow("created")), c.getLong(c.getColumnIndexOrThrow("encrypted_size")),
                c.getString(c.getColumnIndexOrThrow("sha256")), c.getString(c.getColumnIndexOrThrow("metadata")),
                MediaRecord.UploadStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))),
                c.getInt(c.getColumnIndexOrThrow("retry_count")), c.getString(c.getColumnIndexOrThrow("last_error")));
    }

    private static PublicMediaRecord galleryFromCursor(Cursor c) {
        return new PublicMediaRecord(c.getString(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("uri")), c.getInt(c.getColumnIndexOrThrow("seq")),
                c.getString(c.getColumnIndexOrThrow("mime")),
                c.getString(c.getColumnIndexOrThrow("display_name")),
                c.getLong(c.getColumnIndexOrThrow("created")), c.getLong(c.getColumnIndexOrThrow("byte_size")),
                c.getString(c.getColumnIndexOrThrow("metadata")));
    }
}
