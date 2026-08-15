package ru.darkcat.camera.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Durable sequence reservation performed at successful camera-capture time, independently of
 * stamping, encryption, database insertion, or upload.
 */
public final class SequenceAllocator {
    private static final String PREFS = "darkcat_sequences";
    private static final String PHOTO_NEXT = "photo_next";
    private static final String VIDEO_NEXT = "video_next";
    public static final int DEFAULT_FIRST = 1;

    public static synchronized int reservePhoto(Context context) {
        return reserve(context, PHOTO_NEXT);
    }

    public static synchronized int reserveVideo(Context context) {
        return reserve(context, VIDEO_NEXT);
    }

    public static synchronized int peekNextPhoto(Context context) {
        return preferences(context).getInt(PHOTO_NEXT, DEFAULT_FIRST);
    }

    public static synchronized void setNextPhoto(Context context, int next) {
        requirePositive(next);
        if (!preferences(context).edit().putInt(PHOTO_NEXT, next).commit()) {
            throw new IllegalStateException("Unable to persist photo sequence");
        }
    }

    public static synchronized void resetPhoto(Context context) {
        setNextPhoto(context, DEFAULT_FIRST);
    }

    static int nextValue(int current) {
        requirePositive(current);
        if (current == Integer.MAX_VALUE) throw new IllegalStateException("Photo sequence exhausted");
        return current + 1;
    }

    static int valueAfterCapture(int current, boolean cameraCaptureSucceeded) {
        requirePositive(current);
        return cameraCaptureSucceeded ? nextValue(current) : current;
    }

    private static int reserve(Context context, String key) {
        SharedPreferences preferences = preferences(context);
        int current = preferences.getInt(key, DEFAULT_FIRST);
        int next = valueAfterCapture(current, true);
        // commit(), rather than apply(), makes the reservation durable before post-capture work.
        if (!preferences.edit().putInt(key, next).commit()) {
            throw new IllegalStateException("Unable to persist capture sequence");
        }
        return current;
    }

    private static void requirePositive(int value) {
        if (value < 1) throw new IllegalArgumentException("Sequence value must be positive");
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SequenceAllocator() { }
}
