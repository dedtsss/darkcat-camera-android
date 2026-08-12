package ru.darkcat.camera.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic image-space watermark placement used by preview and JPEG rendering. */
public final class WatermarkLayout {
    private WatermarkLayout() { }

    public static List<Box> boxes(int canvasWidth, int canvasHeight, int bitmapWidth, int bitmapHeight,
                                  WatermarkConfig config) {
        if (canvasWidth <= 0 || canvasHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0
                || config == null || !config.enabled) return Collections.emptyList();
        float width = canvasWidth * config.sizeFraction;
        float height = width * bitmapHeight / (float) bitmapWidth;
        if (height > canvasHeight * .9f) {
            height = canvasHeight * .9f;
            width = height * bitmapWidth / (float) bitmapHeight;
        }
        if (config.tiled) {
            ArrayList<Box> result = new ArrayList<>();
            float stepX = Math.max(width, canvasWidth * config.tileStepFraction);
            float stepY = Math.max(height, canvasHeight * config.tileStepFraction);
            for (float y = 0; y < canvasHeight; y += stepY)
                for (float x = 0; x < canvasWidth; x += stepX)
                    result.add(new Box(x, y, x + width, y + height));
            return result;
        }
        float margin = Math.max(8f, Math.min(canvasWidth, canvasHeight) * .03f);
        float left = margin;
        float top = margin;
        switch (config.position) {
            case TOP_RIGHT: left = canvasWidth - width - margin; break;
            case BOTTOM_LEFT: top = canvasHeight - height - margin; break;
            case BOTTOM_RIGHT:
                left = canvasWidth - width - margin;
                top = canvasHeight - height - margin;
                break;
            case CENTER:
                left = (canvasWidth - width) / 2f;
                top = (canvasHeight - height) / 2f;
                break;
            default: break;
        }
        ArrayList<Box> result = new ArrayList<>();
        result.add(new Box(left, top, left + width, top + height));
        return result;
    }

    public static final class Box {
        public final float left, top, right, bottom;
        public Box(float left, float top, float right, float bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }
}
