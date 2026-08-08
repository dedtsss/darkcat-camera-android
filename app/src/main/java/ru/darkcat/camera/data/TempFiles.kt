package ru.darkcat.camera.data

import android.content.Context
import java.io.File
import java.util.UUID

object TempFiles {
    private const val TEMP_DIR = "capture-temp"

    fun directory(context: Context): File = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }

    fun create(context: Context, prefix: String, suffix: String): File =
        File(directory(context), "$prefix-${UUID.randomUUID()}$suffix")

    fun cleanupStale(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L): Int {
        val threshold = System.currentTimeMillis() - maxAgeMs
        return directory(context).listFiles().orEmpty().count { file ->
            file.isFile && file.lastModified() < threshold && file.delete()
        }
    }
}
