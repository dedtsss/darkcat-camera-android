package ru.darkcat.camera.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Structured durable media/queue state. The encrypted file is never represented by a Set in preferences. */
public final class DarkCatDatabase extends SQLiteOpenHelper {
    private static final String NAME = "darkcat.db";
    private static final int VERSION = 1;
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
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

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
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM media WHERE status NOT IN ('VERIFIED','LOCAL_DELETED','FAILED_PERMANENT')", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public synchronized void updateStatus(String id, MediaRecord.UploadStatus status, String error, int retryCount) {
        ContentValues v = new ContentValues(); v.put("status", status.name()); v.put("last_error", error); v.put("retry_count", retryCount);
        getWritableDatabase().update("media", v, "id=?", new String[]{id});
    }

    public synchronized void delete(String id) { getWritableDatabase().delete("media", "id=?", new String[]{id}); }

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
}
