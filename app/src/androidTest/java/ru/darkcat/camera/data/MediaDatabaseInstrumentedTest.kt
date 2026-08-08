package ru.darkcat.camera.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaDatabaseInstrumentedTest {
    @Test
    fun insertAndReadMediaRecord() {
        val database = MediaDatabase.getInstance(ApplicationProvider.getApplicationContext())
        val record = MediaRecord(
            id = "instrumented-${System.nanoTime()}",
            sequenceNumber = database.nextSequence(),
            mimeType = "image/jpeg",
            internalFileName = "instrumented-${System.nanoTime()}.dcv",
            thumbnailFileName = null,
            metadata = CaptureMetadata(System.currentTimeMillis()),
            width = 100,
            height = 80,
            durationMs = 0,
            encryptedFileSize = 128,
            checksumSha256 = "checksum",
        )
        database.insert(record)
        assertEquals(record.id, database.get(record.id)?.id)
        database.delete(record.id)
    }
}
