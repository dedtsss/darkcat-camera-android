package ru.darkcat.camera.vault;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.PhotoCaptureTicket;
import ru.darkcat.camera.data.PhotoCaptureTicketQueue;
import ru.darkcat.camera.data.SequenceAllocator;
import ru.darkcat.camera.tags.TagRepository;
import ru.darkcat.camera.ui.EditorActivity;

/** Adapter between Linked Camera's completed-media callbacks and the protected pipeline. */
public final class DarkCatCaptureCoordinator {
    // A serial executor provides bounded backpressure; successful captures are already durable on disk.
    private static final ExecutorService PIPELINE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIVE_RECOVERY = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> ACTIVE_EXTERNAL = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final PhotoCaptureTicketQueue PHOTO_TICKETS = new PhotoCaptureTicketQueue();

    /** Call exactly once from a successful PHOTO camera callback, before post-processing. */
    public static int reservePhotoSequence(Context context) {
        return DarkCatSettings.sequenceEnabled(context) ? SequenceAllocator.reservePhoto(context) : 0;
    }

    /** Canonical camera-success hook: reserve exactly once and enqueue for ImageSaver FIFO claim. */
    public static PhotoCaptureTicket enqueuePhotoCaptureSuccess(Context context) {
        return enqueuePhotoCaptureSuccess(context, System.currentTimeMillis(), null);
    }

    public static PhotoCaptureTicket enqueuePhotoCaptureSuccess(
            Context context, long capturedAt, ru.darkcat.camera.location.LocationFix captureFix) {
        return PHOTO_TICKETS.enqueue(reservePhotoSequence(context), capturedAt, captureFix);
    }

    public static PhotoCaptureTicket claimPhotoCaptureTicket() { return PHOTO_TICKETS.claim(); }

    public static boolean consumePhotoCaptureTicket(PhotoCaptureTicket expected) {
        return expected != null && PHOTO_TICKETS.claimExpected(expected.ticketId) != null;
    }

    public static PhotoCaptureTicket discardOldestPhotoCaptureTicket() { return PHOTO_TICKETS.discardOldest(); }

    public static int pendingPhotoCaptureTickets() { return PHOTO_TICKETS.size(); }

    public static int pendingExternalCaptureCount(Context context) {
        return externalCaptureStore(context).listPending().size();
    }

    /** Video has a separate counter and never advances the photo sequence. */
    public static int reserveVideoSequence(Context context) { return SequenceAllocator.reserveVideo(context); }

    /**
     * Standard secure JPEG handoff called directly from the camera callback, before ImageSaver can
     * place the only copy in an in-memory queue. Bytes are published to recovery atomically.
     */
    public static void stageCapturedJpeg(Context context, byte[] jpeg, PhotoCaptureTicket ticket,
                                         Date currentDate) throws Exception {
        if (jpeg == null || jpeg.length < 4) throw new java.io.IOException("camera JPEG is empty");
        CaptureContext base = context instanceof android.app.Activity
                ? CaptureContext.fromIntent(((android.app.Activity) context).getIntent())
                : CaptureContext.empty();
        List<String> tags = new ArrayList<>(base.customTags);
        for (String tag : new TagRepository(context).active()) if (!tags.contains(tag)) tags.add(tag);
        ru.darkcat.camera.location.LocationFix fix = ticket == null ? null : ticket.locationFix;
        CaptureContext durableContext = base.withTagsAndLocation(tags, fix);
        // A settings/sequence failure must never prevent durable storage of an already captured JPEG.
        int sequence = ticket == null ? 0 : ticket.sequenceNumber;
        long capturedAt = ticket == null
                ? (currentDate == null ? System.currentTimeMillis() : currentDate.getTime())
                : ticket.capturedAt;
        RecoveryTarget target = newRecoveryTarget(context, false,
                "capture-" + capturedAt + ".jpg", "image/jpeg", sequence, capturedAt,
                durableContext);
        writeRecoveryBytesAndAccept(context, target, jpeg);
    }

    public static boolean interceptFile(Context context, File source, boolean video) {
        if (!canIntercept(context, source != null && source.isFile() && source.length() > 0))
            return false;
        PhotoCaptureTicket ticket = video ? null : claimPhotoCaptureTicket();
        if (ticket != null) return interceptFile(context, source, false, ticket);
        int sequence = video ? reserveVideoSequence(context) : reservePhotoSequence(context);
        return interceptFile(context, source, video, sequence);
    }

