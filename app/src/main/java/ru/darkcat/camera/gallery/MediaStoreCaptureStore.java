package ru.darkcat.camera.gallery;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.DarkCatPreferencePolicy;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.data.PublicMediaRecord;
import ru.darkcat.camera.vault.ImageStamper;

/** Writes user-owned DarkCat photos directly to MediaStore/Pictures/DarkCat. */
public final class MediaStoreCaptureStore {
    public PublicMediaRecord saveJpeg(Context context, byte[] jpeg, String requestedName, int sequence,
                                      long capturedAt, CaptureContext captureContext) throws Exception {
        return saveJpeg(context, jpeg, requestedName, sequence, capturedAt, captureContext, null);
    }

    /**
     * A durable recovery sidecar supplies {@code publicationKey}; repeated retry then converges on
     * one MediaStore row even if process death happened after publication but before the local index.
     */
    public PublicMediaRecord saveJpeg(Context context, byte[] jpeg, String requestedName, int sequence,
                                      long capturedAt, CaptureContext captureContext,
                                      String publicationKey) throws Exception {
        if (jpeg == null || jpeg.length < 4) throw new java.io.IOException("camera JPEG is empty");
        File staged = stagingFile(context, ".jpg");
        try (FileOutputStream output = new FileOutputStream(staged)) {
            output.write(jpeg);
            output.flush();
            output.getFD().sync();
        }
        // A decorative overlay must not turn a successful ordinary Gallery capture into loss.
        try { ImageStamper.stamp(staged, context, sequence, captureContext, capturedAt); }
        catch (Exception ignored) { /* Store original JPEG and retain capture metadata. */ }
        // ImageStamper flattens a bitmap when an overlay is present. Restore the exact fix that
        // was bound at shutter time, rather than consulting a newer live location here.
        writeCaptureLocationExif(staged, captureContext, capturedAt);
        try { return publishFile(context, staged, requestedName, sequence, capturedAt, captureContext, publicationKey); }
        finally { if (staged.exists()) { //noinspection ResultOfMethodCallIgnored
            staged.delete();
        } }
    }

    /** Used by explicit Editor saves: the bitmap is already a user-authored final image. */
    public PublicMediaRecord saveBitmap(Context context, Bitmap bitmap, String requestedName, int sequence,
                                        long capturedAt, CaptureContext captureContext) throws Exception {
        if (bitmap == null) throw new java.io.IOException("editor bitmap missing");
        File staged = stagingFile(context, ".jpg");
        try (FileOutputStream output = new FileOutputStream(staged)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
                throw new java.io.IOException("editor JPEG encoder rejected bitmap");
            output.flush();
            output.getFD().sync();
        } finally { bitmap.recycle(); }
        try { return publishFile(context, staged, requestedName, sequence, capturedAt, captureContext, null); }
        finally { if (staged.exists()) { //noinspection ResultOfMethodCallIgnored
            staged.delete();
        } }
    }

    public static boolean delete(Context context, PublicMediaRecord record) {
        if (record == null) return false;
        boolean removed = false;
        try { removed = context.getContentResolver().delete(Uri.parse(record.contentUri), null, null) > 0; }
        catch (RuntimeException ignored) { }
        if (removed) DarkCatDatabase.get(context).deleteGalleryMedia(record.id);
        return removed;
    }

