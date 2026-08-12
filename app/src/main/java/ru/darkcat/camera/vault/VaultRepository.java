package ru.darkcat.camera.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;

import ru.darkcat.camera.crypto.AuthenticatedFileCipher;
import ru.darkcat.camera.crypto.DarkCatKeyStore;
import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.data.SequenceAllocator;
import ru.darkcat.camera.upload.UploadScheduler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the app-private vault commit. All encrypted media is written as an opaque UUID filename. */
public final class VaultRepository {
    private static final String DIR = "darkcat-vault";
    private static final String SHARE_CACHE_DIR = "darkcat-share";
    private static final int THUMBNAIL_MAX_DIMENSION = 512;
    private static final Object COMMIT_LOCK = new Object();
    private static final AtomicBoolean CACHE_SCAVENGED = new AtomicBoolean();
    private final Context context;
    private final File root;
    private final File recovery;
    private final DarkCatDatabase database;

    public VaultRepository(Context context) {
        this.context = context.getApplicationContext();
        this.root = new File(this.context.getFilesDir(), DIR);
        this.recovery = new File(root, "recovery-pending");
        if (!root.exists()) root.mkdirs();
        if (!recovery.exists()) recovery.mkdirs();
        this.database = DarkCatDatabase.get(this.context);
        scavengePreviousProcessCache();
    }

    public File recoveryDir() { return recovery; }
    public RecoveryStore recoveryStore() { return new RecoveryStore(recovery); }
    public int recoveryPendingCount() { return recoveryStore().pendingCount(); }
    public File vaultFile(String name) { return new File(root, name); }
    public DarkCatDatabase database() { return database; }

    public MediaRecord commit(File plaintext, String displayName, String mimeType, CaptureContext captureContext,
                              boolean crosshairStamped) throws Exception {
        synchronized (COMMIT_LOCK) {
            requireRecoveryFile(plaintext);
            String id = VaultCommitIdentity.forRecoveryFile(plaintext);
            MediaRecord existing = database.get(id);
            if (existing != null) return finishExistingCommit(existing, plaintext);
            int sequence = mimeType != null && mimeType.startsWith("video/")
                    ? SequenceAllocator.reserveVideo(context)
                    : (ru.darkcat.camera.data.DarkCatSettings.sequenceEnabled(context)
                            ? SequenceAllocator.reservePhoto(context) : 0);
            return commitLocked(plaintext, displayName, mimeType, captureContext,
                    crosshairStamped, sequence,
                    plaintext.lastModified() > 0 ? plaintext.lastModified() : System.currentTimeMillis(), id);
        }
    }

    /** Commit using the sequence already reserved at the successful camera callback. */
    public MediaRecord commit(File plaintext, String displayName, String mimeType, CaptureContext captureContext,
                              boolean crosshairStamped, int sequence, long capturedAt) throws Exception {
        synchronized (COMMIT_LOCK) {
            requireRecoveryFile(plaintext);
            String id = VaultCommitIdentity.forRecoveryFile(plaintext);
            MediaRecord existing = database.get(id);
            if (existing != null) return finishExistingCommit(existing, plaintext);
            return commitLocked(plaintext, displayName, mimeType, captureContext,
                    crosshairStamped, sequence, capturedAt, id);
        }
    }

