package ru.darkcat.camera.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.location.Location;
import android.location.LocationManager;
import android.content.pm.PackageManager;
import android.provider.MediaStore;

import ru.darkcat.camera.crypto.AuthenticatedFileCipher;
import ru.darkcat.camera.crypto.DarkCatKeyStore;
import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.upload.UploadScheduler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

/** Owns the app-private vault commit. All encrypted media is written as a random UUID filename. */
public final class VaultRepository {
    private static final String DIR = "darkcat-vault";
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
    }

    public File recoveryDir() { return recovery; }
    public File vaultFile(String name) { return new File(root, name); }
    public DarkCatDatabase database() { return database; }

    public MediaRecord commit(File plaintext, String displayName, String mimeType, CaptureContext captureContext,
                              boolean crosshairStamped) throws Exception {
        if (!plaintext.exists() || plaintext.length() == 0) throw new java.io.IOException("recovery file is missing");
        String id = UUID.randomUUID().toString();
        int sequence = database.nextSequence();
        String vaultName = id + ".dcv";
        String thumbName = id + ".thumb.dcv";
        File encryptedTemp = new File(root, "." + id + ".dcv.tmp");
        File encrypted = new File(root, vaultName);
        AuthenticatedFileCipher cipher = new AuthenticatedFileCipher(DarkCatKeyStore.vaultKey());
        AuthenticatedFileCipher.Result result = cipher.encrypt(plaintext, encryptedTemp);
        if (!encryptedTemp.renameTo(encrypted)) throw new java.io.IOException("vault commit rename failed");

        Location location = lastKnownLocation();
        String metadata = MediaRecord.metadataJson(plaintext.lastModified() > 0 ? plaintext.lastModified() : System.currentTimeMillis(), mimeType,
                captureContext, location, captureContext == null ? Collections.emptyList() : captureContext.customTags, crosshairStamped);
        String actualThumb = createEncryptedThumbnail(cipher, plaintext, mimeType, thumbName);
        MediaRecord record = new MediaRecord(id, sequence, mimeType, vaultName, actualThumb, safeDisplayName(displayName),
                System.currentTimeMillis(), result.size, result.sha256, metadata, MediaRecord.UploadStatus.ENCRYPTED, 0, null);
        try {
            database.insert(record);
        } catch (Exception databaseFailure) {
            // Keep both vault and recovery material. Recovery tooling can reconcile orphaned vault files later.
            throw databaseFailure;
        }
        // Recovery plaintext is disposable only after the durable vault row exists.
        if (!plaintext.delete()) throw new java.io.IOException("recovery cleanup failed; vault is committed");
        if (ru.darkcat.camera.data.DarkCatSettings.autoUpload(context)) {
            database.updateStatus(record.id, MediaRecord.UploadStatus.QUEUED, null, 0);
            UploadScheduler.enqueue(context, record.id);
        }
        return record;
    }

    private Location lastKnownLocation() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            return gps != null ? gps : manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) { return null; }
    }

    private String createEncryptedThumbnail(AuthenticatedFileCipher cipher, File plaintext, String mimeType, String name) throws Exception {
        Bitmap bitmap = null;
        if (mimeType != null && mimeType.startsWith("video/")) bitmap = ThumbnailUtils.createVideoThumbnail(plaintext.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
        else bitmap = BitmapFactory.decodeFile(plaintext.getAbsolutePath());
        if (bitmap == null) return null;
        File image = new File(recovery, name + ".jpg");
        try (FileOutputStream output = new FileOutputStream(image)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output); }
        finally { bitmap.recycle(); }
        File tmp = new File(root, "." + name + ".tmp"); File destination = new File(root, name);
        cipher.encrypt(image, tmp); if (!tmp.renameTo(destination)) throw new java.io.IOException("thumbnail commit rename failed");
        if (!image.delete()) throw new java.io.IOException("thumbnail recovery cleanup failed");
        return name;
    }

    public File decryptToCache(MediaRecord record) throws Exception {
        File cache = new File(context.getCacheDir(), "darkcat-open-" + record.id + extension(record.displayName, record.mimeType));
        new AuthenticatedFileCipher(DarkCatKeyStore.vaultKey()).decrypt(vaultFile(record.vaultFileName), cache);
        return cache;
    }
    public File decryptThumbnailToCache(MediaRecord record) throws Exception {
        if (record.thumbnailFileName == null) return null;
        File cache = new File(context.getCacheDir(), "darkcat-thumb-" + record.id + ".jpg");
        new AuthenticatedFileCipher(DarkCatKeyStore.vaultKey()).decrypt(vaultFile(record.thumbnailFileName), cache);
        return cache;
    }
    public void delete(MediaRecord record) {
        database.delete(record.id);
        deleteFiles(record);
    }
    public void deleteFiles(MediaRecord record) {
        //noinspection ResultOfMethodCallIgnored
        vaultFile(record.vaultFileName).delete();
        if (record.thumbnailFileName != null) vaultFile(record.thumbnailFileName).delete();
    }
    private static String extension(String name, String mime) { int dot = name == null ? -1 : name.lastIndexOf('.'); if (dot >= 0) return name.substring(dot); return mime != null && mime.startsWith("video/") ? ".mp4" : ".jpg"; }
    private static String safeDisplayName(String name) { return name == null || name.length() == 0 ? "capture-" + new Date().getTime() : new File(name).getName(); }
}
