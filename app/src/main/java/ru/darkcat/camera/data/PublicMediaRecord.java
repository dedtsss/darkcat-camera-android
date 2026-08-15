package ru.darkcat.camera.data;

/** Durable local index for a DarkCat image written to the user's MediaStore gallery. */
public final class PublicMediaRecord {
    public final String id;
    public final String contentUri;
    public final int sequenceNumber;
    public final String mimeType;
    public final String displayName;
    public final long createdAt;
    public final long byteSize;
    public final String metadataJson;

    public PublicMediaRecord(String id, String contentUri, int sequenceNumber, String mimeType,
                             String displayName, long createdAt, long byteSize,
                             String metadataJson) {
        this.id = id;
        this.contentUri = contentUri;
        this.sequenceNumber = sequenceNumber;
        this.mimeType = mimeType;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.byteSize = byteSize;
        this.metadataJson = metadataJson == null ? "{}" : metadataJson;
    }
}
