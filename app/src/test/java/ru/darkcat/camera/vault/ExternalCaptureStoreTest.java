package ru.darkcat.camera.vault;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ExternalCaptureStoreTest {
    @Test public void externalReferenceSurvivesStoreRecreation() throws Exception {
        File directory = Files.createTempDirectory("darkcat-external").toFile();
        ExternalCaptureStore first = new ExternalCaptureStore(directory);
        ExternalCaptureStore.PendingExternalCapture written = first.markPending(
                "/storage/emulated/0/DCIM/field clip.mp4",
                "content://media/external_primary/video/media/42",
                "полевой ролик.mp4", "video/mp4", 17, 123456789L);

        List<ExternalCaptureStore.PendingExternalCapture> restored =
                new ExternalCaptureStore(directory).listPending();
        assertEquals(1, restored.size());
        ExternalCaptureStore.PendingExternalCapture pending = restored.get(0);
        assertEquals(written.id, pending.id);
        assertEquals("/storage/emulated/0/DCIM/field clip.mp4", pending.filePath);
        assertEquals("content://media/external_primary/video/media/42", pending.uri);
        assertEquals("полевой ролик.mp4", pending.displayName);
        assertEquals("video/mp4", pending.mimeType);
        assertEquals(17, pending.sequenceNumber);
        assertEquals(123456789L, pending.capturedAt);

        new ExternalCaptureStore(directory).clear(pending);
        assertTrue(new ExternalCaptureStore(directory).listPending().isEmpty());
        deleteTree(directory);
    }

    @Test public void pathOrUriAloneCanBeJournaled() throws Exception {
        File directory = Files.createTempDirectory("darkcat-external-source").toFile();
        ExternalCaptureStore store = new ExternalCaptureStore(directory);
        store.markPending("/private/source.webm", null, "../safe.webm", "video/webm", 1, 1L);
        store.markPending(null, "content://provider/video/2", null, null, 2, 2L);

        List<ExternalCaptureStore.PendingExternalCapture> restored = store.listPending();
        assertEquals(2, restored.size());
        assertEquals("safe.webm", restored.get(0).displayName);
        assertEquals("capture.mp4", restored.get(1).displayName);
        assertEquals("application/octet-stream", restored.get(1).mimeType);
        deleteTree(directory);
    }

    @Test public void partialTemporaryJournalIsIgnored() throws Exception {
        File directory = Files.createTempDirectory("darkcat-external-tmp").toFile();
        File temporary = new File(directory, ".capture.external.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write("partial".getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(new ExternalCaptureStore(directory).listPending().isEmpty());
        assertTrue(temporary.isFile());
        deleteTree(directory);
    }

    @Test public void clearingReferenceNeverDeletesExternalSource() throws Exception {
        File root = Files.createTempDirectory("darkcat-external-clear").toFile();
        File directory = new File(root, "journal");
        File source = new File(root, "source.mp4");
        try (FileOutputStream output = new FileOutputStream(source)) { output.write(1); }
        ExternalCaptureStore store = new ExternalCaptureStore(directory);
        ExternalCaptureStore.PendingExternalCapture pending = store.markPending(
                source.getAbsolutePath(), null, source.getName(), "video/mp4", 3, 3L);

        assertTrue(store.journal(pending).isFile());
        store.clear(pending);
        assertFalse(store.journal(pending).exists());
        assertTrue(source.isFile());
        deleteTree(root);
    }

    @Test(expected = java.io.IOException.class)
    public void sourceReferenceIsRequired() throws Exception {
        File directory = Files.createTempDirectory("darkcat-external-invalid").toFile();
        try {
            new ExternalCaptureStore(directory).markPending(null, " ", "x.mp4", "video/mp4", 1, 1L);
        } finally {
            deleteTree(directory);
        }
    }

    private static void deleteTree(File directory) {
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) {
            if (file.isDirectory()) deleteTree(file);
            else file.delete();
        }
        directory.delete();
    }
}
