package ru.darkcat.camera.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class SequenceAllocatorTest {
    @Test public void successfulReservationAdvancesExactlyOnce() {
        assertEquals(2, SequenceAllocator.valueAfterCapture(1, true));
        assertEquals(428, SequenceAllocator.valueAfterCapture(427, true));
    }

    @Test public void failedCameraCaptureDoesNotAdvance() {
        assertEquals(427, SequenceAllocator.valueAfterCapture(427, false));
    }

    @Test public void invalidOrExhaustedValuesDoNotAdvance() {
        assertThrows(IllegalArgumentException.class, () -> SequenceAllocator.nextValue(0));
        assertThrows(IllegalStateException.class, () -> SequenceAllocator.nextValue(Integer.MAX_VALUE));
    }
}
