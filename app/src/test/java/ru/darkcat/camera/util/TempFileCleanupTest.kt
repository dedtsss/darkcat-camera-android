package ru.darkcat.camera.util

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class TempFileCleanupTest {
    @Test
    fun partialFilesCanBeRemovedByAgePolicy() {
        val directory = Files.createTempDirectory("darkcat-temp").toFile()
        val stale = File(directory, "capture.partial").apply {
            writeText("plaintext")
            setLastModified(System.currentTimeMillis() - 48 * 60 * 60 * 1000L)
        }
        assertTrue(stale.delete())
        assertTrue(!stale.exists())
    }
}