    private MediaRecord commitLocked(File plaintext, String displayName, String mimeType,
                                     CaptureContext captureContext, boolean crosshairStamped,
                                     int sequence, long capturedAt, String id) throws Exception {
        if (sequence < 0) throw new IllegalArgumentException("capture sequence must already be reserved or disabled");
        String vaultName = id + ".dcv";
        String thumbName = id + ".thumb.dcv";
        File encryptedTemp = new File(root, "." + id + ".dcv.tmp");
        File encrypted = new File(root, vaultName);
        File thumbTemp = new File(root, "." + thumbName + ".tmp");
        File thumbnail = new File(root, thumbName);
        File thumbnailPlaintext = new File(recovery, "." + thumbName + ".jpg.tmp");

        // A database row was checked before this point. These deterministic names therefore only
        // belong to an interrupted pre-database attempt for this exact recovery file.
        deleteIfPresent(encryptedTemp);
        deleteIfPresent(encrypted);
        deleteIfPresent(thumbTemp);
        deleteIfPresent(thumbnail);
        deleteIfPresent(thumbnailPlaintext);

        AuthenticatedFileCipher cipher = new AuthenticatedFileCipher(DarkCatKeyStore.vaultKey());
        MediaRecord record;
        try {
            AuthenticatedFileCipher.Result result = cipher.encrypt(plaintext, encryptedTemp);
            if (!encryptedTemp.renameTo(encrypted)) throw new IOException("vault commit rename failed");

            Location location = captureLocation(captureContext);
            String metadata = MediaRecord.metadataJson(capturedAt > 0 ? capturedAt : System.currentTimeMillis(), mimeType,
                    captureContext, location, captureContext == null ? Collections.emptyList() : captureContext.customTags,
                    crosshairStamped);
            String actualThumb = createEncryptedThumbnail(cipher, plaintext, mimeType, thumbName,
                    thumbnailPlaintext, thumbTemp, thumbnail);
            record = new MediaRecord(id, sequence, mimeType, vaultName, actualThumb, safeDisplayName(displayName),
                    System.currentTimeMillis(), result.size, result.sha256, metadata,
                    MediaRecord.UploadStatus.ENCRYPTED, 0, null);
            try {
                database.insert(record);
            } catch (Exception databaseFailure) {
                // Multi-process execution is not expected, but a raced retry must still converge
                // onto the single row instead of creating or deleting another capture.
                MediaRecord raced = database.get(id);
                if (raced != null) {
                    deleteIfPresent(encryptedTemp);
                    deleteIfPresent(thumbTemp);
                    deleteIfPresent(thumbnailPlaintext);
                    return finishExistingCommit(raced, plaintext);
                }
                throw databaseFailure;
            }
        } catch (Exception preDatabaseFailure) {
            // With no durable row, leave only the original recovery plaintext for restart/retry.
            if (database.get(id) == null) {
                cleanupPreDatabaseArtifacts(preDatabaseFailure, encryptedTemp, encrypted,
                        thumbTemp, thumbnail, thumbnailPlaintext);
            }
            throw preDatabaseFailure;
        } catch (OutOfMemoryError memoryFailure) {
            if (database.get(id) == null) {
                cleanupPreDatabaseArtifacts(memoryFailure, encryptedTemp, encrypted,
                        thumbTemp, thumbnail, thumbnailPlaintext);
            }
            throw memoryFailure;
        }

        finishRecoveryCleanup(plaintext);
        scheduleUploadIfEnabled(record);
        return record;
    }

    private MediaRecord finishExistingCommit(MediaRecord existing, File plaintext) throws Exception {
        File encrypted = vaultFile(existing.vaultFileName);
        if (!encrypted.isFile() || encrypted.length() != existing.encryptedSize) {
            throw new IOException("existing vault row has no complete encrypted media");
        }
        if (existing.thumbnailFileName != null && !vaultFile(existing.thumbnailFileName).isFile()) {
            throw new IOException("existing vault row has no encrypted thumbnail");
        }
        finishRecoveryCleanup(plaintext);
        scheduleUploadIfEnabled(existing);
        return existing;
    }

    private void finishRecoveryCleanup(File plaintext) throws IOException {
        // Recovery plaintext is disposable only after the durable vault row exists.
        if (plaintext.exists() && !plaintext.delete()) {
            throw new IOException("recovery cleanup failed; vault is committed");
        }
        recoveryStore().clear(plaintext);
    }

    private void scheduleUploadIfEnabled(MediaRecord record) {
        if (ru.darkcat.camera.data.DarkCatSettings.autoUpload(context)
                && !ru.darkcat.camera.data.DarkCatSettings.PROVIDER_OFF.equals(
                        ru.darkcat.camera.data.DarkCatSettings.provider(context))) {
            UploadScheduler.enqueue(context, record.id);
        }
    }

