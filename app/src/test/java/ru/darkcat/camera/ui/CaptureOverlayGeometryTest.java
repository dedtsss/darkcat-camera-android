package ru.darkcat.camera.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CaptureOverlayGeometryTest {
    @Test public void centerCropMapsCaptureCenterToViewportCenter() {
        CaptureOverlayGeometry geometry = new CaptureOverlayGeometry(10, 20, 1000, 500, 4000, 3000);
        CaptureOverlayGeometry.Point center = geometry.mapNormalized(.5f, .5f);
        assertEquals(510f, center.x, .001f);
        assertEquals(270f, center.y, .001f);
    }

    @Test public void aspectCropIsAppliedToOutputCoordinates() {
        CaptureOverlayGeometry geometry = new CaptureOverlayGeometry(0, 0, 100, 100, 200, 100);
        CaptureOverlayGeometry.Point left = geometry.mapNormalized(0f, .5f);
        CaptureOverlayGeometry.Point right = geometry.mapNormalized(1f, .5f);
        assertEquals(-50f, left.x, .001f);
        assertEquals(150f, right.x, .001f);
        assertEquals(50f, left.y, .001f);
    }

    @Test public void fittedOutputExcludesLetterboxBars() {
        CaptureOverlayGeometry.Frame frame = CaptureOverlayGeometry.fitOutputInViewport(1000, 1000, 4, 3);
        assertEquals(0f, frame.left, .001f);
        assertEquals(125f, frame.top, .001f);
        assertEquals(1000f, frame.width, .001f);
        assertEquals(750f, frame.height, .001f);
    }
}
