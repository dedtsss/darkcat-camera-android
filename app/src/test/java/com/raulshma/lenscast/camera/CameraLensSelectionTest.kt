package com.raulshma.lenscast.camera

import androidx.camera.core.CameraSelector
import com.raulshma.lenscast.camera.model.CameraLensInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraLensSelectionTest {
    private fun lens(id: String, physicalId: String?, focalLength: Float) = CameraLensInfo(
        id = id,
        label = id,
        lensFacing = CameraSelector.LENS_FACING_BACK,
        focalLength = focalLength,
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
        physicalCameraId = physicalId,
    )

    @Test fun logicalBackWinsOverFocalLengthSortedPhysicalCamera() {
        val lenses = listOf(lens("ultrawide", "2", 2.0f), lens("main", null, 4.0f))
        assertEquals(1, CameraLensSelection.defaultBackIndex(lenses))
    }

    @Test fun physicalBackIsFallbackWhenLogicalEntryIsUnavailable() {
        val lenses = listOf(lens("ultrawide", "2", 2.0f), lens("tele", "3", 6.0f))
        assertEquals(0, CameraLensSelection.defaultBackIndex(lenses))
    }
}
