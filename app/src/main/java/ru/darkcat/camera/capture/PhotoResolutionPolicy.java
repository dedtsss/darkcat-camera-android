package ru.darkcat.camera.capture;

import java.util.List;

/** Capability-driven default: prefer the largest practical 4:3 still, never a device hard-code. */
public final class PhotoResolutionPolicy {
    public static final class SizeValue {
        public final int width;
        public final int height;
        public SizeValue(int width, int height) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("positive size required");
            this.width = width; this.height = height;
        }
        public long pixels() { return (long) width * height; }
        public boolean isFourByThree() {
            long wide = Math.max(width, height), tall = Math.min(width, height);
            return Math.abs(wide * 3L - tall * 4L) * 100L <= tall * 4L * 2L;
        }
    }

    public static SizeValue chooseDefault(List<SizeValue> values, long maxPixels) {
        if (values == null || values.isEmpty()) return null;
        SizeValue fourByThree = null, fallback = null, overLimit = null;
        for (SizeValue value : values) {
            if (value == null) continue;
            if (value.pixels() > maxPixels) {
                if (overLimit == null || value.pixels() < overLimit.pixels()) overLimit = value;
                continue;
            }
            if (fallback == null || value.pixels() > fallback.pixels()) fallback = value;
            if (value.isFourByThree() && (fourByThree == null || value.pixels() > fourByThree.pixels()))
                fourByThree = value;
        }
        return fourByThree != null ? fourByThree : fallback != null ? fallback : overLimit;
    }

    private PhotoResolutionPolicy() { }
}
