package ru.darkcat.camera.gallery;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.data.StorageMode;
import ru.darkcat.camera.vault.RecoveryStore;

/**
 * Gallery-specific use of the existing durable recovery sidecar.
 *
 * <p>The sidecar is written beside the app-private JPEG before MediaStore publication. It retains
 * the exact shutter-time context and a stable publication key, so a restart can retry without
 * inventing a new sequence, timestamp, GPS fix, tags, or destination.</p>
 */
public final class MediaStoreRecoveryJournal {
    public static RecoveryStore.PendingCapture markPending(RecoveryStore store, File jpeg,
                                                            int sequence, String displayName,
                                                            long capturedAt,
                                                            CaptureContext captureContext) throws Exception {
        CaptureContext checked = captureContext == null ? CaptureContext.empty() : captureContext;
        return store.markPending(jpeg, sequence, displayName, "image/jpeg", capturedAt,
                CaptureContextJournalCodec.encode(checked), false,
                StorageMode.MEDIASTORE.preferenceValue());
    }

    public static boolean isGallery(RecoveryStore.PendingCapture pending) {
        return pending != null && StorageMode.MEDIASTORE.preferenceValue().equals(pending.destination);
    }

    public static CaptureContext captureContext(RecoveryStore.PendingCapture pending) {
        if (pending == null) return CaptureContext.empty();
        if (CaptureContextJournalCodec.isEncoded(pending.captureContextJson))
            return CaptureContextJournalCodec.decode(pending.captureContextJson);
        // Defensive compatibility for a sidecar created by an interrupted pre-release build.
        try { return CaptureContext.fromJson(new org.json.JSONObject(pending.captureContextJson)); }
        catch (Exception ignored) { return CaptureContext.empty(); }
    }

    /** Stable across a restart for a single durable capture; MediaStore uses it for retry idempotence. */
    public static String publicationKey(RecoveryStore.PendingCapture pending) {
        if (pending == null || pending.mediaFile == null) throw new IllegalArgumentException("pending capture required");
        String input = pending.mediaFile.getName() + "\n" + pending.sequenceNumber + "\n" + pending.capturedAt;
        return "darkcat-field-" + UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    private MediaStoreRecoveryJournal() { }
}
