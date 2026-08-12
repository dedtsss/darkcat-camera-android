package ru.darkcat.camera.ui;

/** User watermark settings shared by preview and JPEG renderer. */
public final class WatermarkConfig {
    public enum Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

    public final boolean enabled;
    public final String imageUri;
    public final Position position;
    public final float sizeFraction;
    public final float opacity;
    public final boolean tiled;
    public final float tileStepFraction;
    public final float angleDegrees;

    public WatermarkConfig(boolean enabled, String imageUri, Position position, float sizeFraction,
                           float opacity, boolean tiled, float tileStepFraction, float angleDegrees) {
        if (sizeFraction <= 0 || sizeFraction > 1) throw new IllegalArgumentException("sizeFraction out of range");
        if (opacity < 0 || opacity > 1) throw new IllegalArgumentException("opacity out of range");
        if (tileStepFraction <= 0 || tileStepFraction > 1) throw new IllegalArgumentException("tileStepFraction out of range");
        this.enabled = enabled;
        this.imageUri = imageUri;
        this.position = position == null ? Position.BOTTOM_RIGHT : position;
        this.sizeFraction = sizeFraction;
        this.opacity = opacity;
        this.tiled = tiled;
        this.tileStepFraction = tileStepFraction;
        this.angleDegrees = angleDegrees;
    }

    public static WatermarkConfig disabled() {
        return new WatermarkConfig(false, null, Position.BOTTOM_RIGHT, .22f, .75f, false, .32f, 0f);
    }
}
