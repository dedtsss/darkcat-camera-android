package ru.darkcat.camera.vault;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Small durable journal for completed media which still lives outside the app-private vault.
 *
 * <p>The journal is intentionally pure Java so its crash/recreation behaviour can be tested on
 * the host JVM. A complete reference is fsynced to an ignored temporary file and atomically
 * published before the camera callback is allowed to report that DarkCat owns the source.</p>
 */
public final class ExternalCaptureStore {
    private static final String ENTRY_SUFFIX = ".external";
    private static final String VERSION = "1";
    private final File directory;

    public ExternalCaptureStore(File directory) {
        this.directory = directory;
    }

    public PendingExternalCapture markPending(String filePath, String uri, String displayName,
                                               String mimeType, int sequenceNumber,
                                               long capturedAt) throws IOException {
        String safePath = emptyToNull(filePath);
        String safeUri = emptyToNull(uri);
        if (safePath == null && safeUri == null) {
            throw new IOException("External capture source is missing");
        }
        if (sequenceNumber < 0) throw new IOException("External capture sequence is invalid");
        ensureDirectory();
        String id = UUID.randomUUID().toString();
        PendingExternalCapture pending = new PendingExternalCapture(id, safePath, safeUri,
                safeDisplayName(displayName), emptyToFallback(mimeType, "application/octet-stream"),
                sequenceNumber, capturedAt > 0 ? capturedAt : System.currentTimeMillis());
        write(pending);
        return pending;
    }

    public List<PendingExternalCapture> listPending() {
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();
        ArrayList<PendingExternalCapture> pending = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            if (!file.isFile() || !name.endsWith(ENTRY_SUFFIX) || name.endsWith(".tmp")) continue;
            try { pending.add(read(file)); }
            catch (Exception ignored) { /* retain an unreadable journal for manual recovery */ }
        }
        Collections.sort(pending, (left, right) -> Long.compare(left.capturedAt, right.capturedAt));
        return pending;
    }

    /** Deletes only the reference journal; the referenced source is owned by the caller. */
    public void clear(PendingExternalCapture pending) throws IOException {
        if (pending == null) return;
        File file = journal(pending.id);
        if (file.exists() && !file.delete()) {
            throw new IOException("Unable to clear external capture journal");
        }
    }

    File journal(PendingExternalCapture pending) {
        return journal(pending.id);
    }

    private void write(PendingExternalCapture pending) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", VERSION);
        properties.setProperty("id", pending.id);
        if (pending.filePath != null) properties.setProperty("filePath", pending.filePath);
        if (pending.uri != null) properties.setProperty("uri", pending.uri);
        properties.setProperty("displayName", pending.displayName);
        properties.setProperty("mimeType", pending.mimeType);
        properties.setProperty("sequence", Integer.toString(pending.sequenceNumber));
        properties.setProperty("capturedAt", Long.toString(pending.capturedAt));

        File destination = journal(pending.id);
        File temporary = new File(directory, "." + destination.getName() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                properties.store(output, "DarkCat external capture journal");
                output.flush();
                output.getFD().sync();
            }
            if (destination.exists() || !temporary.renameTo(destination)) {
                throw new IOException("Unable to commit external capture journal");
            }
        } finally {
            if (temporary.exists()) { //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    private PendingExternalCapture read(File file) throws IOException {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) { properties.load(input); }
        if (!VERSION.equals(properties.getProperty("version"))) {
            throw new IOException("Unsupported external capture journal");
        }
        String id = properties.getProperty("id", "");
        if (!file.getName().equals(id + ENTRY_SUFFIX)) {
            throw new IOException("External capture journal identity mismatch");
        }
        String filePath = emptyToNull(properties.getProperty("filePath"));
        String uri = emptyToNull(properties.getProperty("uri"));
        if (filePath == null && uri == null) throw new IOException("External capture source is missing");
        int sequence = Integer.parseInt(properties.getProperty("sequence", "-1"));
        if (sequence < 0) throw new IOException("External capture sequence is invalid");
        long capturedAt = Long.parseLong(properties.getProperty("capturedAt", "0"));
        if (capturedAt <= 0) throw new IOException("External capture timestamp is invalid");
        return new PendingExternalCapture(id, filePath, uri,
                safeDisplayName(properties.getProperty("displayName")),
                emptyToFallback(properties.getProperty("mimeType"), "application/octet-stream"),
                sequence, capturedAt);
    }

    private File journal(String id) {
        return new File(directory, id + ENTRY_SUFFIX);
    }

    private void ensureDirectory() throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create external capture journal directory");
        }
        if (!directory.isDirectory()) {
            throw new IOException("External capture journal path is not a directory");
        }
    }

    private static String safeDisplayName(String value) {
        String nonEmpty = emptyToFallback(value, "capture.mp4");
        return new File(nonEmpty).getName();
    }

    private static String emptyToFallback(String value, String fallback) {
        String nonEmpty = emptyToNull(value);
        return nonEmpty == null ? fallback : nonEmpty;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public static final class PendingExternalCapture {
        public final String id;
        public final String filePath;
        public final String uri;
        public final String displayName;
        public final String mimeType;
        public final int sequenceNumber;
        public final long capturedAt;

        private PendingExternalCapture(String id, String filePath, String uri, String displayName,
                                       String mimeType, int sequenceNumber, long capturedAt) {
            this.id = id;
            this.filePath = filePath;
            this.uri = uri;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.sequenceNumber = sequenceNumber;
            this.capturedAt = capturedAt;
        }
    }
}
