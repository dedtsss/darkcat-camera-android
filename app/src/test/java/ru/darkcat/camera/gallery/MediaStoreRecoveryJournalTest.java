package ru.darkcat.camera.gallery;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.location.LocationFix;
import ru.darkcat.camera.vault.RecoveryStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MediaStoreRecoveryJournalTest {
    @Test public void survivesRestartWithOriginalShutterContextAndStablePublicationKey() throws Exception {
        File directory = Files.createTempDirectory("darkcat-gallery-journal").toFile();
        File jpeg = new File(directory, "field.jpg");
        try (FileOutputStream output = new FileOutputStream(jpeg)) {
            output.write("jpeg".getBytes(StandardCharsets.UTF_8));
        }
        CaptureContext context = CaptureContext.empty().withTagsAndLocation(
                Arrays.asList("склад", "ночь"),
                new LocationFix(64.602931d, 30.625576d, 6.4f, 123L, 456L, "gps"));
        RecoveryStore first = new RecoveryStore(directory);
        RecoveryStore.PendingCapture written = MediaStoreRecoveryJournal.markPending(first, jpeg, 17,
                "DarkCat-Field-456.jpg", 456L, context);
        String key = MediaStoreRecoveryJournal.publicationKey(written);

        RecoveryStore.PendingCapture recovered = new RecoveryStore(directory).listPending().get(0);
        CaptureContext restored = MediaStoreRecoveryJournal.captureContext(recovered);
        assertTrue(MediaStoreRecoveryJournal.isGallery(recovered));
        assertEquals(17, recovered.sequenceNumber);
        assertEquals(456L, recovered.capturedAt);
        assertEquals(64.602931d, restored.captureLatitude, 0d);
        assertEquals(30.625576d, restored.captureLongitude, 0d);
        assertEquals(6.4f, restored.captureAccuracyMeters, 0.001f);
        assertEquals(123L, restored.captureLocationElapsedRealtimeNanos);
        assertEquals("gps", restored.captureLocationProvider);
        assertEquals(Arrays.asList("склад", "ночь"), restored.customTags);
        assertEquals(key, MediaStoreRecoveryJournal.publicationKey(recovered));

        new RecoveryStore(directory).clear(jpeg);
        jpeg.delete();
        directory.delete();
    }
}
