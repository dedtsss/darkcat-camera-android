package ru.darkcat.camera.capture;

/** Cheap variance-of-Laplacian focus metric over a downscaled Y plane. */
public final class SharpnessScorer {
    public static double varianceOfLaplacian(byte[] yPlane, int width, int height) {
        if (yPlane == null || width < 3 || height < 3 || yPlane.length < width * height) return 0.0;
        int stride = Math.max(1, Math.min(width, height) / 160);
        long count = 0;
        double sum = 0.0;
        double sumSquares = 0.0;
        for (int y = 1; y < height - 1; y += stride) {
            int row = y * width;
            for (int x = 1; x < width - 1; x += stride) {
                int center = yPlane[row + x] & 0xff;
                int laplacian = (yPlane[row + x - 1] & 0xff) + (yPlane[row + x + 1] & 0xff)
                        + (yPlane[row - width + x] & 0xff) + (yPlane[row + width + x] & 0xff)
                        - 4 * center;
                sum += laplacian;
                sumSquares += (double) laplacian * laplacian;
                count++;
            }
        }
        if (count == 0) return 0.0;
        double mean = sum / count;
        return Math.max(0.0, sumSquares / count - mean * mean);
    }

    private SharpnessScorer() { }
}
