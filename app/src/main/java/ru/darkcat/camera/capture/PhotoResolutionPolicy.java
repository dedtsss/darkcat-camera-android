package ru.darkcat.camera.capture;

import java.util.List;
import java.util.Locale;

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

    /** Keeps a supported explicit choice; a lens-specific missing choice falls back safely. */
    public static SizeValue chooseSupported(List<SizeValue> values, SizeValue requested, long maxPixels) {
        if (requested != null && values != null) for (SizeValue value : values) {
            if (value != null && value.width == requested.width && value.height == requested.height) return value;
        }
        return chooseDefault(values, maxPixels);
    }

    public static String label(SizeValue value) {
        if (value == null) return "Недоступно";
        long wide = Math.max(value.width, value.height);
        long tall = Math.min(value.width, value.height);
        long divisor = gcd(wide, tall);
        String aspect = (wide / divisor) + ":" + (tall / divisor);
        return value.width + " × " + value.height + " · "
                + String.format(Locale.US, "%.1f", value.pixels() / 1_000_000d) + " МП · " + aspect;
    }

    private static long gcd(long left, long right) {
        while (right != 0L) { long next = left % right; left = right; right = next; }
        return Math.max(1L, left);
    }

    private PhotoResolutionPolicy() { }
}
