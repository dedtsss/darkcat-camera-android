package com.raulshma.lenscast.camera

import androidx.camera.core.CameraSelector
import com.raulshma.lenscast.camera.model.CameraLensInfo

object CameraLensSelection {
    fun defaultBackIndex(lenses: List<CameraLensInfo>): Int {
        val logical = lenses.indexOfFirst {
            it.lensFacing == CameraSelector.LENS_FACING_BACK && it.physicalCameraId == null
        }
        if (logical >= 0) return logical
        return lenses.indexOfFirst { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            .takeIf { it >= 0 } ?: 0
    }
}
