package ru.darkcat.camera.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaModelsTest {
    @Test
    fun metadataRoundTripKeepsCrmContextAndLocation() {
        val source = CaptureMetadata(
            originalCaptureTimestamp = 1234L,
            latitude = 55.75,
            longitude = 37.61,
            accuracyMeters = 4.5f,
            context = CaptureContext(crmObjectId = "asset-7", inspectionId = "inspection-3", customTags = listOf("roof", "leak")),
            stampEnabled = true,
            stampText = "north wall",
        )
        val restored = CaptureMetadataCodec.decode(CaptureMetadataCodec.encode(source))
        assertEquals(source, restored)
    }
}
