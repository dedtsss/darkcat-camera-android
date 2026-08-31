package com.raulshma.lenscast.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class TapFocusCoordinatesTest {
    @Test fun previewTapKeepsPixelsAndDerivesOverlayFractions() {
        val tap = TapFocusCoordinates.fromPreview(300f, 200f, 1000f, 800f)
        assertEquals(300f, tap.xPx, 0f)
        assertEquals(200f, tap.yPx, 0f)
        assertEquals(.3f, tap.normalizedX, 0f)
        assertEquals(.25f, tap.normalizedY, 0f)
    }

    @Test fun previewTapClampsToPreviewBounds() {
        val tap = TapFocusCoordinates.fromPreview(-5f, 900f, 1000f, 800f)
        assertEquals(0f, tap.xPx, 0f)
        assertEquals(800f, tap.yPx, 0f)
        assertEquals(0f, tap.normalizedX, 0f)
        assertEquals(1f, tap.normalizedY, 0f)
    }
}