    private static void requireRecoveryFile(File plaintext) throws IOException {
        if (plaintext == null || !plaintext.isFile() || plaintext.length() == 0) {
            throw new IOException("recovery file is missing");
        }
    }

    private static void cleanupPreDatabaseArtifacts(Throwable failure, File... files) {
        for (File file : files) {
            try { deleteIfPresent(file); }
            catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
        }
    }

    private static Location captureLocation(CaptureContext captureContext) {
        if (captureContext == null || captureContext.captureLatitude == null
                || captureContext.captureLongitude == null) return null;
        Location location = new Location(captureContext.captureLocationProvider == null
                ? LocationManager.GPS_PROVIDER : captureContext.captureLocationProvider);
        location.setLatitude(captureContext.captureLatitude);
        location.setLongitude(captureContext.captureLongitude);
        if (captureContext.captureAccuracyMeters != null)
            location.setAccuracy(captureContext.captureAccuracyMeters);
        if (captureContext.captureLocationElapsedRealtimeNanos > 0)
            location.setElapsedRealtimeNanos(captureContext.captureLocationElapsedRealtimeNanos);
        return location;
    }

    private String createEncryptedThumbnail(AuthenticatedFileCipher cipher, File plaintext,
                                            String mimeType, String name, File image, File tmp,
                                            File destination) throws Exception {
        Bitmap decoded = null;
        Bitmap thumbnail = null;
        boolean published = false;
        try {
            if (mimeType != null && mimeType.startsWith("video/")) {
                decoded = ThumbnailUtils.createVideoThumbnail(plaintext.getAbsolutePath(),
                        MediaStore.Video.Thumbnails.MINI_KIND);
            } else {
                decoded = decodeSampledImage(plaintext);
            }
            if (decoded == null) return null;
            thumbnail = scaleDown(decoded, THUMBNAIL_MAX_DIMENSION);
            try (FileOutputStream output = new FileOutputStream(image)) {
                if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                    throw new IOException("thumbnail encoder rejected bitmap");
                }
                output.flush();
                output.getFD().sync();
            }
            cipher.encrypt(image, tmp);
            if (!tmp.renameTo(destination)) throw new IOException("thumbnail commit rename failed");
            published = true;
            deleteIfPresent(image);
            return name;
        } catch (OutOfMemoryError memoryFailure) {
            // A thumbnail is optional. The full recovery media remains available for encryption,
            // and sampled decoding prevents the common full-resolution allocation path.
            try {
                deleteIfPresent(image);
                deleteIfPresent(tmp);
                deleteIfPresent(destination);
            } catch (IOException cleanupFailure) {
                cleanupFailure.addSuppressed(memoryFailure);
                throw cleanupFailure;
            }
            return null;
        } finally {
            if (thumbnail != null && thumbnail != decoded && !thumbnail.isRecycled()) thumbnail.recycle();
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
            deleteBestEffort(image);
            deleteBestEffort(tmp);
            if (!published) deleteBestEffort(destination);
        }
    }

    private static Bitmap decodeSampledImage(File plaintext) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(plaintext.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = ThumbnailSampling.inSampleSize(bounds.outWidth, bounds.outHeight,
                THUMBNAIL_MAX_DIMENSION);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(plaintext.getAbsolutePath(), options);
    }

    private static Bitmap scaleDown(Bitmap bitmap, int maximumDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maximumDimension) return bitmap;
        float scale = maximumDimension / (float) largest;
        return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
    }

    public File decryptToCache(MediaRecord record) throws Exception {
        File cache = File.createTempFile(".darkcat-decrypted-" + record.id + "-",
                extension(record.displayName, record.mimeType), shareCacheDir());
        try {
            new AuthenticatedFileCipher(DarkCatKeyStore.vaultKey()).decrypt(
                    vaultFile(record.vaultFileName), cache);
            return cache;
        } catch (Exception failure) {
            cleanupDecryptedCacheQuietly(cache);
            throw failure;
        } catch (OutOfMemoryError failure) {
            cleanupDecryptedCacheQuietly(cache);
            throw failure;
        }
    }
    public File decryptThumbnailToCache(MediaRecord record) throws Exception {
        if (record.thumbnailFileName == null) return null;
        File cache = File.createTempFile(".darkcat-decrypted-thumb-" + record.id + "-", ".jpg",
                context.getCacheDir());
        try {
            new AuthenticatedFileCipher(DarkCatKeyStore.vaultKey()).decrypt(
                    vaultFile(record.thumbnailFileName), cache);
            return cache;
        } catch (Exception failure) {
            cleanupDecryptedCacheQuietly(cache);
            throw failure;
        } catch (OutOfMemoryError failure) {
            cleanupDecryptedCacheQuietly(cache);
            throw failure;
        }
    }

    /** Deletes only one plaintext session file created by this repository. */
    public void cleanupDecryptedCache(File cacheFile) throws IOException {
        if (cacheFile == null) return;
        File cacheRoot = context.getCacheDir().getCanonicalFile();
        File shareRoot = new File(cacheRoot, SHARE_CACHE_DIR).getCanonicalFile();
        File candidate = cacheFile.getCanonicalFile();
        if ((!cacheRoot.equals(candidate.getParentFile()) && !shareRoot.equals(candidate.getParentFile()))
                || !candidate.getName().startsWith(".darkcat-decrypted-")) {
            throw new IOException("refusing to delete a non-session cache file");
        }
        deleteIfPresent(candidate);
    }

    public void cleanupDecryptedCacheQuietly(File cacheFile) {
        try { cleanupDecryptedCache(cacheFile); } catch (IOException ignored) { }
    }

    private void scavengePreviousProcessCache() {
        if (!CACHE_SCAVENGED.compareAndSet(false, true)) return;
        scavengeCacheDirectory(context.getCacheDir());
        scavengeCacheDirectory(new File(context.getCacheDir(), SHARE_CACHE_DIR));
    }

    private void scavengeCacheDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (file.isFile() && (name.startsWith(".darkcat-decrypted-")
                    || name.startsWith(".darkcat-decrypt-")
                    || name.startsWith("darkcat-open-")
                    || name.startsWith("darkcat-thumb-"))) {
                deleteBestEffort(file);
            }
        }
    }

    private File shareCacheDir() throws IOException {
        File directory = new File(context.getCacheDir(), SHARE_CACHE_DIR);
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException("unable to create share cache directory");
        return directory;
    }

    /** Returns false and preserves the row if either encrypted core file cannot be deleted. */
    public boolean delete(MediaRecord record) {
        try { deleteFilesChecked(record); }
        catch (IOException ignored) { return false; }
        database.delete(record.id);
        return true;
    }
    public void deleteFiles(MediaRecord record) {
        try { deleteFilesChecked(record); } catch (IOException ignored) { }
    }
    public void deleteFilesChecked(MediaRecord record) throws IOException {
        // Delete the optional derivative first, so a thumbnail failure cannot orphan the only
        // full encrypted media while its database row is deliberately retained.
        if (record.thumbnailFileName != null) deleteIfPresent(vaultFile(record.thumbnailFileName));
        deleteIfPresent(vaultFile(record.vaultFileName));
    }
    private static void deleteIfPresent(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("Unable to delete local vault file: " + file.getName());
    }
    private static void deleteBestEffort(File file) {
        if (file != null && file.exists()) { //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
    private static String extension(String name, String mime) { int dot = name == null ? -1 : name.lastIndexOf('.'); if (dot >= 0) return name.substring(dot); return mime != null && mime.startsWith("video/") ? ".mp4" : ".jpg"; }
    private static String safeDisplayName(String name) { return name == null || name.length() == 0 ? "capture-" + new Date().getTime() : new File(name).getName(); }
}