    public static boolean interceptFile(Context context, File source, boolean video, int sequence) {
        return interceptFile(context, source, video, sequence, System.currentTimeMillis(), null);
    }

    public static boolean interceptFile(Context context, File source, boolean video, PhotoCaptureTicket ticket) {
        if (ticket == null) return interceptFile(context, source, video);
        return interceptFile(context, source, video, ticket.sequenceNumber, ticket.capturedAt,
                ticket.locationFix);
    }

    private static boolean interceptFile(Context context, File source, boolean video, int sequence,
                                         long capturedAt,
                                         ru.darkcat.camera.location.LocationFix captureFix) {
        if (!canIntercept(context, source != null && source.isFile() && source.length() > 0)) return false;
        try {
            stageAndDispatch(context, new Source(source, null, source.getName(), video ? "video/mp4" : "image/jpeg"),
                    video, sequence, capturedAt, captureFix);
            return true;
        } catch (Exception stagingFailure) {
            // Returning false lets upstream finish/publish its already-written source instead of
            // falsely claiming that Secure Mode owns bytes which were never staged durably.
            DarkCatSettings.set(context, "darkcat_storage_blocked", true);
            return false;
        }
    }

    public static boolean interceptUri(Context context, Uri uri, boolean video) {
        if (!canIntercept(context, uri != null)) return false;
        PhotoCaptureTicket ticket = video ? null : claimPhotoCaptureTicket();
        if (ticket != null) return interceptUri(context, uri, false, ticket);
        int sequence = video ? reserveVideoSequence(context) : reservePhotoSequence(context);
        return interceptUri(context, uri, video, sequence);
    }

    public static boolean interceptUri(Context context, Uri uri, boolean video, int sequence) {
        return interceptUri(context, uri, video, sequence, System.currentTimeMillis(), null);
    }

    public static boolean interceptUri(Context context, Uri uri, boolean video, PhotoCaptureTicket ticket) {
        if (ticket == null) return interceptUri(context, uri, video);
        return interceptUri(context, uri, video, ticket.sequenceNumber, ticket.capturedAt,
                ticket.locationFix);
    }

    private static boolean interceptUri(Context context, Uri uri, boolean video, int sequence,
                                        long capturedAt,
                                        ru.darkcat.camera.location.LocationFix captureFix) {
        if (!canIntercept(context, uri != null)) return false;
        try {
            stageAndDispatch(context, new Source(null, uri, displayName(context, uri, video), video ? "video/mp4" : "image/jpeg"),
                    video, sequence, capturedAt, captureFix);
            return true;
        } catch (Exception stagingFailure) {
            DarkCatSettings.set(context, "darkcat_storage_blocked", true);
            return false;
        }
    }

    public static boolean interceptVideoAsync(Context context, Uri uri, String filename, boolean videoCaptureIntent) {
        if (!DarkCatSettings.isSecureMode(context) || videoCaptureIntent) return false;
        Context appContext = context.getApplicationContext();
        try {
            int sequence = reserveVideoSequence(context);
            long capturedAt = System.currentTimeMillis();
            String filePath = filename == null || filename.trim().isEmpty() ? null : filename;
            String uriText = uri == null ? null : uri.toString();
            String name = filePath == null ? displayName(context, uri, true) : new File(filePath).getName();
            ExternalCaptureStore.PendingExternalCapture pending = externalCaptureStore(appContext)
                    .markPending(filePath, uriText, name, contentType(context, uri, filePath), sequence, capturedAt);
            // The reference journal is durable before this method reports ownership to upstream.
            processExternalAsync(appContext, pending);
            return true;
        } catch (Exception journalFailure) {
            // Upstream must complete/publish its source when DarkCat cannot durably remember it.
            DarkCatSettings.set(context, "darkcat_storage_blocked", true);
            return false;
        }
    }

    /**
     * Provides an app-private destination when upstream can write capture bytes directly, avoiding
     * even a transient public MediaStore plaintext. Call {@link #acceptRecoveryTarget} after close.
     */
    public static RecoveryTarget newRecoveryTarget(Context context, boolean video, String displayName,
                                                   String mimeType, int sequence, CaptureContext captureContext) {
        return newRecoveryTarget(context, video, displayName, mimeType, sequence,
                System.currentTimeMillis(), captureContext);
    }

