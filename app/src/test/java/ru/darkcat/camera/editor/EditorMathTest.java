package ru.darkcat.camera.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EditorMathTest {
    @Test public void inverseTransformAccountsForMoveScaleAndRotation() {
        EditorMath.Point local = EditorMath.inverseTransform(10f, 24f, 10f, 20f, 90f, 2f);
        assertEquals(2f, local.x, 0.0001f);
        assertEquals(0f, local.y, 0.0001f);
    }

    @Test public void angleDeltaDoesNotJumpAtPiBoundary() {
        float initial = (float) Math.toRadians(179d);
        float current = (float) Math.toRadians(-179d);
        assertEquals(2f, EditorMath.angleDeltaDegrees(current, initial), 0.001f);
    }

    @Test public void segmentDistanceUsesNearestEndpointOutsideSegment() {
        assertEquals(5f, EditorMath.distanceToSegment(15f, 0f, 0f, 0f, 10f, 0f), 0.0001f);
        assertEquals(4f, EditorMath.distanceToSegment(5f, 4f, 0f, 0f, 10f, 0f), 0.0001f);
    }
}
