package ru.darkcat.camera.haptic;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.Objects;

/** Android vibrator implementation with a short success pulse and simple two-pulse failure. */
public final class AndroidCaptureHaptics implements CaptureHaptics {
    public static final long SUCCESS_DURATION_MILLIS = 35L;
    public static final long[] FAILURE_PATTERN_MILLIS = {0L, 110L, 55L, 170L};

    private static final int SUCCESS_AMPLITUDE = 96;
    private static final int[] FAILURE_AMPLITUDES = {0, 180, 0, 255};

    private final Vibrator vibrator;

    public AndroidCaptureHaptics(Context context) {
        Objects.requireNonNull(context, "context");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            @SuppressWarnings("deprecation")
            Vibrator legacy = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator = legacy;
        }
    }

    @Override
    public void signalCaptureSuccess() {
        if (!canVibrate()) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        SUCCESS_DURATION_MILLIS,
                        vibrator.hasAmplitudeControl()
                                ? SUCCESS_AMPLITUDE
                                : VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrateLegacy(SUCCESS_DURATION_MILLIS);
            }
        } catch (SecurityException ignored) {
            // Capture must never fail because haptic permission/capability changed.
        }
    }

    @Override
    public void signalCaptureFailure() {
        if (!canVibrate()) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = vibrator.hasAmplitudeControl()
                        ? VibrationEffect.createWaveform(
                                FAILURE_PATTERN_MILLIS,
                                FAILURE_AMPLITUDES,
                                -1)
                        : VibrationEffect.createWaveform(FAILURE_PATTERN_MILLIS, -1);
                vibrator.vibrate(effect);
            } else {
                vibrateLegacy(FAILURE_PATTERN_MILLIS);
            }
        } catch (SecurityException ignored) {
            // Feedback is best-effort and never changes capture/storage semantics.
        }
    }

    private boolean canVibrate() {
        return vibrator != null && vibrator.hasVibrator();
    }

    @SuppressWarnings("deprecation")
    private void vibrateLegacy(long durationMillis) {
        vibrator.vibrate(durationMillis);
    }

    @SuppressWarnings("deprecation")
    private void vibrateLegacy(long[] patternMillis) {
        vibrator.vibrate(patternMillis, -1);
    }
}