    public static RecoveryTarget newRecoveryTarget(Context context, boolean video, String displayName,
                                                   String mimeType, PhotoCaptureTicket ticket,
                                                   CaptureContext captureContext) {
        if (ticket == null) throw new IllegalArgumentException("photo capture ticket is required");
        return newRecoveryTarget(context, video, displayName, mimeType, ticket.sequenceNumber,
                ticket.capturedAt, captureContext);
    }

    private static RecoveryTarget newRecoveryTarget(Context context, boolean video, String displayName,
                                                    String mimeType, int sequence, long capturedAt,
                                                    CaptureContext captureContext) {
        VaultRepository repository = new VaultRepository(context);
        String suffix = video ? ".mp4" : ".jpg";
        File file = new File(repository.recoveryDir(), UUID.randomUUID() + suffix);
        return new RecoveryTarget(file, sequence, displayName, mimeType, capturedAt,
                (captureContext == null ? CaptureContext.empty() : captureContext).toJson().toString(),
                false);
    }

    /** Journal and dispatch a direct app-private target after upstream has fully closed it. */
    public static void acceptRecoveryTarget(Context context, RecoveryTarget target) throws Exception {
        VaultRepository repository = new VaultRepository(context);
        RecoveryStore.PendingCapture pending = repository.recoveryStore().markPending(target.file,
                target.sequenceNumber, target.displayName, target.mimeType, target.capturedAt,
                target.captureContextJson, target.editRequested);
        dispatch(context, pending);
    }

