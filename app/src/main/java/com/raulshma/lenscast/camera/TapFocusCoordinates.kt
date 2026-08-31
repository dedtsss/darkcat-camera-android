package com.raulshma.lenscast.camera

import kotlin.math.max

/** The UI contract is PreviewView pixels; normalized values are only for the web adapter. */
data class PreviewTapCoordinates(
    val xPx: Float,
    val yPx: Float,
    val normalizedX: Float,
    val normalizedY: Float,
)

object TapFocusCoordinates {
    fun fromPreview(xPx: Float, yPx: Float, widthPx: Float, heightPx: Float): PreviewTapCoordinates {
        val width = max(widthPx, 1f)
        val height = max(heightPx, 1f)
        return PreviewTapCoordinates(
            xPx = xPx.coerceIn(0f, width),
            yPx = yPx.coerceIn(0f, height),
            normalizedX = (xPx / width).coerceIn(0f, 1f),
            normalizedY = (yPx / height).coerceIn(0f, 1f),
        )
    }
}
