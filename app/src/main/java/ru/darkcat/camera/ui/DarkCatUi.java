package ru.darkcat.camera.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.linkedcamera.app.MainActivity;
import com.linkedcamera.app.KeyguardUtils;
import com.linkedcamera.app.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.field.FieldModeState;
import ru.darkcat.camera.location.GpsIndicator;
import ru.darkcat.camera.location.GpsLockerService;
import ru.darkcat.camera.location.GpsState;
import ru.darkcat.camera.tags.TagRepository;
import ru.darkcat.camera.upload.UploadQueueSummary;

/** Product camera chrome layered over the retained Linked/Open Camera engine. */
public final class DarkCatUi {
    private static WeakReference<MainActivity> installedActivity = new WeakReference<>(null);

    @SuppressLint("SetTextI18n")
    public static void install(MainActivity activity) {
        installedActivity = new WeakReference<>(activity);
        FrameLayout preview = activity.findViewById(R.id.preview);
        if (preview != null) {
            preview.addView(new WatermarkView(activity), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            preview.addView(new TechnicalStampView(activity), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            preview.addView(new CrosshairView(activity), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        RelativeLayout root = activity.findViewById(R.id.main_layout);
        if (root == null) return;
        hideUpstreamChrome(activity);
        root.addOnLayoutChangeListener((view, left, topEdge, right, bottomEdge,
                                        oldLeft, oldTop, oldRight, oldBottom) -> hideUpstreamChrome(activity));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
        top.setBackgroundColor(0xb0000000);

        TextView gps = statusChip(activity);
        TextView sequence = statusChip(activity);
        TextView sync = statusChip(activity);
        TextView field = statusChip(activity);
        top.addView(gps, weighted());
        top.addView(sequence, weighted());
        top.addView(sync, weighted());
        top.addView(field, weighted());
        gps.setOnClickListener(v -> openProductSettings(activity));
        sequence.setOnClickListener(v -> openProductSettings(activity));
        sync.setOnClickListener(v -> requireUnlocked(activity,
                () -> activity.startActivity(new Intent(activity, SyncActivity.class))));
        field.setOnClickListener(v -> openProductSettings(activity));

        RelativeLayout.LayoutParams topParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        topParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        root.addView(top, topParams);

        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(activity, 5), dp(activity, 5), dp(activity, 5), dp(activity, 8));
        bottom.setBackgroundColor(0xa6000000);

        LinearLayout secondary = new LinearLayout(activity);
        secondary.setGravity(Gravity.CENTER);
        Button flash = control(activity, "Вспышка", v -> activity.clickedCycleFlash(v));
        Button tags = control(activity, "Теги", v -> requireUnlocked(activity, () -> showTags(activity)));
        Button vault = control(activity, "Хранилище", v -> requireUnlocked(activity,
                () -> activity.startActivity(new Intent(activity, VaultActivity.class))));
        Button settings = control(activity, "Настройки", v -> openProductSettings(activity));
        secondary.addView(flash, compactControl(activity));
        secondary.addView(tags, compactControl(activity));
        secondary.addView(vault, compactControl(activity));
        secondary.addView(settings, compactControl(activity));
        bottom.addView(secondary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView mainScroll = new HorizontalScrollView(activity);
        mainScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout primary = new LinearLayout(activity);
        primary.setGravity(Gravity.CENTER_VERTICAL);
        Button photoVideo = control(activity, "Видео", v -> activity.clickedSwitchVideo(v));
        Button lens = control(activity, "Линза", v -> activity.clickedSwitchMultiCamera(v));
        lens.setOnLongClickListener(v -> {
            activity.clickedSwitchCamera(v);
            return true;
        });
        Button front = control(activity, "Фронт ↔", v -> activity.clickedSwitchCamera(v));
        Button shutter = control(activity, "●", v -> activity.clickedTakePhoto(v));
        shutter.setTextSize(34f);
        shutter.setContentDescription("Снять кадр или начать и остановить видео");
        primary.addView(photoVideo, primaryControl(activity));
        primary.addView(lens, primaryControl(activity));
        primary.addView(shutter, shutterControl(activity));
        primary.addView(front, primaryControl(activity));
        mainScroll.addView(primary, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        bottom.addView(mainScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        RelativeLayout.LayoutParams bottomParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        bottomParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        root.addView(bottom, bottomParams);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            topParams.topMargin = safe.top;
            bottomParams.bottomMargin = safe.bottom;
            top.requestLayout();
            bottom.requestLayout();
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        Runnable statusUpdater = new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                hideUpstreamChrome(activity);
                GpsState gpsState = GpsLockerService.currentState(activity);
                gps.setText("GPS " + gpsState.getAccuracyLabel());
                gps.setTextColor(gpsColor(gpsState.getIndicator()));
                sequence.setText(DarkCatSettings.sequenceEnabled(activity)
                        ? "№ " + String.format(java.util.Locale.US, "%05d", DarkCatSettings.currentPhotoSequence(activity))
                        : "№ выкл.");
                UploadQueueSummary queue = DarkCatDatabase.get(activity).queueSummary();
                String queueMark = queue.errors > 0 ? " !" : queue.uploading > 0 ? " ↑" : "";
                boolean storageError = DarkCatSettings.storageBlocked(activity);
                sync.setText(storageError ? "Хранилище !" : "Облако " + queue.pending + queueMark);
                sync.setTextColor(storageError || queue.errors > 0 ? 0xffff7777 : Color.WHITE);
                field.setText(FieldModeState.isRunning() ? "Полевой: вкл." : "Полевой: выкл.");
                field.setTextColor(FieldModeState.isRunning() ? 0xff72e59c : Color.LTGRAY);
                photoVideo.setText(activity.getPreview() != null && activity.getPreview().isVideo()
                        ? "Фото" : "Видео");
                flash.setText(flashLabel(activity));
                lens.setText(lensLabel(activity));
                List<String> activeTags = new TagRepository(activity).active();
                tags.setText(activeTags.isEmpty() ? "Теги" : "Теги · " + activeTags.size());
                gps.postDelayed(this, 1_000L);
            }
        };
        gps.post(statusUpdater);
    }

    /** Opens the retained upstream settings only as an explicit Advanced action. */
    static boolean openAdvancedSettings(android.app.Activity caller) {
        MainActivity activity = installedActivity.get();
        if (activity == null || activity.isFinishing()) {
            Toast.makeText(caller, "Камера должна быть открыта для расширенных настроек", Toast.LENGTH_LONG).show();
            return false;
        }
        caller.finish();
        activity.openSettings();
        return true;
    }

    private static void openProductSettings(MainActivity activity) {
        requireUnlocked(activity, () -> activity.startActivity(
                new Intent(activity, DarkCatSettingsActivity.class)
                        .putExtra("selected_camera", selectedCamera(activity))));
    }

    private static void requireUnlocked(MainActivity activity, Runnable action) {
        KeyguardUtils.requireKeyguard(activity, action);
    }

    private static void showTags(MainActivity activity) {
        TagRepository repository = new TagRepository(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0);
        List<String> all = repository.all();
        if (all.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Сначала создайте метку. Активные метки остаются выбранными для следующих кадров.");
            empty.setPadding(0, 0, 0, dp(activity, 8));
            content.addView(empty);
        } else {
            for (String tag : all) {
                CheckBox chip = new CheckBox(activity);
                chip.setText(tag);
                chip.setChecked(repository.isActive(tag));
                chip.setOnCheckedChangeListener((button, checked) -> {
                    if (repository.isActive(tag) != checked) repository.toggle(tag);
                });
                content.addView(chip);
            }
        }
        EditText add = new EditText(activity);
        add.setHint("Новая метка или emoji");
        add.setSingleLine(true);
        content.addView(add);
        new AlertDialog.Builder(activity)
                .setTitle("Метки кадра")
                .setView(content)
                .setPositiveButton("Готово", (dialog, which) -> {
                    String value = add.getText().toString();
                    if (!value.trim().isEmpty()) {
                        if (repository.add(value)) repository.toggle(value);
                        else Toast.makeText(activity, "Такая метка уже существует", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Очистить выбранные", (dialog, which) -> repository.clearActive())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static void hideUpstreamChrome(MainActivity activity) {
        int[] ids = {
                R.id.take_photo, R.id.switch_camera, R.id.switch_multi_camera, R.id.switch_video,
                R.id.take_photo_when_video_recording, R.id.pause_video, R.id.cancel_panorama,
                R.id.gallery, R.id.settings, R.id.popup, R.id.trash, R.id.share, R.id.locker,
                R.id.exposure, R.id.exposure_lock, R.id.white_balance_lock, R.id.cycle_raw,
                R.id.store_location, R.id.text_stamp, R.id.stamp, R.id.focus_peaking,
                R.id.auto_level, R.id.cycle_flash, R.id.face_detection, R.id.audio_control,
                R.id.kraken_icon, R.id.zoom_seekbar, R.id.focus_seekbar,
                R.id.focus_bracketing_target_seekbar, R.id.sliders_container
        };
        for (int id : ids) {
            View view = activity.findViewById(id);
            if (view != null && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
        }
    }

    private static Button control(MainActivity activity, String text, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12f);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(activity, 8), dp(activity, 5), dp(activity, 8), dp(activity, 5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xbb20242a);
        background.setCornerRadius(dp(activity, 18));
        background.setStroke(dp(activity, 1), 0x667f8b99);
        button.setBackground(background);
        button.setOnClickListener(listener);
        return button;
    }

    private static TextView statusChip(MainActivity activity) {
        TextView text = new TextView(activity);
        text.setTextColor(Color.WHITE);
        text.setTextSize(11f);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        text.setPadding(dp(activity, 3), 0, dp(activity, 3), 0);
        return text;
    }

    private static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static LinearLayout.LayoutParams compactControl(MainActivity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(activity, 40), 1f);
        params.setMargins(dp(activity, 2), dp(activity, 1), dp(activity, 2), dp(activity, 3));
        return params;
    }

    private static LinearLayout.LayoutParams primaryControl(MainActivity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 78), dp(activity, 54));
        params.setMargins(dp(activity, 3), dp(activity, 2), dp(activity, 3), dp(activity, 2));
        return params;
    }

    private static LinearLayout.LayoutParams shutterControl(MainActivity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 78), dp(activity, 70));
        params.setMargins(dp(activity, 7), dp(activity, 1), dp(activity, 7), dp(activity, 1));
        return params;
    }

    private static int gpsColor(GpsIndicator indicator) {
        switch (indicator) {
            case GREEN: return 0xff72e59c;
            case YELLOW: return 0xffffd166;
            default: return 0xffff7777;
        }
    }

    private static String lensLabel(MainActivity activity) {
        try {
            int logicalId = activity.getApplicationInterface().getCameraIdPref();
            String logical = Integer.toString(logicalId);
            String physical = activity.getApplicationInterface().getCameraIdSPhysicalPref();
            CameraManager manager = (CameraManager) activity.getSystemService(android.content.Context.CAMERA_SERVICE);
            if (manager == null) return "Линза";
            CameraCharacteristics logicalCharacteristics = manager.getCameraCharacteristics(logical);
            Integer facing = logicalCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return "Фронт";
            if (physical == null) return "1×";
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return "Линза";
            Set<String> ids = logicalCharacteristics.getPhysicalCameraIds();
            ArrayList<String> sorted = new ArrayList<>(ids);
            sorted.sort(Comparator.comparingDouble(id -> focalLength(manager, id)));
            int index = sorted.indexOf(physical);
            if (index == 0 && sorted.size() > 1) return "0.5×";
            if (index == 1 || sorted.size() == 1) return "1×";
            if (index >= 2) return index + "×";
        } catch (Exception ignored) { }
        return "Линза";
    }

    private static String selectedCamera(MainActivity activity) {
        String physical = activity.getApplicationInterface().getCameraIdSPhysicalPref();
        return physical == null
                ? Integer.toString(activity.getApplicationInterface().getCameraIdPref())
                : activity.getApplicationInterface().getCameraIdPref() + "/" + physical;
    }

    private static String flashLabel(MainActivity activity) {
        String value = activity.getApplicationInterface().getFlashPref();
        if ("flash_auto".equals(value) || "flash_frontscreen_auto".equals(value)) {
            return "Вспышка: авто";
        }
        if ("flash_on".equals(value) || "flash_frontscreen_on".equals(value)) {
            return "Вспышка: вкл.";
        }
        if ("flash_torch".equals(value)) return "Фонарь";
        if ("flash_off".equals(value) || "flash_frontscreen_off".equals(value)) {
            return "Вспышка: выкл.";
        }
        return "Вспышка";
    }

    private static double focalLength(CameraManager manager, String id) {
        try {
            float[] values = manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            return values == null || values.length == 0 ? Double.MAX_VALUE : values[0];
        } catch (Exception ignored) { return Double.MAX_VALUE; }
    }

    private static int dp(MainActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private DarkCatUi() { }
}