    /** Writes a complete in-memory capture through an ignored temporary file and atomic rename. */
    public static void writeRecoveryBytesAndAccept(Context context, RecoveryTarget target,
                                                   byte[] bytes) throws Exception {
        if (target == null || bytes == null || bytes.length == 0)
            throw new java.io.IOException("recovery bytes are empty");
        File parent = target.file.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs()))
            throw new java.io.IOException("unable to create recovery directory");
        File temporary = new File(parent, target.file.getName() + ".tmp");
        boolean retriedAfterReserve = false;
        while (true) {
            try {
                writeBytesAtomically(temporary, target.file, bytes);
                break;
            } catch (java.io.IOException firstFailure) {
                if (retriedAfterReserve || !RecoveryCapacity.releaseReserve(context)) throw firstFailure;
                retriedAfterReserve = true;
            }
        }
        try {
            acceptRecoveryTarget(context, target);
        } catch (Exception journalFailure) {
            // A full final file is already restart-discoverable as legacy recovery. Freeing the
            // reserve and retrying still gives the sidecar its exact sequence/location metadata.
            if (!retriedAfterReserve && RecoveryCapacity.releaseReserve(context))
                acceptRecoveryTarget(context, target);
            else throw journalFailure;
        }
    }

    private static void writeBytesAtomically(File temporary, File destination, byte[] bytes)
            throws java.io.IOException {
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            if (temporary.length() != bytes.length)
                throw new java.io.IOException("incomplete recovery write");
            if (destination.exists() || !temporary.renameTo(destination))
                throw new java.io.IOException("unable to publish recovery file");
        } finally {
            if (temporary.exists()) { //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    /** Queue all non-editor recovery entries after process recreation; no plaintext is TTL-deleted. */
    public static void resumePending(Context context) {
        Context appContext = context.getApplicationContext();
        VaultRepository repository = new VaultRepository(appContext);
        Set<String> externalRecoveryPaths = new HashSet<>();
        // External references go first. If a previous run already published their private recovery
        // file, this pass removes the external source/journal before normal recovery can commit it.
        for (ExternalCaptureStore.PendingExternalCapture pending
                : externalCaptureStore(appContext).listPending()) {
            externalRecoveryPaths.add(externalRecoveryFile(repository, pending).getAbsolutePath());
            processExternalAsync(appContext, pending);
        }
        for (RecoveryStore.PendingCapture pending : repository.recoveryStore().listPending()) {
            // The external worker owns these stable paths until it has deleted the public source
            // and reference journal. Avoid racing its sidecar creation as a legacy recovery entry.
            if (externalRecoveryPaths.contains(pending.mediaFile.getAbsolutePath())) continue;
            if (pending.editRequested) continue;
            RecoveryStore.PendingCapture journaled = ensureJournaled(appContext, repository, pending);
            processAsync(appContext, journaled);
        }
        PIPELINE_EXECUTOR.execute(() -> {
            try { RecoveryCapacity.ensureReserve(appContext); }
            catch (Exception ignored) { /* capture preflight remains conservative without it */ }
        });
    }

    /** Resolves an EDIT recovery entry without discarding it or silently bypassing the user. */
    public static void finalizeRecoveryWithoutEditing(Context context, File mediaFile) throws Exception {
        VaultRepository repository = new VaultRepository(context);
        RecoveryStore.PendingCapture pending = repository.recoveryStore().get(mediaFile);
        if (pending == null) throw new java.io.IOException("recovery journal missing");
        RecoveryStore.PendingCapture ready = repository.recoveryStore().markPending(
                pending.mediaFile, pending.sequenceNumber, pending.displayName, pending.mimeType,
                pending.capturedAt, pending.captureContextJson, false);
        processAsync(context.getApplicationContext(), ready);
    }

    public static void finalizeEdited(Context context, String recoveryPath, String displayName,
                                      String mimeType, CaptureContext captureContext) throws Exception {
        File file = new File(recoveryPath);
        if (!file.isFile()) throw new java.io.IOException("edit recovery file missing");
        VaultRepository repository = new VaultRepository(context);
        RecoveryStore.PendingCapture pending = repository.recoveryStore().get(file);
        int sequence = pending == null
                ? (mimeType != null && mimeType.startsWith("video/") ? reserveVideoSequence(context) : reservePhotoSequence(context))
                : pending.sequenceNumber;
        long capturedAt = pending == null ? file.lastModified() : pending.capturedAt;
        CaptureContext durableContext = pending == null ? captureContext : parseContext(pending.captureContextJson);
        ImageStamper.stamp(file, context, sequence, durableContext, capturedAt);
        repository.commit(file, displayName, mimeType, durableContext,
                DarkCatSettings.CROSSHAIR_STAMP.equals(DarkCatSettings.crosshair(context)), sequence, capturedAt);
    }

    private static boolean canIntercept(Context context, boolean validSource) {
        return DarkCatSettings.effectiveIsVaultMode(context) && validSource;
    }

    private static void stageAndDispatch(Context context, Source source, boolean video, int sequence,
                                         long capturedAt,
                                         ru.darkcat.camera.location.LocationFix captureFix) throws Exception {
        if (sequence < 0) throw new IllegalArgumentException("capture sequence must be reserved or disabled");
        VaultRepository repository = new VaultRepository(context);
        File recovery = new File(repository.recoveryDir(), UUID.randomUUID() + (video ? ".mp4" : ".jpg"));
        copySource(context, source, recovery);
        CaptureContext baseContext = context instanceof android.app.Activity
                ? CaptureContext.fromIntent(((android.app.Activity) context).getIntent())
                : CaptureContext.empty();
        List<String> tags = new ArrayList<>(baseContext.customTags);
        for (String tag : new TagRepository(context).active()) if (!tags.contains(tag)) tags.add(tag);
        CaptureContext captureContext = baseContext.withTagsAndLocation(tags, captureFix);
        // 0.5 makes the editor an explicit Gallery action; background capture never launches UI.
        boolean editRequested = false;
        RecoveryStore.PendingCapture pending = repository.recoveryStore().markPending(recovery, sequence,
                source.displayName, source.mimeType, capturedAt, captureContext.toJson().toString(),
                editRequested);
        // Delete public/plain source only after both private bytes and the durable journal exist.
        deleteSource(context, source);
        dispatch(context, pending);
    }

    private static void dispatch(Context context, RecoveryStore.PendingCapture pending) {
        if (pending.editRequested) {
            boolean activityVisible = context instanceof com.linkedcamera.app.MainActivity
                    && !((com.linkedcamera.app.MainActivity) context).isAppPaused();
            android.app.KeyguardManager keyguard = (android.app.KeyguardManager)
                    context.getSystemService(Context.KEYGUARD_SERVICE);
            if (!activityVisible || (keyguard != null && keyguard.isKeyguardLocked())) {
                // Never surface the editor over the real lockscreen/background. The protected
                // Vault exposes an explicit resume/save-without-edits choice after unlock.
                return;
            }
            Intent intent = new Intent(context, EditorActivity.class)
                    .putExtra(EditorActivity.EXTRA_RECOVERY_PATH, pending.mediaFile.getAbsolutePath())
                    .putExtra(EditorActivity.EXTRA_DISPLAY_NAME, pending.displayName)
                    .putExtra(EditorActivity.EXTRA_MIME, pending.mimeType)
                    .putExtra(CaptureContext.EXTRA_CONTEXT_JSON, pending.captureContextJson)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(intent); }
            catch (RuntimeException blockedBackgroundLaunch) {
                // Durable recovery already exists; leave it for the protected recovery screen.
            }
        } else {
            processAsync(context.getApplicationContext(), pending);
        }
    }

    private static void processAsync(Context context, RecoveryStore.PendingCapture pending) {
        String key = pending.mediaFile.getAbsolutePath();
        if (!ACTIVE_RECOVERY.add(key)) return;
        PIPELINE_EXECUTOR.execute(() -> {
            try { process(context, pending); }
            catch (Exception ignored) { /* journal and plaintext remain for restart/recovery UI */ }
            catch (OutOfMemoryError memoryPressure) {
                // Keep the durable journal/plaintext; a later process can retry with free memory.
            }
            finally { ACTIVE_RECOVERY.remove(key); }
        });
    }

    private static void processExternalAsync(Context context,
                                             ExternalCaptureStore.PendingExternalCapture pending) {
        if (!ACTIVE_EXTERNAL.add(pending.id)) return;
        PIPELINE_EXECUTOR.execute(() -> {
            try { processExternal(context, pending); }
            catch (Exception ignored) {
                // The original source reference and any complete private recovery copy remain.
                DarkCatSettings.set(context, "darkcat_storage_blocked", true);
            } catch (OutOfMemoryError memoryPressure) {
                // Both the source reference and any complete recovery copy remain for restart.
            } finally {
                ACTIVE_EXTERNAL.remove(pending.id);
            }
        });
    }

    /**
     * Two-phase external handoff. The stable ID makes every crash point idempotent: a complete
     * private file without a sidecar is journaled on retry, while a complete sidecar is reused.
     */
    private static void processExternal(Context context,
                                        ExternalCaptureStore.PendingExternalCapture external)
            throws Exception {
        VaultRepository repository = new VaultRepository(context);
        File recovery = externalRecoveryFile(repository, external);
        RecoveryStore.PendingCapture pending = repository.recoveryStore().get(recovery);
        Source source = new Source(
                external.filePath == null ? null : new File(external.filePath),
                external.uri == null ? null : Uri.parse(external.uri),
                external.displayName, external.mimeType);

        if (pending == null) {
            if (!recovery.isFile() || recovery.length() <= 0) {
                if (recovery.exists() && !recovery.delete()) {
                    throw new java.io.IOException("incomplete external recovery cannot be replaced");
                }
                copySource(context, source, recovery);
            }
            pending = repository.recoveryStore().markPending(recovery, external.sequenceNumber,
                    external.displayName, external.mimeType, external.capturedAt, "{}", false);
        }

        // Clear the reference only after both the app-private media+sidecar are durable and the
        // external plaintext has been deleted (or can be proven already absent after a crash).
        deleteSource(context, source);
        externalCaptureStore(context).clear(external);
        dispatch(context, pending);
    }

    private static void process(Context context, RecoveryStore.PendingCapture pending) throws Exception {
        boolean video = pending.mimeType != null && pending.mimeType.startsWith("video/");
        CaptureContext durableContext = parseContext(pending.captureContextJson);
        if (!video) ImageStamper.stamp(pending.mediaFile, context, pending.sequenceNumber,
                durableContext, pending.capturedAt);
        new VaultRepository(context).commit(pending.mediaFile, pending.displayName, pending.mimeType,
                durableContext,
                !video && DarkCatSettings.CROSSHAIR_STAMP.equals(DarkCatSettings.crosshair(context)),
                pending.sequenceNumber, pending.capturedAt);
        try { RecoveryCapacity.ensureReserve(context); }
        catch (Exception ignored) { /* next capture's StatFs gate remains authoritative */ }
    }

    private static RecoveryStore.PendingCapture ensureJournaled(Context context, VaultRepository repository,
                                                                RecoveryStore.PendingCapture pending) {
        if (!pending.legacy) return pending;
        try {
            boolean video = pending.mimeType != null && pending.mimeType.startsWith("video/");
            int sequence = video ? reserveVideoSequence(context) : reservePhotoSequence(context);
            return repository.recoveryStore().markPending(pending.mediaFile, sequence, pending.displayName,
                    pending.mimeType, pending.capturedAt, pending.captureContextJson, false);
        } catch (Exception ignored) { return pending; }
    }

    private static CaptureContext parseContext(String json) {
        try { return CaptureContext.fromJson(new JSONObject(json)); }
        catch (Exception ignored) { return CaptureContext.empty(); }
    }

    private static void copySource(Context context, Source source, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new java.io.IOException("recovery directory unavailable");
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        boolean useFile = source.file != null && source.file.isFile() && source.file.length() > 0;
        try (InputStream input = useFile ? new FileInputStream(source.file)
                : source.uri == null ? null
                : context.getContentResolver().openInputStream(source.uri)) {
            if (input == null) throw new java.io.IOException("capture source cannot be opened");
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
                out.flush();
                out.getFD().sync();
            }
            if (temporary.length() == 0) throw new java.io.IOException("empty capture");
            if (destination.exists() || !temporary.renameTo(destination))
                throw new java.io.IOException("unable to publish recovery copy");
        } finally {
            if (temporary.exists()) { //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    private static void deleteSource(Context context, Source source) throws Exception {
        boolean attempted = false;
        if (source.file != null) {
            attempted = true;
            if (source.file.exists() && !source.file.delete()) {
                throw new java.io.IOException("public capture file could not be removed");
            }
        }
        if (source.uri != null) {
            attempted = true;
            boolean deleted = false;
            try { deleted = context.getContentResolver().delete(source.uri, null, null) > 0; }
            catch (Exception error) {
                if (uriExists(context, source.uri)) throw error;
            }
            if (!deleted && uriExists(context, source.uri)) {
                throw new java.io.IOException("public capture URI could not be removed");
            }
        }
        if (!attempted) throw new java.io.IOException("public capture source is missing");
    }

    private static boolean uriExists(Context context, Uri uri) {
        try (InputStream ignored = context.getContentResolver().openInputStream(uri)) {
            return ignored != null;
        } catch (FileNotFoundException missing) {
            return false;
        } catch (Exception uncertain) {
            // Retain the journal unless absence can be established; this avoids silently leaving a
            // public plaintext behind on a provider permission or transient storage failure.
            return true;
        }
    }

    private static String displayName(Context context, Uri uri, boolean video) {
        if (uri != null) try (Cursor c = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return "capture-" + System.currentTimeMillis() + (video ? ".mp4" : ".jpg");
    }

    private static String contentType(Context context, Uri uri, String filePath) {
        if (uri != null) try {
            String type = context.getContentResolver().getType(uri);
            if (type != null && !type.trim().isEmpty()) return type;
        } catch (Exception ignored) { }
        if (filePath != null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(new File(filePath)).toString());
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (type != null && type.startsWith("video/")) return type;
        }
        return "video/mp4";
    }

    private static ExternalCaptureStore externalCaptureStore(Context context) {
        File vault = new File(context.getApplicationContext().getFilesDir(), "darkcat-vault");
        return new ExternalCaptureStore(new File(vault, "external-pending"));
    }

    private static File externalRecoveryFile(VaultRepository repository,
                                             ExternalCaptureStore.PendingExternalCapture external) {
        return new File(repository.recoveryDir(), "external-" + external.id + ".mp4");
    }

    public static final class RecoveryTarget {
        public final File file;
        public final int sequenceNumber;
        public final String displayName;
        public final String mimeType;
        public final long capturedAt;
        public final String captureContextJson;
        public final boolean editRequested;

        private RecoveryTarget(File file, int sequenceNumber, String displayName, String mimeType,
                               long capturedAt, String captureContextJson, boolean editRequested) {
            this.file = file;
            this.sequenceNumber = sequenceNumber;
            this.displayName = displayName == null ? file.getName() : new File(displayName).getName();
            this.mimeType = mimeType == null ? "application/octet-stream" : mimeType;
            this.capturedAt = capturedAt;
            this.captureContextJson = captureContextJson;
            this.editRequested = editRequested;
        }
    }

    private static final class Source {
        final File file;
        final Uri uri;
        final String displayName;
        final String mimeType;
        Source(File file, Uri uri, String displayName, String mimeType) {
            this.file = file;
            this.uri = uri;
            this.displayName = displayName;
            this.mimeType = mimeType;
        }
    }

    private DarkCatCaptureCoordinator() { }
}
