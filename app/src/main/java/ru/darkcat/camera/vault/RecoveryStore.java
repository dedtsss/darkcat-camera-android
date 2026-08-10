package ru.darkcat.camera.vault;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Durable, app-private journal for successful captures awaiting post-processing. Entries have no
 * TTL and survive process death/reboot until a vault commit succeeds or the user resolves them.
 */
public final class RecoveryStore {
    private static final String SIDECAR_SUFFIX = ".pending";
    private static final String VERSION = "1";
    private final File directory;

    public RecoveryStore(File directory) {
        this.directory = directory;
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create recovery directory");
        }
    }

    public PendingCapture markPending(File mediaFile, int sequenceNumber, String displayName,
                                      String mimeType, long capturedAt, String captureContextJson,
                                      boolean editRequested) throws IOException {
        if (mediaFile == null || !mediaFile.isFile() || mediaFile.length() <= 0) {
            throw new IOException("Recovery media is missing or empty");
        }
        if (!directory.equals(mediaFile.getParentFile())) {
            throw new IOException("Recovery media must be app-private");
        }
        if (sequenceNumber < 0) throw new IOException("Recovery sequence is invalid");
        PendingCapture pending = new PendingCapture(mediaFile, sequenceNumber,
                safeDisplayName(displayName, mediaFile.getName()), mimeType == null ? "application/octet-stream" : mimeType,
                capturedAt > 0 ? capturedAt : System.currentTimeMillis(),
                captureContextJson == null ? "{}" : captureContextJson, editRequested);
        write(pending);
        return pending;
    }

    public PendingCapture get(File mediaFile) {
        reconcileJournalBackup(mediaFile);
        File sidecar = sidecar(mediaFile);
        if (!mediaFile.isFile() || !sidecar.isFile()) return null;
        try { return read(mediaFile, sidecar); }
        catch (Exception ignored) { return null; }
    }

    public List<PendingCapture> listPending() {
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();
        reconcileStampBackups(files);
        reconcileJournalBackups(files);
        files = directory.listFiles();
        if (files == null) return Collections.emptyList();
        ArrayList<PendingCapture> pending = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            if (!file.isFile() || name.startsWith(".") || name.endsWith(SIDECAR_SUFFIX)
                    || name.endsWith(".tmp") || name.endsWith(".stamp.bak")
                    || name.endsWith(".thumb.dcv.jpg")) continue;
            PendingCapture entry = get(file);
            // Pre-journal recovery media from 0.2 remains visible and is never TTL-deleted.
            if (entry == null) entry = PendingCapture.legacy(file);
            pending.add(entry);
        }
        Collections.sort(pending, (left, right) -> Long.compare(left.capturedAt, right.capturedAt));
        return pending;
    }

    /** Repairs the two atomic-stamp crash points without importing a backup as another capture. */
    private void reconcileStampBackups(File[] files) {
        for (File backup : files) {
            String name = backup.getName();
            if (!backup.isFile() || !name.startsWith(".") || !name.endsWith(".stamp.bak")) continue;
            String originalName = name.substring(1, name.length() - ".stamp.bak".length());
            if (originalName.isEmpty()) continue;
            File original = new File(directory, originalName);
            if (original.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                backup.delete();
            } else {
                //noinspection ResultOfMethodCallIgnored -- retry on the next scan if storage refuses.
                backup.renameTo(original);
            }
        }
    }

    /** Restores the old sidecar if replacement stopped after preserving it but before publish. */
    private void reconcileJournalBackups(File[] files) {
        for (File backup : files) {
            String name = backup.getName();
            if (!backup.isFile() || !name.startsWith(".") || !name.endsWith(SIDECAR_SUFFIX + ".bak"))
                continue;
            String destinationName = name.substring(1, name.length() - ".bak".length());
            if (destinationName.isEmpty()) continue;
            File destination = new File(directory, destinationName);
            if (destination.isFile()) {
                // The replacement was already published; the old journal is obsolete.
                //noinspection ResultOfMethodCallIgnored
                backup.delete();
            } else {
                //noinspection ResultOfMethodCallIgnored -- retry on the next read if storage refuses.
                backup.renameTo(destination);
            }
        }
    }

    private void reconcileJournalBackup(File mediaFile) {
        if (mediaFile == null) return;
        File destination = sidecar(mediaFile);
        File backup = sidecarBackup(destination);
        if (!backup.isFile()) return;
        if (destination.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
        } else {
            //noinspection ResultOfMethodCallIgnored -- the caller will retain media and retry later.
            backup.renameTo(destination);
        }
    }

    public int pendingCount() { return listPending().size(); }

    public void clear(File mediaFile) {
        File sidecar = sidecar(mediaFile);
        // A stale sidecar without its media is ignored by listPending(); never delete media here.
        if (sidecar.exists()) { //noinspection ResultOfMethodCallIgnored
            sidecar.delete();
        }
    }

    private void write(PendingCapture pending) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", VERSION);
        properties.setProperty("sequence", Integer.toString(pending.sequenceNumber));
        properties.setProperty("displayName", pending.displayName);
        properties.setProperty("mimeType", pending.mimeType);
        properties.setProperty("capturedAt", Long.toString(pending.capturedAt));
        properties.setProperty("captureContext", pending.captureContextJson);
        properties.setProperty("editRequested", Boolean.toString(pending.editRequested));
        File destination = sidecar(pending.mediaFile);
        File temporary = new File(directory, destination.getName() + ".tmp");
        File backup = sidecarBackup(destination);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                properties.store(output, "DarkCat recovery journal");
                output.flush();
                output.getFD().sync();
            }
            reconcileJournalBackup(pending.mediaFile);
            boolean replacing = destination.isFile();
            if (replacing && !destination.renameTo(backup)) {
                throw new IOException("Unable to preserve recovery journal");
            }
            if (!temporary.renameTo(destination)) {
                if (replacing) { //noinspection ResultOfMethodCallIgnored
                    backup.renameTo(destination);
                }
                throw new IOException("Unable to commit recovery journal");
            }
            //noinspection ResultOfMethodCallIgnored -- a surviving backup is reconciled on read.
            backup.delete();
        } finally {
            if (temporary.exists()) { //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    private PendingCapture read(File mediaFile, File sidecar) throws IOException {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(sidecar)) { properties.load(input); }
        if (!VERSION.equals(properties.getProperty("version"))) throw new IOException("Unsupported recovery journal");
        int sequence = Integer.parseInt(properties.getProperty("sequence", "0"));
        if (sequence < 0) throw new IOException("Invalid recovery sequence");
        long capturedAt = Long.parseLong(properties.getProperty("capturedAt", "0"));
        return new PendingCapture(mediaFile, sequence,
                safeDisplayName(properties.getProperty("displayName"), mediaFile.getName()),
                properties.getProperty("mimeType", "application/octet-stream"), capturedAt,
                properties.getProperty("captureContext", "{}"),
                Boolean.parseBoolean(properties.getProperty("editRequested", "false")));
    }

    private static File sidecar(File mediaFile) {
        return new File(mediaFile.getParentFile(), mediaFile.getName() + SIDECAR_SUFFIX);
    }

    private static File sidecarBackup(File destination) {
        return new File(destination.getParentFile(), "." + destination.getName() + ".bak");
    }

    private static String safeDisplayName(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return new File(value).getName();
    }

    public static final class PendingCapture {
        public final File mediaFile;
        public final int sequenceNumber;
        public final String displayName;
        public final String mimeType;
        public final long capturedAt;
        public final String captureContextJson;
        public final boolean editRequested;
        public final boolean legacy;

        private PendingCapture(File mediaFile, int sequenceNumber, String displayName, String mimeType,
                               long capturedAt, String captureContextJson, boolean editRequested) {
            this(mediaFile, sequenceNumber, displayName, mimeType, capturedAt, captureContextJson,
                    editRequested, false);
        }

        private PendingCapture(File mediaFile, int sequenceNumber, String displayName, String mimeType,
                               long capturedAt, String captureContextJson, boolean editRequested,
                               boolean legacy) {
            this.mediaFile = mediaFile;
            this.sequenceNumber = sequenceNumber;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.capturedAt = capturedAt;
            this.captureContextJson = captureContextJson;
            this.editRequested = editRequested;
            this.legacy = legacy;
        }

        private static PendingCapture legacy(File file) {
            String mime = file.getName().toLowerCase(java.util.Locale.US).endsWith(".mp4") ? "video/mp4" : "image/jpeg";
            return new PendingCapture(file, -1, file.getName(), mime, file.lastModified(), "{}", false, true);
        }
    }
}
