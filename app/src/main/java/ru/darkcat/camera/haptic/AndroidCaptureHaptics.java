package ru.darkcat.camera.haptic;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.Objects;

import ru.darkcat.camera.data.DarkCatSettings;

/** Android vibrator implementation with immediate, distinguishable field-capture feedback. */
public final class AndroidCaptureHaptics implements CaptureHaptics {
    public static final long SUCCESS_DURATION_MILLIS = 55L;
    public static final long[] FAILURE_PATTERN_MILLIS = {0L, 150L, 70L, 260L};

    private static final int SUCCESS_AMPLITUDE = 180;
    private static final int[] FAILURE_AMPLITUDES = {0, 220, 0, 255};

    private final Vibrator vibrator;
    private final Context context;

    public AndroidCaptureHaptics(Context context) {
        Objects.requireNonNull(context, "context");
        this.context = context.getApplicationContext();
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
            HapticPreset preset = HapticPreset.fromPreference(DarkCatSettings.hapticSuccess(context));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        preset.successDurationMillis,
                        vibrator.hasAmplitudeControl()
                                ? preset.successAmplitude
                                : VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrateLegacy(preset.successDurationMillis);
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
            HapticPreset preset = HapticPreset.fromPreference(DarkCatSettings.hapticFailure(context));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = vibrator.hasAmplitudeControl()
                        ? VibrationEffect.createWaveform(
                                preset.failurePatternMillis,
                                preset.failureAmplitudes,
                                -1)
                        : VibrationEffect.createWaveform(preset.failurePatternMillis, -1);
                vibrator.vibrate(effect);
            } else {
                vibrateLegacy(preset.failurePatternMillis);
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
