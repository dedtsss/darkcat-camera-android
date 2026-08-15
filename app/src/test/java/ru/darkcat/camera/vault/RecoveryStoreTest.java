package ru.darkcat.camera.vault;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class RecoveryStoreTest {
    @Test public void journalSurvivesStoreRecreationUntilExplicitClear() throws Exception {
        File directory = Files.createTempDirectory("darkcat-recovery").toFile();
        File media = new File(directory, "capture.jpg");
        try (FileOutputStream output = new FileOutputStream(media)) {
            output.write("jpeg bytes".getBytes(StandardCharsets.UTF_8));
        }
        RecoveryStore first = new RecoveryStore(directory);
        first.markPending(media, 42, "field.jpg", "image/jpeg", 123456L,
                "{\"customTags\":[\"склад\"]}", false);

        RecoveryStore recreated = new RecoveryStore(directory);
        List<RecoveryStore.PendingCapture> pending = recreated.listPending();
        assertEquals(1, recreated.pendingCount());
        assertEquals(42, pending.get(0).sequenceNumber);
        assertEquals("field.jpg", pending.get(0).displayName);
        assertEquals(123456L, pending.get(0).capturedAt);
        assertFalse(pending.get(0).legacy);

        recreated.clear(media);
        assertNull(recreated.get(media));
        // Clearing the journal never silently deletes recovery media.
        assertTrue(media.isFile());
        deleteTree(directory);
    }

    @Test public void legacyPlaintextIsDiscoveredAndNeverTtlDeleted() throws Exception {
        File directory = Files.createTempDirectory("darkcat-recovery-legacy").toFile();
        File media = new File(directory, "legacy.jpg");
        try (FileOutputStream output = new FileOutputStream(media)) { output.write(1); }

        RecoveryStore.PendingCapture pending = new RecoveryStore(directory).listPending().get(0);
        assertTrue(pending.legacy);
        assertEquals(-1, pending.sequenceNumber);
        assertTrue(media.exists());
        deleteTree(directory);
    }

    @Test public void partialTemporaryFileIsNeverImportedAsCapture() throws Exception {
        File directory = Files.createTempDirectory("darkcat-recovery-tmp").toFile();
        try (FileOutputStream output = new FileOutputStream(new File(directory, "capture.jpg.tmp"))) {
            output.write("partial".getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(new RecoveryStore(directory).listPending().isEmpty());
        deleteTree(directory);
    }

    @Test public void interruptedStampBackupIsRestoredInsteadOfDuplicated() throws Exception {
        File directory = Files.createTempDirectory("darkcat-recovery-stamp").toFile();
        File backup = new File(directory, ".capture.jpg.stamp.bak");
        try (FileOutputStream output = new FileOutputStream(backup)) {
            output.write("complete jpeg".getBytes(StandardCharsets.UTF_8));
        }

        List<RecoveryStore.PendingCapture> pending = new RecoveryStore(directory).listPending();
        assertEquals(1, pending.size());
        assertEquals("capture.jpg", pending.get(0).mediaFile.getName());
        assertFalse(backup.exists());
        deleteTree(directory);
    }

    @Test public void interruptedJournalReplacementRestoresExactOldMetadata() throws Exception {
        File directory = Files.createTempDirectory("darkcat-recovery-journal-backup").toFile();
        File media = new File(directory, "capture.jpg");
        try (FileOutputStream output = new FileOutputStream(media)) { output.write(1); }
        RecoveryStore store = new RecoveryStore(directory);
        store.markPending(media, 73, "field.jpg", "image/jpeg", 456789L,
                "{\"customTags\":[\"вход\"]}", true);

        File sidecar = new File(directory, "capture.jpg.pending");
        File backup = new File(directory, ".capture.jpg.pending.bak");
        assertTrue(sidecar.renameTo(backup)); // crash after preserving old journal, before publish

        RecoveryStore.PendingCapture recovered = new RecoveryStore(directory).listPending().get(0);
        assertEquals(73, recovered.sequenceNumber);
        assertEquals(456789L, recovered.capturedAt);
        assertEquals("{\"customTags\":[\"вход\"]}", recovered.captureContextJson);
        assertTrue(recovered.editRequested);
        assertFalse(recovered.legacy);
        assertTrue(sidecar.isFile());
        assertFalse(backup.exists());
        deleteTree(directory);
    }

    private static void deleteTree(File directory) {
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        directory.delete();
    }
}
