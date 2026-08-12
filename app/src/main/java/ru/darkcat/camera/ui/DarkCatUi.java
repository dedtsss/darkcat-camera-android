package ru.darkcat.camera.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.linkedcamera.app.MainActivity;
import com.linkedcamera.app.PreferenceKeys;
import com.linkedcamera.app.R;
import com.linkedcamera.app.cameracontroller.CameraController;
import com.linkedcamera.app.cameracontroller.CameraControllerManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.DarkCatPreferencePolicy;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.StorageMode;
import ru.darkcat.camera.field.FieldModeService;
import ru.darkcat.camera.field.FieldModeState;
import ru.darkcat.camera.gallery.GalleryItem;
import ru.darkcat.camera.gallery.GalleryRepository;
import ru.darkcat.camera.lens.LensCapabilityMapper;
import ru.darkcat.camera.lens.ZoomPresetGenerator;
import ru.darkcat.camera.location.GpsIndicator;
import ru.darkcat.camera.location.GpsLockerService;
import ru.darkcat.camera.location.GpsState;
import ru.darkcat.camera.location.LocationRepository;
import ru.darkcat.camera.tags.TagRepository;
import ru.darkcat.camera.upload.UploadQueueSummary;
import ru.darkcat.camera.vault.VaultRepository;

/** Compact camera chrome over the retained Linked/Open Camera engine. */
public final class DarkCatUi {
    private static final String ROOT_TAG = "darkcat-camera-0.5-chrome";
    private static final String WATERMARK_TAG = "darkcat-watermark";
    private static final String STAMP_TAG = "darkcat-technical-stamp";
    private static final String CROSSHAIR_TAG = "darkcat-crosshair";
    private static WeakReference<MainActivity> installedActivity = new WeakReference<>(null);

    @SuppressLint("SetTextI18n")
    public static void install(MainActivity activity) {
        installedActivity = new WeakReference<>(activity);
        DarkCatPreferencePolicy.normalize(activity);
        reapplyChrome(activity);
        RelativeLayout root = activity.findViewById(R.id.main_layout);
        if (root == null || root.findViewWithTag(ROOT_TAG) != null) return;
        root.addOnLayoutChangeListener((view, left, topEdge, right, bottomEdge, oldLeft, oldTop, oldRight, oldBottom) -> hideUpstreamChrome(activity));

        LinearLayout top = new LinearLayout(activity); top.setTag(ROOT_TAG); top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(activity, 7), dp(activity, 8), dp(activity, 7), dp(activity, 8)); top.setBackgroundColor(0xb8000000);
        TextView gps = statusChip(activity), sequence = statusChip(activity), sync = statusChip(activity), shield = statusChip(activity), field = statusChip(activity);
        top.addView(gps, weighted()); top.addView(sequence, weighted()); top.addView(sync, weighted()); top.addView(shield, weighted()); top.addView(field, weighted());
        gps.setOnClickListener(v -> showGpsQuickPanel(activity));
        sequence.setOnClickListener(v -> openGallery(activity));
        sync.setOnClickListener(v -> activity.startActivity(new Intent(activity, SyncActivity.class)));
        shield.setOnClickListener(v -> toggleStorage(activity));
        field.setOnClickListener(v -> toggleField(activity));
        RelativeLayout.LayoutParams topParams = new RelativeLayout.LayoutParams(-1, -2); topParams.addRule(RelativeLayout.ALIGN_PARENT_TOP); root.addView(top, topParams);