    private PublicMediaRecord publishFile(Context context, File source, String requestedName,
                                          int sequence, long capturedAt,
                                          CaptureContext captureContext, String publicationKey) throws Exception {
        if (publicationKey != null && !publicationKey.trim().isEmpty()) {
            PublicMediaRecord indexed = DarkCatDatabase.get(context).getGalleryMedia(publicationKey);
            if (indexed != null) return indexed;
            PublicMediaRecord published = findPublishedRecovery(context, publicationKey, sequence,
                    capturedAt, captureContext);
            if (published != null) {
                DarkCatDatabase.get(context).insertGalleryMedia(published);
                return published;
            }
        }
        ContentResolver resolver = context.getContentResolver();
        String displayName = displayName(requestedName, capturedAt);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_TAKEN, capturedAt);
        values.put(MediaStore.Images.Media.DATE_ADDED, capturedAt / 1000L);
        if (publicationKey != null && !publicationKey.trim().isEmpty()) {
            values.put(MediaStore.Images.ImageColumns.DESCRIPTION, publicationKey);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + DarkCatPreferencePolicy.DARKCAT_MEDIASTORE_FOLDER);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new java.io.IOException("MediaStore insert rejected");
        boolean published = false;
        try {
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new java.io.IOException("MediaStore output unavailable");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.flush();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues complete = new ContentValues();
                complete.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, complete, null, null);
            }
            PublicMediaRecord record = new PublicMediaRecord(
                    publicationKey == null || publicationKey.trim().isEmpty()
                            ? UUID.randomUUID().toString() : publicationKey,
                    uri.toString(),
                    sequence, "image/jpeg", displayName, capturedAt, source.length(),
                    metadata(capturedAt, captureContext));
            DarkCatDatabase.get(context).insertGalleryMedia(record);
            published = true;
            return record;
        } finally {
            if (!published) {
                try { resolver.delete(uri, null, null); } catch (RuntimeException ignored) { }
            }
        }
    }

    private static File stagingFile(Context context, String extension) throws Exception {
        File root = new File(context.getCacheDir(), "darkcat-gallery-staging");
        if (!root.exists() && !root.mkdirs()) throw new java.io.IOException("gallery staging unavailable");
        return new File(root, UUID.randomUUID() + extension);
    }

    private static PublicMediaRecord findPublishedRecovery(Context context, String publicationKey,
                                                            int sequence, long capturedAt,
                                                            CaptureContext captureContext) {
        String[] projection = new String[]{MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE};
        android.database.Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, MediaStore.Images.ImageColumns.DESCRIPTION + "=?",
                    new String[]{publicationKey}, null);
            if (cursor == null || !cursor.moveToFirst()) return null;
            long id = cursor.getLong(0);
            String displayName = cursor.getString(1);
            long size = cursor.isNull(2) ? 0L : cursor.getLong(2);
            return new PublicMediaRecord(publicationKey,
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    sequence, "image/jpeg", displayName, capturedAt, size,
                    metadata(capturedAt, captureContext));
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static String displayName(String requested, long capturedAt) {
        String name = requested == null ? "" : new File(requested).getName().trim();
        if (name.isEmpty()) name = "DarkCat-" + capturedAt + ".jpg";
        if (!name.toLowerCase(java.util.Locale.US).endsWith(".jpg")
                && !name.toLowerCase(java.util.Locale.US).endsWith(".jpeg")) name += ".jpg";
        return name;
    }

    private static String metadata(long capturedAt, CaptureContext context) {
        Location location = null;
        if (context != null && context.captureLatitude != null && context.captureLongitude != null) {
            location = new Location(context.captureLocationProvider == null
                    ? LocationManager.GPS_PROVIDER : context.captureLocationProvider);
            location.setLatitude(context.captureLatitude);
            location.setLongitude(context.captureLongitude);
            if (context.captureAccuracyMeters != null) location.setAccuracy(context.captureAccuracyMeters);
        }
        return MediaRecord.metadataJson(capturedAt, "image/jpeg", context, location,
                context == null ? null : context.customTags, false);
    }

    private static void writeCaptureLocationExif(File file, CaptureContext context, long capturedAt) {
        if (context == null || context.captureLatitude == null || context.captureLongitude == null) return;
        try {
            Location location = new Location(context.captureLocationProvider == null
                    ? LocationManager.GPS_PROVIDER : context.captureLocationProvider);
            location.setLatitude(context.captureLatitude);
            location.setLongitude(context.captureLongitude);
            location.setTime(capturedAt);
            if (context.captureAccuracyMeters != null) location.setAccuracy(context.captureAccuracyMeters);
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            exif.setGpsInfo(location);
            exif.saveAttributes();
        } catch (Exception ignored) {
            // The MediaStore item and its indexed capture metadata are still valid if an OEM JPEG
            // does not accept EXIF rewriting.
        }
    }
}
