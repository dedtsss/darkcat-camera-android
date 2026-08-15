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

    /** The actual image rectangle inside a preview host; controls must not be drawn in its bars. */
    public static Frame fitOutputInViewport(float viewportWidth, float viewportHeight,
                                            float outputWidth, float outputHeight) {
        if (!(viewportWidth > 0) || !(viewportHeight > 0) || !(outputWidth > 0) || !(outputHeight > 0))
            throw new IllegalArgumentException("viewport/output dimensions must be positive");
        float scale = Math.min(viewportWidth / outputWidth, viewportHeight / outputHeight);
        float width = outputWidth * scale, height = outputHeight * scale;
        return new Frame((viewportWidth - width) / 2f, (viewportHeight - height) / 2f, width, height);
    }

    public static final class Point {
        public final float x;
        public final float y;
        public Point(float x, float y) { this.x = x; this.y = y; }
    }

    public static final class Frame {
        public final float left, top, width, height;
        Frame(float left, float top, float width, float height) {
            this.left = left; this.top = top; this.width = width; this.height = height;
        }
        public float right() { return left + width; }
        public float bottom() { return top + height; }
    }
}