        LinearLayout bottom = new LinearLayout(activity); bottom.setOrientation(LinearLayout.VERTICAL); bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(activity, 7), dp(activity, 3), dp(activity, 7), dp(activity, 7)); bottom.setBackgroundColor(0xb8000000);
        LinearLayout compact = new LinearLayout(activity); compact.setGravity(Gravity.CENTER);
        Button flash = control(activity, "⚡", v -> activity.clickedCycleFlash(v));
        Button tags = control(activity, "Теги", v -> showTags(activity));
        Button photoVideo = control(activity, "Видео", v -> activity.clickedSwitchVideo(v));
        Button night = control(activity, "Ночь", v -> toggleNight(activity));
        Button settings = control(activity, "⚙", v -> openProductSettings(activity));
        compact.addView(flash, compactControl(activity)); compact.addView(tags, compactControl(activity)); compact.addView(photoVideo, compactControl(activity)); compact.addView(night, compactControl(activity)); compact.addView(settings, compactControl(activity));
        bottom.addView(compact, new LinearLayout.LayoutParams(-1, -2));
        HorizontalScrollView zoomScroll = new HorizontalScrollView(activity); zoomScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout zooms = new LinearLayout(activity); zooms.setGravity(Gravity.CENTER); zoomScroll.addView(zooms); bottom.addView(zoomScroll, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout primary = new LinearLayout(activity); primary.setGravity(Gravity.CENTER);
        ImageButton last = lastShotButton(activity); Button shutter = control(activity, "●", v -> activity.clickedTakePhoto(v)); Button lens = control(activity, "Линза", v -> showLensSelector(activity));
        shutter.setTextSize(35f); shutter.setContentDescription("Снять кадр или начать и остановить видео"); last.setOnClickListener(v -> openLatest(activity));
        primary.addView(last, lastShotParams(activity)); primary.addView(shutter, shutterControl(activity)); primary.addView(lens, primaryControl(activity));
        bottom.addView(primary, new LinearLayout.LayoutParams(-1, -2));
        RelativeLayout.LayoutParams bottomParams = new RelativeLayout.LayoutParams(-1, -2); bottomParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); root.addView(bottom, bottomParams);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topParams.topMargin = safe.top; bottomParams.bottomMargin = safe.bottom; top.requestLayout(); bottom.requestLayout(); return insets;
        });
        ViewCompat.requestApplyInsets(root);

        Runnable update = new Runnable() {
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                hideUpstreamChrome(activity); renderStatus(activity, gps, sequence, sync, shield, field, flash, tags, photoVideo, night, lens, zooms, last);
                gps.postDelayed(this, 1_000L);
            }
        };
        LocationRepository.Listener locationListener = ignored -> gps.post(update);
        LocationRepository.addListener(locationListener);
        top.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { LocationRepository.removeListener(locationListener); }
        });
        gps.post(update);
    }

    /** Safe to call after resume, rotation, or a retained engine mode/lens change. */
    public static void reapplyChrome(MainActivity activity) {
        if (activity == null) return;
        installedActivity = new WeakReference<>(activity);
        FrameLayout preview = activity.findViewById(R.id.preview);
        if (preview != null) installCaptureOverlays(activity, preview);
        hideUpstreamChrome(activity);
    }

    private static void installCaptureOverlays(MainActivity activity, FrameLayout preview) {
        if (preview.findViewWithTag(WATERMARK_TAG) == null) { WatermarkView view = new WatermarkView(activity); view.setTag(WATERMARK_TAG); preview.addView(view, cover()); }
        if (preview.findViewWithTag(STAMP_TAG) == null) { TechnicalStampView view = new TechnicalStampView(activity); view.setTag(STAMP_TAG); preview.addView(view, cover()); }
        if (preview.findViewWithTag(CROSSHAIR_TAG) == null) { CrosshairView view = new CrosshairView(activity); view.setTag(CROSSHAIR_TAG); preview.addView(view, cover()); }
    }

    @SuppressLint("SetTextI18n") // Compact product status is intentionally Russian-only in this 0.5 surface.
    private static void renderStatus(MainActivity activity, TextView gps, TextView sequence, TextView sync, TextView shield, TextView field,
                                     Button flash, Button tags, Button photoVideo, Button night, Button lens, LinearLayout zooms, ImageButton last) {
        updateOverlayOutputSize(activity);
        GpsState gpsState = GpsLockerService.currentState(activity);
        gps.setText("GPS " + gpsState.getAccuracyLabel()); gps.setTextColor(gpsColor(gpsState.getIndicator()));
        sequence.setText(DarkCatSettings.sequenceEnabled(activity) ? "№" + String.format(java.util.Locale.US, "%05d", DarkCatSettings.currentPhotoSequence(activity)) : "№ выкл.");
        UploadQueueSummary queue = DarkCatDatabase.get(activity).queueSummary(); boolean storageError = DarkCatSettings.storageBlocked(activity);
        sync.setText(storageError ? "Облако !" : "Облако " + queue.pending + (queue.errors > 0 ? " !" : queue.uploading > 0 ? " ↑" : "")); sync.setTextColor(storageError || queue.errors > 0 ? 0xffff7777 : Color.WHITE);
        boolean vault = DarkCatSettings.isVaultMode(activity); shield.setText(vault ? "Vault" : "Галерея"); shield.setTextColor(vault ? 0xff72e59c : 0xffffd166);
        field.setText(FieldModeState.isRunning() ? "Field ●" : "Field ○"); field.setTextColor(FieldModeState.isRunning() ? 0xff72e59c : Color.LTGRAY);
        photoVideo.setText(activity.getPreview() != null && activity.getPreview().isVideo() ? "Фото" : "Видео"); flash.setText(flashLabel(activity));
        boolean nightAvailable = supportsOemNight(activity); night.setText(DarkCatSettings.nightMode(activity) && nightAvailable ? "Ночь OEM" : nightAvailable ? "Ночь" : "Ночь —"); night.setEnabled(nightAvailable && !FieldModeState.isRunning());
        List<String> active = new TagRepository(activity).active(); tags.setText(active.isEmpty() ? "Теги" : "Теги " + active.size()); lens.setText(lensLabel(activity));
        refreshZooms(activity, zooms); refreshLastShot(activity, last);
    }

    private static void updateOverlayOutputSize(MainActivity activity) {
        if (activity.getPreview() == null) return;
        com.linkedcamera.app.cameracontroller.CameraController.Size output = activity.getPreview().getCurrentPictureSize();
        if (output == null) return;
        FrameLayout preview = activity.findViewById(R.id.preview); if (preview == null) return;
        View watermark = preview.findViewWithTag(WATERMARK_TAG); if (watermark instanceof WatermarkView) ((WatermarkView) watermark).setOutputSize(output.width, output.height);
        View stamp = preview.findViewWithTag(STAMP_TAG); if (stamp instanceof TechnicalStampView) ((TechnicalStampView) stamp).setOutputSize(output.width, output.height);
        View crosshair = preview.findViewWithTag(CROSSHAIR_TAG); if (crosshair instanceof CrosshairView) ((CrosshairView) crosshair).setOutputSize(output.width, output.height);
    }

    private static void refreshZooms(MainActivity activity, LinearLayout parent) {
        if (activity.getPreview() == null) return;
        int max = activity.getPreview().getMaxZoom(); float[] ratios = new float[max + 1]; for (int i = 0; i <= max; i++) ratios[i] = activity.getPreview().getZoomRatio(i);
        List<ZoomPresetGenerator.Preset> presets = ZoomPresetGenerator.generate(ratios, hasPhysicalWide(activity));
        StringBuilder signature = new StringBuilder(); for (ZoomPresetGenerator.Preset p : presets) signature.append(p.index).append(':').append(p.label()).append(';');
        if (signature.toString().equals(parent.getTag())) return;
        parent.setTag(signature.toString()); parent.removeAllViews();
        for (ZoomPresetGenerator.Preset preset : presets) {
            Button button = control(activity, preset.label(), v -> activity.getPreview().zoomTo(preset.index, false, true));
            parent.addView(button, zoomControl(activity));
        }
    }

    private static void refreshLastShot(MainActivity activity, ImageButton button) {
        List<GalleryItem> timeline = new GalleryRepository(activity).list();
        GalleryItem current = timeline.isEmpty() ? null : timeline.get(0);
        String signature = current == null ? "none" : current.source.name() + current.id;
        if (signature.equals(button.getTag())) return; button.setTag(signature); button.setImageResource(R.drawable.ic_photo_camera_white_48dp);
        if (current == null) return;
        try {
            if (current.source == GalleryItem.Source.MEDIASTORE) button.setImageURI(current.publicUri);
            else {
                VaultRepository vault = new VaultRepository(activity); java.io.File file = vault.decryptThumbnailToCache(current.vaultRecord);
                if (file != null) { Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath()); button.setImageBitmap(bitmap); vault.cleanupDecryptedCacheQuietly(file); }
            }
        } catch (Exception ignored) { }
    }

    private static void showGpsQuickPanel(MainActivity activity) {
        GpsState state = GpsLockerService.currentState(activity);
        new AlertDialog.Builder(activity).setTitle("GPS · live")
                .setMessage("Текущая точность: " + state.getAccuracyLabel() + "\nСостояние: " + state.getIssue()
                        + "\nСостояние обновляется тем же callback, что штамп и EXIF.")
                .setNegativeButton("Закрыть", null).setPositiveButton("Настройки", (d, w) -> openProductSettings(activity)).show();
    }

    private static void toggleStorage(MainActivity activity) {
        StorageMode next = DarkCatSettings.isVaultMode(activity) ? StorageMode.MEDIASTORE : StorageMode.VAULT;
        DarkCatSettings.setStorageMode(activity, next); DarkCatPreferencePolicy.normalize(activity);
        Toast.makeText(activity, next == StorageMode.VAULT ? "Vault включён" : "Сохранение в MediaStore Gallery", Toast.LENGTH_SHORT).show();
    }

    /** Applies the OEM extension only when Camera2 exposes it; never synthesizes a Night pipeline. */
    public static void reconcileNightMode(MainActivity activity) {
        if (activity == null || FieldModeState.isRunning() || !supportsOemNight(activity)) return;
        boolean requested = DarkCatSettings.nightMode(activity);
        String wanted = requested ? "preference_photo_mode_x_night" : "preference_photo_mode_std";
        android.content.SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!wanted.equals(preferences.getString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std"))) {
            preferences.edit().putString(PreferenceKeys.PhotoModePreferenceKey, wanted).apply();
            activity.updateForSettings(true, requested ? "Ночной режим OEM" : "Ночной режим выключен");
        }
    }

    private static void toggleNight(MainActivity activity) {
        if (FieldModeState.isRunning()) { Toast.makeText(activity, "Для Night сначала остановите Field Mode", Toast.LENGTH_LONG).show(); return; }
        if (!supportsOemNight(activity)) { Toast.makeText(activity, "OEM Night capability не заявлен этой камерой", Toast.LENGTH_LONG).show(); return; }
        boolean enabled = !DarkCatSettings.nightMode(activity);
        DarkCatSettings.set(activity, "darkcat_night_mode", enabled);
        // OEM Night owns its own multi-frame cadence; do not leave DarkCat in Max Speed semantics.
        if (enabled) DarkCatSettings.set(activity, "darkcat_capture_mode", DarkCatSettings.CAPTURE_SHARP);
        reconcileNightMode(activity);
        if (enabled) Toast.makeText(activity, "OEM Night: держите телефон неподвижно", Toast.LENGTH_LONG).show();
    }

    private static boolean supportsOemNight(MainActivity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && activity.getPreview() != null
                && activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT);
    }

    private static void toggleField(MainActivity activity) {
        if (FieldModeState.isRunning()) { FieldModeService.stop(activity); Toast.makeText(activity, "Field Mode выключен", Toast.LENGTH_SHORT).show(); return; }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "Нужно разрешение камеры: откройте Полевой режим в настройках", Toast.LENGTH_LONG).show(); openProductSettings(activity); return;
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "Для Field Mode нужна точная геолокация: откройте Полевой режим в настройках", Toast.LENGTH_LONG).show(); openProductSettings(activity); return;
        }
        try { FieldModeService.startFromVisibleActivity(activity); Toast.makeText(activity, "Field Mode запускается", Toast.LENGTH_SHORT).show(); }
        catch (RuntimeException error) { Toast.makeText(activity, "Не удалось запустить Field Mode", Toast.LENGTH_LONG).show(); }
    }

    private static void openLatest(MainActivity activity) {
        List<GalleryItem> all = new GalleryRepository(activity).list();
        if (all.isEmpty()) { openGallery(activity); return; }
        GalleryItem item = all.get(0); activity.startActivity(new Intent(activity, MediaViewerActivity.class)
                .putExtra(MediaViewerActivity.EXTRA_GALLERY_SOURCE, item.source.name()).putExtra(MediaViewerActivity.EXTRA_GALLERY_ID, item.id));
    }
    private static void openGallery(MainActivity activity) { activity.startActivity(new Intent(activity, GalleryActivity.class)); }
    static boolean openAdvancedSettings(android.app.Activity caller) {
        MainActivity activity = installedActivity.get(); if (activity == null || activity.isFinishing()) { Toast.makeText(caller, "Камера должна быть открыта для расширенных настроек", Toast.LENGTH_LONG).show(); return false; }
        caller.finish(); activity.openSettings(); return true;
    }
    /** The retained camera activity is the only source for current-lens capability lists. */
    public static MainActivity activeCameraActivity() {
        MainActivity activity = installedActivity.get();
        return activity == null || activity.isFinishing() || activity.isDestroyed() ? null : activity;
    }
    private static void openProductSettings(MainActivity activity) { activity.startActivity(new Intent(activity, DarkCatSettingsRootActivity.class)); }

    private static void showTags(MainActivity activity) {
        TagRepository repository = new TagRepository(activity); LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), 0);
        for (String tag : repository.all()) { CheckBox box = new CheckBox(activity); box.setText(tag); box.setChecked(repository.isActive(tag)); box.setOnCheckedChangeListener((button, checked) -> { if (repository.isActive(tag) != checked) repository.toggle(tag); }); content.addView(box); }
        EditText input = new EditText(activity); input.setHint("Новая метка или emoji"); input.setSingleLine(true); content.addView(input);
        new AlertDialog.Builder(activity).setTitle("Метки кадра").setView(content).setNegativeButton("Отмена", null)
                .setNeutralButton("Очистить", (d, w) -> repository.clearActive()).setPositiveButton("Готово", (d, w) -> { String value = input.getText().toString(); if (!value.trim().isEmpty() && repository.add(value)) repository.toggle(value); }).show();
    }

    private static void hideUpstreamChrome(MainActivity activity) {
        int[] ids = {R.id.take_photo, R.id.switch_camera, R.id.switch_multi_camera, R.id.switch_video, R.id.take_photo_when_video_recording, R.id.pause_video, R.id.cancel_panorama, R.id.gallery, R.id.settings, R.id.popup, R.id.trash, R.id.share, R.id.locker, R.id.exposure, R.id.exposure_lock, R.id.white_balance_lock, R.id.cycle_raw, R.id.store_location, R.id.text_stamp, R.id.stamp, R.id.focus_peaking, R.id.auto_level, R.id.cycle_flash, R.id.face_detection, R.id.audio_control, R.id.kraken_icon, R.id.zoom_seekbar, R.id.focus_seekbar, R.id.focus_bracketing_target_seekbar, R.id.sliders_container};
        for (int id : ids) { View view = activity.findViewById(id); if (view != null && view.getVisibility() != View.GONE) view.setVisibility(View.GONE); }
    }

    private static boolean hasPhysicalWide(MainActivity activity) {
        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE); if (manager == null) return false;
            ArrayList<Float> focal = new ArrayList<>(); for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id); Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) addFocal(focal, c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
            }
            return LensCapabilityMapper.hasUltraWide(focal);
        } catch (Exception ignored) { return false; }
    }

    /** Product-facing camera chooser: labels derive from Camera2 focal capabilities, never raw IDs. */
    private static void showLensSelector(MainActivity activity) {
        if (activity.getPreview() == null || !activity.getPreview().canSwitchCamera()) {
            Toast.makeText(activity, "Переключение объектива пока недоступно", Toast.LENGTH_SHORT).show();
            return;
        }
        int currentLogical = activity.getApplicationInterface().getCameraIdPref();
        String currentPhysical = activity.getApplicationInterface().getCameraIdSPhysicalPref();
        ArrayList<LensChoice> choices = new ArrayList<>();
        CameraControllerManager manager = activity.getPreview().getCameraControllerManager();
        Set<String> physical = activity.getPreview().getPhysicalCameras();
        if (physical != null && !physical.isEmpty()) {
            ArrayList<PhysicalLens> lenses = new ArrayList<>();
            ArrayList<Float> allFocals = new ArrayList<>();
            CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) for (String physicalId : physical) try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(physicalId);
                float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                float focal = focalLengths == null || focalLengths.length == 0 ? 0f : focalLengths[0];
                if (focalLengths != null) for (float value : focalLengths) allFocals.add(value);
                lenses.add(new PhysicalLens(physicalId, focal));
            } catch (Exception ignored) {
                lenses.add(new PhysicalLens(physicalId, 0f));
            }
            Collections.sort(lenses, new Comparator<PhysicalLens>() {
                @Override public int compare(PhysicalLens left, PhysicalLens right) {
                    return Float.compare(left.focal, right.focal);
                }
            });
            String automatic = logicalLensLabel(manager, activity, currentLogical);
            choices.add(new LensChoice("Авто · " + automatic, currentLogical, null));
            float standard = LensCapabilityMapper.standardFocal(allFocals);
            for (PhysicalLens lens : lenses) {
                String label = LensCapabilityMapper.label(lens.focal, standard, false);
                if ("Линза".equals(label)) label = "Дополнительная задняя камера";
                choices.add(new LensChoice(label, currentLogical, lens.id));
            }
        }
        int cameraCount = manager == null ? 0 : manager.getNumberOfCameras();
        for (int logical = 0; logical < cameraCount; logical++) {
            if (logical == currentLogical && physical != null && !physical.isEmpty()) continue;
            choices.add(new LensChoice(logicalLensLabel(manager, activity, logical), logical, null));
        }
        if (choices.isEmpty()) {
            Toast.makeText(activity, "Других объективов камера не заявила", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] labels = new CharSequence[choices.size()];
        int selected = 0;
        for (int index = 0; index < choices.size(); index++) {
            LensChoice choice = choices.get(index); labels[index] = choice.label;
            if (choice.logicalId == currentLogical
                    && (choice.physicalId == null ? currentPhysical == null : choice.physicalId.equals(currentPhysical))) selected = index;
        }
        new AlertDialog.Builder(activity).setTitle("Объективы")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    LensChoice choice = choices.get(which);
                    if (activity.getPreview().isOpeningCamera()) {
                        Toast.makeText(activity, "Камера ещё открывается", Toast.LENGTH_SHORT).show();
                    } else activity.userSwitchToCamera(choice.logicalId, choice.physicalId);
                    dialog.dismiss();
                }).show();
    }

    private static String logicalLensLabel(CameraControllerManager manager, MainActivity activity, int logical) {
        if (manager == null) return "Камера";
        CameraController.Facing facing = manager.getFacing(logical);
        if (facing == CameraController.Facing.FACING_FRONT) return "Фронт";
        String description = manager.getDescription(activity, logical);
        if (description == null || description.trim().isEmpty()
                || description.toLowerCase(java.util.Locale.US).contains("camera id")
                || description.matches("(?i).*\\bcamera\\s*(id\\s*)?\\d+\\b.*"))
            return logical == 0 ? "Основная" : "Дополнительная задняя камера";
        return description;
    }

    private static final class PhysicalLens {
        final String id; final float focal;
        PhysicalLens(String id, float focal) { this.id = id; this.focal = focal; }
    }
    private static final class LensChoice {
        final String label; final int logicalId; final String physicalId;
        LensChoice(String label, int logicalId, String physicalId) {
            this.label = label; this.logicalId = logicalId; this.physicalId = physicalId;
        }
    }
    private static String lensLabel(MainActivity activity) {
        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE); if (manager == null) return "Линза";
            String[] ids = manager.getCameraIdList(); int logicalIndex = activity.getApplicationInterface().getCameraIdPref();
            if (logicalIndex < 0 || logicalIndex >= ids.length) return "Линза"; CameraCharacteristics logical = manager.getCameraCharacteristics(ids[logicalIndex]);
            Integer facing = logical.get(CameraCharacteristics.LENS_FACING); if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return "Фронт";
            ArrayList<Float> all = new ArrayList<>(); for (String id : ids) { CameraCharacteristics c = manager.getCameraCharacteristics(id); Integer f = c.get(CameraCharacteristics.LENS_FACING); if (f != null && f == CameraCharacteristics.LENS_FACING_BACK) addFocal(all, c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)); }
            String physical = activity.getApplicationInterface().getCameraIdSPhysicalPref(); CameraCharacteristics selected = physical == null ? logical : manager.getCameraCharacteristics(physical);
            float[] values = selected.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS); float focal = values == null || values.length == 0 ? 0f : values[0];
            return LensCapabilityMapper.label(focal, LensCapabilityMapper.standardFocal(all), false);
        } catch (Exception ignored) { return "Линза"; }
    }
    private static void addFocal(List<Float> output, float[] values) { if (values != null) for (float value : values) output.add(value); }
    private static String flashLabel(MainActivity activity) { String value = activity.getApplicationInterface().getFlashPref(); if ("flash_auto".equals(value) || "flash_frontscreen_auto".equals(value)) return "⚡ авто"; if ("flash_on".equals(value) || "flash_frontscreen_on".equals(value)) return "⚡ вкл."; if ("flash_torch".equals(value)) return "Фонарь"; return "⚡"; }
    private static Button control(MainActivity activity, String text, View.OnClickListener listener) { Button b = new Button(activity); b.setAllCaps(false); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(12f); b.setMinWidth(0); b.setMinHeight(0); b.setPadding(dp(activity, 7), dp(activity, 4), dp(activity, 7), dp(activity, 4)); GradientDrawable background = new GradientDrawable(); background.setColor(0xbb20242a); background.setCornerRadius(dp(activity, 18)); background.setStroke(dp(activity, 1), 0x667f8b99); b.setBackground(background); b.setOnClickListener(listener); return b; }
    private static ImageButton lastShotButton(MainActivity activity) { ImageButton b = new ImageButton(activity); b.setImageResource(R.drawable.ic_photo_camera_white_48dp); b.setScaleType(ImageButton.ScaleType.CENTER_CROP); GradientDrawable background = new GradientDrawable(); background.setColor(0xbb20242a); background.setCornerRadius(dp(activity, 25)); b.setBackground(background); b.setContentDescription("Последний снимок"); return b; }
    private static TextView statusChip(MainActivity activity) { TextView t = new TextView(activity); t.setTextColor(Color.WHITE); t.setTextSize(13.5f); t.setGravity(Gravity.CENTER); t.setSingleLine(true); t.setPadding(dp(activity, 3), dp(activity, 9), dp(activity, 3), dp(activity, 9)); return t; }
    private static FrameLayout.LayoutParams cover() { return new FrameLayout.LayoutParams(-1, -1); }
    private static LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -2, 1f); }
    private static LinearLayout.LayoutParams compactControl(MainActivity activity) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(activity, 38), 1f); p.setMargins(dp(activity, 2), 0, dp(activity, 2), 0); return p; }
    private static LinearLayout.LayoutParams primaryControl(MainActivity activity) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(activity, 104), dp(activity, 62)); p.setMargins(dp(activity, 7), dp(activity, 3), dp(activity, 7), dp(activity, 2)); return p; }
    private static LinearLayout.LayoutParams shutterControl(MainActivity activity) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(activity, 92), dp(activity, 70)); p.setMargins(dp(activity, 7), dp(activity, 1), dp(activity, 7), dp(activity, 1)); return p; }
    private static LinearLayout.LayoutParams lastShotParams(MainActivity activity) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 58)); p.setMargins(dp(activity, 7), dp(activity, 3), dp(activity, 7), dp(activity, 3)); return p; }
    private static LinearLayout.LayoutParams zoomControl(MainActivity activity) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(activity, 32)); p.setMargins(dp(activity, 2), dp(activity, 1), dp(activity, 2), dp(activity, 1)); return p; }
    private static int gpsColor(GpsIndicator indicator) { return indicator == GpsIndicator.GREEN ? 0xff72e59c : indicator == GpsIndicator.YELLOW ? 0xffffd166 : 0xffff7777; }
    private static int dp(MainActivity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private DarkCatUi() { }
}
