package ru.darkcat.camera.ui;

/**
 * Shared center-crop mapping from capture pixels to the actual preview viewport. Keeping this
 * math independent from Views lets preview and JPEG stamp tests use the same geometry contract.
 */
public final class CaptureOverlayGeometry {
    private final float viewportLeft;
    private final float viewportTop;
    private final float viewportWidth;
    private final float viewportHeight;
    private final float outputWidth;
    private final float outputHeight;
    private final float scale;
    private final float cropX;
    private final float cropY;

    public CaptureOverlayGeometry(float viewportLeft, float viewportTop, float viewportWidth,
                                  float viewportHeight, float outputWidth, float outputHeight) {
        if (!(viewportWidth > 0) || !(viewportHeight > 0) || !(outputWidth > 0) || !(outputHeight > 0))
            throw new IllegalArgumentException("viewport/output dimensions must be positive");
        this.viewportLeft = viewportLeft;
        this.viewportTop = viewportTop;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        scale = Math.max(viewportWidth / outputWidth, viewportHeight / outputHeight);
        float scaledWidth = outputWidth * scale;
        float scaledHeight = outputHeight * scale;
        cropX = (scaledWidth - viewportWidth) / 2f;
        cropY = (scaledHeight - viewportHeight) / 2f;
    }

    public Point mapOutputPixel(float x, float y) {
        return new Point(viewportLeft + x * scale - cropX,
                viewportTop + y * scale - cropY);
    }

    public Point mapNormalized(float x, float y) {
        return mapOutputPixel(x * outputWidth, y * outputHeight);
    }

    public float scale() { return scale; }
    public float cropX() { return cropX; }
    public float cropY() { return cropY; }

    public static final class Point {
        public final float x;
        public final float y;
        public Point(float x, float y) { this.x = x; this.y = y; }
    }
}
