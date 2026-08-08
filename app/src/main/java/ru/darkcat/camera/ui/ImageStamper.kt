package ru.darkcat.camera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import ru.darkcat.camera.data.CaptureMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageStamper {
    fun stamp(source: File, destination: File, metadata: CaptureMetadata, sequence: Int) {
        val decoded = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("Unable to decode image for stamp")
        val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
        if (bitmap !== decoded) decoded.recycle()
        val canvas = Canvas(bitmap)
        val text = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(metadata.originalCaptureTimestamp)))
            append("  #")
            append(sequence)
            if (metadata.latitude != null && metadata.longitude != null) {
                append("  ")
                append("%.5f, %.5f".format(Locale.US, metadata.latitude, metadata.longitude))
            }
            if (metadata.stampText.isNotBlank()) append("  ").append(metadata.stampText)
            if (metadata.tags.isNotEmpty()) append("  ").append(metadata.tags.joinToString(" "))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (bitmap.width / 42f).coerceAtLeast(22f)
            setShadowLayer(5f, 1f, 1f, Color.BLACK)
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, 20f, bitmap.height - 24f, paint)
        destination.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) { "Unable to encode stamped image" }
        }
        bitmap.recycle()
    }
}
