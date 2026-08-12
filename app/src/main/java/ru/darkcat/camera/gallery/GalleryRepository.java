package ru.darkcat.camera.gallery;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.data.PublicMediaRecord;
import ru.darkcat.camera.vault.VaultRepository;

/** Unified, local-only index for the DarkCat gallery. It never imports unrelated system images. */
public final class GalleryRepository {
    private final Context context;
    private final DarkCatDatabase database;

    public GalleryRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = DarkCatDatabase.get(this.context);
    }

    public List<GalleryItem> list() {
        ArrayList<GalleryItem> result = new ArrayList<>();
        for (MediaRecord record : database.list()) {
            if (record.status != MediaRecord.UploadStatus.LOCAL_DELETED) result.add(GalleryItem.fromVault(record));
        }
        for (PublicMediaRecord record : database.listGalleryMedia()) result.add(GalleryItem.fromMediaStore(record));
        Collections.sort(result);
        return result;
    }

    public GalleryItem get(String source, String id) {
        if (id == null) return null;
        if (GalleryItem.Source.MEDIASTORE.name().equals(source)) {
            PublicMediaRecord record = database.getGalleryMedia(id);
            return record == null ? null : GalleryItem.fromMediaStore(record);
        }
        MediaRecord record = database.get(id);
        return record == null ? null : GalleryItem.fromVault(record);
    }

    public boolean delete(GalleryItem item) {
        if (item == null) return false;
        if (item.source == GalleryItem.Source.MEDIASTORE) {
            PublicMediaRecord record = database.getGalleryMedia(item.id);
            return MediaStoreCaptureStore.delete(context, record);
        }
        return new VaultRepository(context).delete(item.vaultRecord);
    }

    public EditorInput prepareEditorInput(GalleryItem item) throws Exception {
        if (item == null || item.isVideo()) throw new java.io.IOException("only still images can be edited");
        File root = new File(context.getFilesDir(), "darkcat-editor");
        if (!root.exists() && !root.mkdirs()) throw new java.io.IOException("editor workspace unavailable");
        File target = new File(root, UUID.randomUUID() + ".jpg");
        File decrypted = null;
        try {
            InputStream input;
            if (item.source == GalleryItem.Source.VAULT) {
                decrypted = new VaultRepository(context).decryptToCache(item.vaultRecord);
                input = new FileInputStream(decrypted);
            } else {
                input = context.getContentResolver().openInputStream(item.publicUri);
            }
            if (input == null) throw new java.io.IOException("gallery source unavailable");
            try (InputStream source = input; FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024]; int read;
                while ((read = source.read(buffer)) != -1) output.write(buffer, 0, read);
                output.flush(); output.getFD().sync();
            }
            return new EditorInput(target, item.displayName, item.mimeType, captureContext(item.metadataJson),
                    item.source, item.id);
        } catch (Exception error) {
            if (target.exists()) { //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            throw error;
        } finally {
            if (decrypted != null) new VaultRepository(context).cleanupDecryptedCacheQuietly(decrypted);
        }
    }

    public static CaptureContext captureContext(String metadata) {
        try {
            JSONObject object = new JSONObject(metadata == null ? "{}" : metadata);
            JSONObject capture = object.optJSONObject("captureContext");
            return capture == null ? CaptureContext.empty() : CaptureContext.fromJson(capture);
        } catch (Exception ignored) { return CaptureContext.empty(); }
    }

    public static final class EditorInput {
        public final File file;
        public final String displayName;
        public final String mimeType;
        public final CaptureContext captureContext;
        public final GalleryItem.Source source;
        public final String sourceId;
        EditorInput(File file, String displayName, String mimeType, CaptureContext captureContext,
                    GalleryItem.Source source, String sourceId) {
            this.file = file; this.displayName = displayName; this.mimeType = mimeType;
            this.captureContext = captureContext; this.source = source; this.sourceId = sourceId;
        }
    }
}
