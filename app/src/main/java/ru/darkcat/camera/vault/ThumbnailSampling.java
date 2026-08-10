package ru.darkcat.camera.vault;

/** Pure thumbnail sizing policy, separated so it can be tested without Android graphics. */
final class ThumbnailSampling {
    static int inSampleSize(int width, int height, int maximumDimension) {
        if (width <= 0 || height <= 0 || maximumDimension <= 0) return 1;
        int sample = 1;
        while ((width / (sample * 2)) >= maximumDimension
                || (height / (sample * 2)) >= maximumDimension) {
            sample *= 2;
        }
        return sample;
    }

    private ThumbnailSampling() { }
}
