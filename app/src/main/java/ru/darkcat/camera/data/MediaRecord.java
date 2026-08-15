package ru.darkcat.camera.data;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MediaRecord {
    /**
     * Durable media lifecycle values. They are stored as TEXT, so adding values does not require a
     * schema migration and remains compatible with the version-1 database.
     */
    public enum UploadStatus {
        CAPTURED,
        RECOVERY_PENDING,
        ENCRYPTED,
        QUEUED,
        UPLOADING,
        UPLOADED,
        VERIFIED,
        FAILED_RETRYABLE,
        FAILED_PERMANENT,
        LOCAL_DELETE_PENDING,
        LOCAL_DELETED
    }
    public final String id;
    public final int sequenceNumber;
    public final String mimeType;
    public final String vaultFileName;
    public final String thumbnailFileName;
    public final String displayName;
    public final long createdAt;
    public final long encryptedSize;
    public final String sha256;
    public final String metadataJson;
    public final UploadStatus status;
    public final int retryCount;
    public final String lastError;

    public MediaRecord(String id, int sequenceNumber, String mimeType, String vaultFileName,
                       String thumbnailFileName, String displayName, long createdAt, long encryptedSize,
                       String sha256, String metadataJson, UploadStatus status, int retryCount, String lastError) {
        this.id = id; this.sequenceNumber = sequenceNumber; this.mimeType = mimeType;
        this.vaultFileName = vaultFileName; this.thumbnailFileName = thumbnailFileName; this.displayName = displayName;
        this.createdAt = createdAt; this.encryptedSize = encryptedSize; this.sha256 = sha256;
        this.metadataJson = metadataJson; this.status = status; this.retryCount = retryCount; this.lastError = lastError;
    }

    public static String metadataJson(long capturedAt, String mimeType, CaptureContext context, Location location, List<String> tags, boolean crosshairStamped) {
        JSONObject object = new JSONObject();
        try {
            object.put("capturedAt", capturedAt);
            object.put("mimeType", mimeType);
            object.put("latitude", location == null ? JSONObject.NULL : location.getLatitude());
            object.put("longitude", location == null ? JSONObject.NULL : location.getLongitude());
            object.put("accuracy", location == null || !location.hasAccuracy()
                    ? JSONObject.NULL : location.getAccuracy());
            object.put("altitude", location == null || !location.hasAltitude()
                    ? JSONObject.NULL : location.getAltitude());
            object.put("crosshairStamped", crosshairStamped);
            object.put("tags", new JSONArray(tags == null ? new ArrayList<>() : tags));
            object.put("captureContext", context == null ? CaptureContext.empty().toJson() : context.toJson());
        } catch (JSONException ignored) { }
        return object.toString();
    }

    public static String metadataSummary(String metadataJson) {
        try {
            JSONObject object = new JSONObject(metadataJson);
            StringBuilder value = new StringBuilder();
            value.append(object.optString("mimeType", "media")).append(" • ");
            value.append(object.optString("capturedAt", ""));
            JSONObject context = object.optJSONObject("captureContext");
            if (context != null && context.optString("taskId", "").length() > 0) value.append(" • task ").append(context.optString("taskId"));
            return value.toString();
        } catch (JSONException ignored) { return "media"; }
    }
}
