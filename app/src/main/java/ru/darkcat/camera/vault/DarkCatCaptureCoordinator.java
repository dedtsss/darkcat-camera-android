package ru.darkcat.camera.vault;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.ui.EditorActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adapter between Linked Camera's completed media callbacks and the protected pipeline. */
public final class DarkCatCaptureCoordinator {
    private static final ExecutorService VIDEO_EXECUTOR = Executors.newSingleThreadExecutor();

    public static boolean interceptFile(Context context, File source, boolean video) {
        if (!DarkCatSettings.isSecureMode(context) || source == null || !source.isFile() || source.length() == 0) return false;
        try { return ingest(context, new Source(source, null, source.getName(), video ? "video/mp4" : "image/jpeg"), video, false); }
        catch (Exception ignored) { return true; }
    }

    public static boolean interceptUri(Context context, Uri uri, boolean video) {
        if (!DarkCatSettings.isSecureMode(context) || uri == null) return false;
        try { return ingest(context, new Source(null, uri, displayName(context, uri), video ? "video/mp4" : "image/jpeg"), video, false); }
        catch (Exception ignored) { return true; }
    }

    public static boolean interceptVideoAsync(Context context, Uri uri, String filename, boolean videoCaptureIntent) {
        if (!DarkCatSettings.isSecureMode(context) || videoCaptureIntent) return false;
        Source source = filename == null ? new Source(null, uri, displayName(context, uri), "video/mp4") : new Source(new File(filename), uri, new File(filename).getName(), "video/mp4");
        VIDEO_EXECUTOR.execute(() -> { try { ingest(context, source, true, true); } catch (Exception ignored) { /* source remains recovery-pending */ } });
        return true;
    }

    public static void finalizeEdited(Context context, String recoveryPath, String displayName, String mimeType, CaptureContext captureContext) throws Exception {
        File file = new File(recoveryPath); if (!file.isFile()) throw new java.io.IOException("edit recovery file missing");
        ImageStamper.stampCrosshair(file, context);
        new VaultRepository(context).commit(file, displayName, mimeType, captureContext, DarkCatSettings.CROSSHAIR_STAMP.equals(DarkCatSettings.crosshair(context)));
    }

    private static boolean ingest(Context context, Source source, boolean video, boolean asynchronous) throws Exception {
        VaultRepository repository = new VaultRepository(context);
        File recovery = new File(repository.recoveryDir(), UUID.randomUUID() + (video ? ".mp4" : ".jpg"));
        copySource(context, source, recovery);
        // Source deletion happens only after an equivalent recovery-pending copy exists.
        deleteSource(context, source);
        CaptureContext captureContext = context instanceof android.app.Activity
                ? CaptureContext.fromIntent(((android.app.Activity) context).getIntent())
                : CaptureContext.empty();
        if (!video && DarkCatSettings.MODE_EDIT.equals(DarkCatSettings.workflow(context))) {
            Intent intent = new Intent(context, EditorActivity.class).putExtra(EditorActivity.EXTRA_RECOVERY_PATH, recovery.getAbsolutePath())
                    .putExtra(EditorActivity.EXTRA_DISPLAY_NAME, source.displayName).putExtra(EditorActivity.EXTRA_MIME, source.mimeType)
                    .putExtra(CaptureContext.EXTRA_CONTEXT_JSON, captureContext.toJson().toString());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); return true;
        }
        if (!video) ImageStamper.stampCrosshair(recovery, context);
        repository.commit(recovery, source.displayName, source.mimeType, captureContext, !video && DarkCatSettings.CROSSHAIR_STAMP.equals(DarkCatSettings.crosshair(context)));
        return true;
    }

    private static void copySource(Context context, Source source, File destination) throws Exception {
        InputStream input = source.file != null ? new FileInputStream(source.file) : context.getContentResolver().openInputStream(source.uri);
        if (input == null) throw new java.io.IOException("capture source cannot be opened");
        destination.getParentFile().mkdirs(); try (InputStream in = input; FileOutputStream out = new FileOutputStream(destination)) { byte[] buffer = new byte[64 * 1024]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); out.flush(); }
        if (destination.length() == 0) throw new java.io.IOException("empty capture");
    }
    private static void deleteSource(Context context, Source source) {
        try { if (source.file != null && source.file.exists()) source.file.delete(); if (source.uri != null) context.getContentResolver().delete(source.uri, null, null); } catch (Exception ignored) { }
    }
    private static String displayName(Context context, Uri uri) { try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) return c.getString(0); } catch (Exception ignored) { } return "capture-" + System.currentTimeMillis() + ".jpg"; }
    private static final class Source { final File file; final Uri uri; final String displayName; final String mimeType; Source(File file, Uri uri, String displayName, String mimeType) { this.file=file; this.uri=uri; this.displayName=displayName; this.mimeType=mimeType; } }
    private DarkCatCaptureCoordinator() { }
}
