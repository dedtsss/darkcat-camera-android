package ru.darkcat.camera.upload;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WebDavVerificationTest {
    @Test public void exactRemoteLengthVerifies() {
        assertTrue(WebDavVerification.hasExactLength(200, 4096, 4096));
        assertTrue(WebDavVerification.hasExactLength(204, 0, 0));
    }

    @Test public void missingOrMismatchedLengthNeverVerifies() {
        assertFalse(WebDavVerification.hasExactLength(200, -1, 4096));
        assertFalse(WebDavVerification.hasExactLength(200, 4095, 4096));
        assertFalse(WebDavVerification.hasExactLength(200, 4096, -1));
        assertFalse(WebDavVerification.hasExactLength(404, 4096, 4096));
    }
}
