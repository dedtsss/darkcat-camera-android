package ru.darkcat.camera.gallery;

import android.net.Uri;

import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.data.PublicMediaRecord;

/** A timeline item independent of whether its original is encrypted in Vault or public in MediaStore. */
public final class GalleryItem implements Comparable<GalleryItem> {
    public enum Source { VAULT, MEDIASTORE }

    public final Source source;
    public final String id;
    public final int sequenceNumber;
    public final String mimeType;
    public final String displayName;
    public final long createdAt;
    public final long byteSize;
    public final String metadataJson;
    public final Uri publicUri;
    public final MediaRecord vaultRecord;

    private GalleryItem(Source source, String id, int sequenceNumber, String mimeType, String displayName,
                        long createdAt, long byteSize, String metadataJson, Uri publicUri,
                        MediaRecord vaultRecord) {
        this.source = source; this.id = id; this.sequenceNumber = sequenceNumber; this.mimeType = mimeType;
        this.displayName = displayName; this.createdAt = createdAt; this.byteSize = byteSize;
        this.metadataJson = metadataJson; this.publicUri = publicUri; this.vaultRecord = vaultRecord;
    }

    public static GalleryItem fromVault(MediaRecord record) {
        return new GalleryItem(Source.VAULT, record.id, record.sequenceNumber, record.mimeType,
                record.displayName, record.createdAt, record.encryptedSize, record.metadataJson, null, record);
    }

    public static GalleryItem fromMediaStore(PublicMediaRecord record) {
        return new GalleryItem(Source.MEDIASTORE, record.id, record.sequenceNumber, record.mimeType,
                record.displayName, record.createdAt, record.byteSize, record.metadataJson,
                Uri.parse(record.contentUri), null);
    }

    public boolean isVideo() { return mimeType != null && mimeType.startsWith("video/"); }

    @Override public int compareTo(GalleryItem other) {
        return GalleryTimelineOrder.newestFirst(createdAt, id, other.createdAt, other.id);
    }
}
