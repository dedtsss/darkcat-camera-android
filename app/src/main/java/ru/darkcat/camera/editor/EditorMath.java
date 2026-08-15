package ru.darkcat.camera.editor;

/** Pure geometry used by the touch editor and covered by local unit tests. */
public final class EditorMath {
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Point inverseTransform(float x, float y, float centerX, float centerY,
                                         float rotationDegrees, float scale) {
        float dx = x - centerX;
        float dy = y - centerY;
        double radians = Math.toRadians(-rotationDegrees);
        float safeScale = Math.max(0.2f, scale);
        return new Point(
                (float) (dx * Math.cos(radians) - dy * Math.sin(radians)) / safeScale,
                (float) (dx * Math.sin(radians) + dy * Math.cos(radians)) / safeScale);
    }

    /** Smallest signed rotation from initial to current; avoids a 360-degree jump at +/-pi. */
    public static float angleDeltaDegrees(float currentRadians, float initialRadians) {
        float delta = currentRadians - initialRadians;
        while (delta > Math.PI) delta -= (float) (Math.PI * 2d);
        while (delta < -Math.PI) delta += (float) (Math.PI * 2d);
        return (float) Math.toDegrees(delta);
    }

    public static float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0001f) return (float) Math.hypot(px - x1, py - y1);
        float t = clamp(((px - x1) * dx + (py - y1) * dy) / lengthSquared, 0f, 1f);
        return (float) Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    public static final class Point {
        public final float x;
        public final float y;
        Point(float x, float y) { this.x = x; this.y = y; }
    }

    private EditorMath() { }
}
