package ru.darkcat.camera.ui;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.linkedcamera.app.PreferenceKeys;
import com.linkedcamera.app.MainActivity;
import com.linkedcamera.app.R;
import com.linkedcamera.app.cameracontroller.CameraController;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ru.darkcat.camera.crypto.SecureCredentialStore;
import ru.darkcat.camera.capture.PhotoResolutionPolicy;
import ru.darkcat.camera.data.DarkCatPreferencePolicy;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.StorageMode;
import ru.darkcat.camera.field.FieldModeService;
import ru.darkcat.camera.field.FieldModeState;
import ru.darkcat.camera.haptic.AndroidCaptureHaptics;
import ru.darkcat.camera.haptic.HapticPreset;
import ru.darkcat.camera.location.GpsLockerService;
import ru.darkcat.camera.tags.TagRepository;
import ru.darkcat.camera.vault.DarkCatCaptureCoordinator;
import ru.darkcat.camera.vault.VaultRepository;

/** Russian, task-oriented settings. Upstream controls remain available only under Advanced. */
@SuppressLint({"SetTextI18n", "UseSwitchCompatOrMaterialCode"})
public final class DarkCatSettingsActivity extends Activity {
    private static final int REQUEST_CAMERA = 6101;
    private static final int REQUEST_LOCATION = 6102;
    private static final int REQUEST_NOTIFICATIONS = 6103;
    private static final int REQUEST_BACKGROUND_LOCATION = 6104;
    private static final int REQUEST_WATERMARK = 6105;

    private enum PendingStart { NONE, GPS, FIELD }

    private SharedPreferences upstream;
    private PendingStart pendingStart = PendingStart.NONE;
    private boolean binding;
    private boolean waitingBackgroundSettings;
    private boolean requestingBackgroundOnly;
    private String selectedCategory;
    private final ArrayList<SectionBoundary> sectionBoundaries = new ArrayList<>();

    private Switch pausePreview;
    private Switch recordLocation;
    private Switch gpsLocker;
    private Switch strictGps;
    private Switch sequenceEnabled;
    private Switch stampCoordinates;
    private Switch stampAccuracy;
    private Switch stampSequence;
    private Switch stampTags;
    private Switch stampCustom;
    private Switch autoUpload;
    private Switch wifiOnly;
    private Switch deleteAfterVerified;
    private Switch fieldMode;
    private Switch volumeShutter;
    private Switch videoStabilization;
    private Switch recordAudio;
    private Switch watermarkEnabled;
    private Switch watermarkTiled;
    private Switch nightMode;

    private Spinner captureMode;
    private Spinner photoResolution;
    private Spinner flash;
    private Spinner orientation;
    private Spinner storageMode;
    private Spinner hapticSuccess;
    private Spinner hapticFailure;
    private Spinner whiteBalance;
    private Spinner brightness;
    private Spinner coordinateFormat;
    private Spinner crosshair;
    private Spinner crosshairColor;
    private Spinner crosshairSize;
    private Spinner crosshairThickness;
    private Spinner provider;
    private Spinner watermarkPosition;
    private TextView watermarkPath;

    private EditText maxAccuracy;
    private EditText currentSequence;
    private EditText customStamp;
    private EditText nextcloudShare;
    private EditText nextcloudPassword;
    private EditText webdavBase;
    private EditText remoteFolder;
    private EditText webdavUser;
    private EditText webdavPassword;
    private LinearLayout nextcloudFields;
    private LinearLayout webdavFields;
    private final ArrayList<PhotoResolutionPolicy.SizeValue> photoResolutionOptions = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        selectedCategory = getIntent().getStringExtra(DarkCatSettingsRootActivity.EXTRA_CATEGORY);
        setTitle(selectedCategory == null ? "Настройки DarkCat Camera" : "Настройки · " + selectedCategory);
        upstream = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        binding = true;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(28));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);

        section(content, "СЪЁМКА", "Технический автомат без ручных ISO и выдержки");
        captureMode = spinner(content, "Режим съёмки",
                new String[]{"Максимальная скорость", "Приоритет резкости"},
                DarkCatSettings.CAPTURE_SHARP.equals(DarkCatSettings.captureMode(this)) ? 1 : 0);
        photoResolutionOptions.addAll(supportedPhotoResolutions());
        photoResolution = spinner(content, "Разрешение фото", resolutionLabels(), selectedResolutionIndex());
        if (photoResolutionOptions.isEmpty()) {
            photoResolution.setEnabled(false);
            note(content, "Список появится после открытия выбранной камеры.");
        }
        note(content, "После кадра: снимок остаётся в выбранном хранилище. Редактор открывается только из Viewer.");
        pausePreview = toggle(content, "Показывать снимок после съёмки",
                upstream.getBoolean(PreferenceKeys.PausePreviewPreferenceKey, false));
        whiteBalance = spinner(content, "Баланс белого",
                new String[]{"Авто", "Дневной свет", "Облачно", "Лампа", "Флуоресцентный"},
                valueIndex(upstream.getString(PreferenceKeys.WhiteBalancePreferenceKey, "auto"),
                        new String[]{"auto", "daylight", "cloudy-daylight", "incandescent", "fluorescent"}));
        brightness = spinner(content, "Яркость снимка",
                new String[]{"Темнее", "Обычная", "Светлее"},
                exposureIndex(upstream.getString(PreferenceKeys.ExposurePreferenceKey, "0")));
        flash = spinner(content, "Вспышка",
                new String[]{"Авто", "Выкл.", "Вкл.", "Фонарь"}, flashIndex());
        orientation = spinner(content, "Ориентация",
                new String[]{"Авто", "Портрет", "Альбомная"}, orientationIndex());
        nightMode = toggle(content, "OEM Night (если камера заявляет capability)", DarkCatSettings.nightMode(this));
        note(content, "Используется только официальный Camera2/OEM extension. При отсутствии capability остаётся обычная съёмка.");

        section(content, "ГЕОЛОКАЦИЯ", "Field Mode поднимает GPS Locker сам; отдельный Locker можно держать постоянно");
        gpsLocker = toggle(content, "Держать GPS Locker постоянно", DarkCatSettings.gpsLockerUserRequested(this));
        recordLocation = toggle(content, "Записывать координаты", upstream.getBoolean(PreferenceKeys.LocationPreferenceKey, true));
        strictGps = toggle(content, "Запрещать снимок без точного GPS", DarkCatSettings.strictGps(this));
        maxAccuracy = field(content, "Максимальная погрешность, м", false, true);
        maxAccuracy.setText(trimFloat(DarkCatSettings.maxGpsAccuracyMeters(this)));
        coordinateFormat = spinner(content, "Формат координат",
                new String[]{"Десятичные градусы", "Градусы / минуты / секунды"},
                "preference_stamp_gpsformat_dms".equals(upstream.getString(
                        PreferenceKeys.StampGPSFormatPreferenceKey, "preference_stamp_gpsformat_default")) ? 1 : 0);
        TextView age = note(content, "Свежий fix: до " + seconds(DarkCatSettings.locationFreshMs(this))
                + " с · устаревший: после " + seconds(DarkCatSettings.locationStaleMs(this)) + " с.");
        age.setTextColor(Color.DKGRAY);
        Button backgroundLocation = button("Разрешить восстановление GPS после перезапуска", v -> requestBackgroundLocationForBoot());
        content.addView(backgroundLocation, match());

        section(content, "МЕТКИ", "Индекс увеличивается только после успешного camera callback");
        sequenceEnabled = toggle(content, "Порядковый номер фото", DarkCatSettings.sequenceEnabled(this));
        currentSequence = field(content, "Следующий номер", false, true);
        currentSequence.setText(Integer.toString(DarkCatSettings.currentPhotoSequence(this)));
        LinearLayout sequenceActions = row(content);
        Button setSequence = button("Установить номер", v -> applySequenceOnly());
        Button resetSequence = button("Сбросить на 1", v -> {
            DarkCatSettings.resetPhotoSequence(this);
            currentSequence.setText("1");
        });
        sequenceActions.addView(setSequence, weight());
        sequenceActions.addView(resetSequence, weight());
        Button tags = button("Управление тегами", v -> showTagManager());
        content.addView(tags, match());

        crosshair = spinner(content, "Перекрестие",
                new String[]{"Выкл.", "Только на экране", "На экране и в снимке"},
                valueIndex(DarkCatSettings.crosshair(this), new String[]{
                        DarkCatSettings.CROSSHAIR_OFF, DarkCatSettings.CROSSHAIR_PREVIEW, DarkCatSettings.CROSSHAIR_STAMP}));
        crosshairColor = spinner(content, "Цвет перекрестия",
                new String[]{"Белый", "Зелёный", "Красный"}, colorIndex(DarkCatSettings.crosshairColor(this)));
        crosshairSize = spinner(content, "Размер перекрестия", new String[]{"Маленький", "Средний", "Большой"},
                DarkCatSettings.crosshairSize(this) <= 24 ? 0 : DarkCatSettings.crosshairSize(this) >= 52 ? 2 : 1);
        crosshairThickness = spinner(content, "Толщина перекрестия", new String[]{"Тонкая", "Средняя", "Толстая"},
                DarkCatSettings.crosshairThickness(this) <= 1 ? 0 : DarkCatSettings.crosshairThickness(this) >= 4 ? 2 : 1);
        stampCoordinates = toggle(content, "Штамп: координаты", DarkCatSettings.stampCoordinates(this));
        stampAccuracy = toggle(content, "Штамп: точность ±N м", DarkCatSettings.stampAccuracy(this));
        stampSequence = toggle(content, "Штамп: номер", DarkCatSettings.stampSequence(this));
        stampTags = toggle(content, "Штамп: активные теги", DarkCatSettings.stampTags(this));
        stampCustom = toggle(content, "Штамп: свой текст", DarkCatSettings.stampCustomText(this));
        customStamp = field(content, "Свой текст", false, false);
        customStamp.setText(DarkCatSettings.customStampText(this));

        subsection(content, "ВОДЯНОЙ ЗНАК", "Один и тот же image-space слой на preview и JPEG");
        watermarkEnabled = toggle(content, "Показывать watermark", DarkCatSettings.watermarkEnabled(this));
        watermarkTiled = toggle(content, "Повторять по всему кадру", DarkCatSettings.watermarkTiled(this));
        watermarkPosition = spinner(content, "Позиция", new String[]{"Слева сверху", "Справа сверху",
                "Слева снизу", "Справа снизу", "По центру"}, watermarkPositionIndex());
        watermarkPath = note(content, watermarkLabel());
        Button chooseWatermark = button("Выбрать PNG/WebP", v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_WATERMARK);
        });
        content.addView(chooseWatermark, match());

        section(content, "ХРАНИЛИЩЕ", "Vault шифрует оригинал; Gallery сохраняет в Pictures/DarkCat через MediaStore");
        storageMode = spinner(content, "Куда сохранять", new String[]{"Vault · зашифрованное", "Галерея · MediaStore"},
                DarkCatSettings.isVaultMode(this) ? 0 : 1);
        VaultRepository vaultRepository = new VaultRepository(this);
        TextView recovery = note(content, "Материалов на восстановлении: " + vaultRepository.recoveryPendingCount());
        Button retryRecovery = button("Продолжить обработку recovery", v -> {
            DarkCatCaptureCoordinator.resumePending(this);
            Toast.makeText(this, "Обработка recovery поставлена в очередь", Toast.LENGTH_SHORT).show();
        });
        content.addView(retryRecovery, match());

        section(content, "ГАЛЕРЕЯ", "Единая лента Vault и MediaStore без импорта чужих фото");
        Button gallery = button("Открыть галерею DarkCat", v -> startActivity(new Intent(this, GalleryActivity.class)));
        content.addView(gallery, match());
        Button points = button("Открыть точки съёмки", v -> startActivity(new Intent(this, PointGalleryActivity.class)));
        content.addView(points, match());

        section(content, "СИНХРОНИЗАЦИЯ", "Сеть и облако никогда не блокируют съёмку");
        provider = spinner(content, "Провайдер",
                new String[]{"Выключено", "Nextcloud", "WebDAV", "DarkCat API"}, providerIndex());
        nextcloudFields = group(content);
        nextcloudShare = field(nextcloudFields, "Ссылка на публичную папку Nextcloud", false, false);
        nextcloudShare.setText(DarkCatSettings.nextcloudShare(this));
        nextcloudPassword = field(nextcloudFields, "Пароль папки (пусто — не менять)", true, false);
        webdavFields = group(content);
        webdavBase = field(webdavFields, "Базовый URL WebDAV", false, false);
        webdavBase.setText(DarkCatSettings.baseUrl(this));
        remoteFolder = field(webdavFields, "Удалённая папка", false, false);
        remoteFolder.setText(DarkCatSettings.remoteFolder(this));
        webdavUser = field(webdavFields, "Имя пользователя", false, false);
        webdavUser.setText(SecureCredentialStore.get(this, "webdav_user"));
        webdavPassword = field(webdavFields, "Пароль (пусто — не менять)", true, false);
        autoUpload = toggle(content, "Автоматическая отправка", DarkCatSettings.autoUpload(this));
        wifiOnly = toggle(content, "Только Wi-Fi / сеть без тарификации", DarkCatSettings.wifiOnly(this));
        deleteAfterVerified = toggle(content, "Удалять локально только после VERIFIED", DarkCatSettings.deleteAfterVerified(this));
        note(content, "По умолчанию локальный зашифрованный оригинал сохраняется.");
        Button sync = button("Состояние синхронизации", v -> startActivity(new Intent(this, SyncActivity.class)));
        content.addView(sync, match());
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateProviderVisibility(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        section(content, "ПОЛЕВОЙ РЕЖИМ", "Штатная блокировка Android остаётся полностью активной");
        fieldMode = toggle(content, "Полевой режим", DarkCatSettings.fieldModeEnabled(this) || FieldModeState.isRunning());
        volumeShutter = toggle(content, "Volume+ как спуск в полевом режиме", DarkCatSettings.volumeShutterEnabled(this));
        LinearLayout haptics = row(content);
        Button successHaptic = button("Тест: кадр снят", v -> new AndroidCaptureHaptics(this).signalCaptureSuccess());
        Button failHaptic = button("Тест: отказ", v -> new AndroidCaptureHaptics(this).signalCaptureFailure());
        haptics.addView(successHaptic, weight());
        haptics.addView(failHaptic, weight());
        hapticSuccess = spinner(content, "Сила отклика: кадр", new String[]{"Слабый", "Средний", "Сильный"},
                HapticPreset.fromPreference(DarkCatSettings.hapticSuccess(this)).ordinal());
        hapticFailure = spinner(content, "Сила отклика: отказ", new String[]{"Слабый", "Средний", "Сильный"},
                HapticPreset.fromPreference(DarkCatSettings.hapticFailure(this)).ordinal());
        fieldMode.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            if (checked) showFieldSafety();
            else {
                pendingStart = PendingStart.NONE;
                DarkCatSettings.set(this, "darkcat_field_mode", false);
                FieldModeService.stop(this);
            }
        });
        gpsLocker.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            if (checked) beginPermissionFlow(PendingStart.GPS);
            else GpsLockerService.stopUser(this);
        });

        section(content, "ВИДЕО", "Дополнительные параметры разрешения доступны в расширенных настройках");
        videoStabilization = toggle(content, "Стабилизация видео",
                upstream.getBoolean(PreferenceKeys.VideoStabilizationPreferenceKey, false));
        recordAudio = toggle(content, "Записывать звук",
                upstream.getBoolean(PreferenceKeys.RecordAudioPreferenceKey, true));

        section(content, "РАСШИРЕННЫЕ", "Инженерные функции Linked/Open Camera");
        Button diagnostics = button("Экспорт диагностики камеры", v -> startActivity(
                new Intent(this, DiagnosticsActivity.class).putExtra("selected_camera",
                        getIntent().getStringExtra("selected_camera"))));
        content.addView(diagnostics, match());
        Button advanced = button("Расширенные настройки камеры", v -> {
            persistSettings();
            DarkCatUi.openAdvancedSettings(this);
        });
        content.addView(advanced, match());

        Button save = button("Сохранить", v -> saveAndClose());
        save.setId(R.id.cat_ui_settings_save);
        save.setTextSize(17f);
        LinearLayout.LayoutParams saveParams = match();
        saveParams.topMargin = dp(24);
        content.addView(save, saveParams);

        updateProviderVisibility();
        applyCategoryVisibility(content);
        // Save is a shared footer rather than an Advanced-only control.
        save.setVisibility(View.VISIBLE);
        setContentView(scroll);
        binding = false;
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingBackgroundSettings) {
            waitingBackgroundSettings = false;
            if (hasBackgroundLocation()) {
                if (requestingBackgroundOnly) {
                    requestingBackgroundOnly = false;
                    Toast.makeText(this, "Фоновая геолокация разрешена", Toast.LENGTH_SHORT).show();
                } else continuePermissionFlow();
            }
            else {
                if (!requestingBackgroundOnly) {
                    pendingStart = PendingStart.NONE;
                    fieldMode.setChecked(false);
                }
                requestingBackgroundOnly = false;
                Toast.makeText(this, "Доступ к геолокации «Всегда» не выдан; запущенный GPS Locker продолжит работать, но не восстановится после перезапуска", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_CAMERA || requestCode == REQUEST_LOCATION
                || requestCode == REQUEST_NOTIFICATIONS || requestCode == REQUEST_BACKGROUND_LOCATION) {
            boolean granted = results.length > 0;
            for (int result : results) granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                if (requestCode == REQUEST_BACKGROUND_LOCATION && requestingBackgroundOnly) {
                    requestingBackgroundOnly = false;
                    Toast.makeText(this, "Фоновая геолокация разрешена", Toast.LENGTH_SHORT).show();
                } else continuePermissionFlow();
            }
            else {
                if (requestCode == REQUEST_BACKGROUND_LOCATION && requestingBackgroundOnly) {
                    requestingBackgroundOnly = false;
                    Toast.makeText(this, "Автовосстановление GPS после перезапуска не включено", Toast.LENGTH_LONG).show();
                    return;
                }
                PendingStart failed = pendingStart;
                pendingStart = PendingStart.NONE;
                if (failed == PendingStart.FIELD) fieldMode.setChecked(false);
                if (failed == PendingStart.GPS) gpsLocker.setChecked(false);
                Toast.makeText(this, "Без этого разрешения режим не запускается", Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressLint("WrongConstant") // Intent returns a superset; only read/write URI grants are retained below.
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_WATERMARK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            int grantFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (grantFlags != 0) getContentResolver().takePersistableUriPermission(uri, grantFlags);
        } catch (SecurityException ignored) { }
        DarkCatSettings.set(this, "darkcat_watermark_uri", uri.toString());
        if (watermarkPath != null) watermarkPath.setText(watermarkLabel());
    }

    private void showFieldSafety() {
        new AlertDialog.Builder(this)
                .setTitle("Включить полевой режим?")
                .setMessage("• Камера продолжит работать после выключения экрана.\n"
                        + "• GPS будет постоянно активен.\n"
                        + "• Android покажет системное уведомление.\n"
                        + "• Расход батареи увеличится.\n\n"
                        + "Телефон по-прежнему защищён обычным PIN, паролем или отпечатком. "
                        + "DarkCat не обходит экран блокировки и не показывает хранилище на нём. "
                        + "Режим можно остановить в приложении или из уведомления.")
                .setPositiveButton("Понятно, включить", (dialog, which) -> {
                    beginPermissionFlow(PendingStart.FIELD);
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    binding = true;
                    fieldMode.setChecked(false);
                    binding = false;
                })
                .setOnCancelListener(dialog -> {
                    binding = true;
                    fieldMode.setChecked(false);
                    binding = false;
                })
                .show();
    }

    private void beginPermissionFlow(PendingStart start) {
        pendingStart = start;
        if (!persistSettings()) {
            pendingStart = PendingStart.NONE;
            return;
        }
        continuePermissionFlow();
    }

    private void continuePermissionFlow() {
        if (pendingStart == PendingStart.NONE) return;
        if (Build.VERSION.SDK_INT >= 23 && pendingStart == PendingStart.FIELD && !granted(Manifest.permission.CAMERA)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        startRequestedService();
    }

    private void requestBackgroundLocationForBoot() {
        if (Build.VERSION.SDK_INT < 29 || hasBackgroundLocation()) {
            Toast.makeText(this, "Фоновая геолокация уже доступна", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(this, "Сначала включите GPS Locker и выдайте точную геолокацию", Toast.LENGTH_LONG).show();
            return;
        }
        requestingBackgroundOnly = true;
        if (Build.VERSION.SDK_INT == 29) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
        } else {
            explainBackgroundLocationSettings();
        }
    }

    private void explainBackgroundLocationSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Геолокация в фоне")
                .setMessage("Для автоматического восстановления GPS Locker после перезапуска выберите для DarkCat Camera доступ к геолокации «Всегда» в системных настройках.")
                .setPositiveButton("Открыть настройки", (dialog, which) -> {
                    waitingBackgroundSettings = true;
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Не сейчас", (dialog, which) -> cancelPendingStart())
                .setOnCancelListener(dialog -> cancelPendingStart())
                .show();
    }

    private void cancelPendingStart() {
        if (requestingBackgroundOnly) {
            requestingBackgroundOnly = false;
            return;
        }
        PendingStart cancelled = pendingStart;
        pendingStart = PendingStart.NONE;
        binding = true;
        if (cancelled == PendingStart.FIELD) fieldMode.setChecked(false);
        if (cancelled == PendingStart.GPS) gpsLocker.setChecked(false);
        binding = false;
    }

    private void startRequestedService() {
        PendingStart requested = pendingStart;
        pendingStart = PendingStart.NONE;
        try {
            if (requested == PendingStart.FIELD) {
                DarkCatSettings.set(this, "darkcat_volume_shutter", volumeShutter.isChecked());
                FieldModeService.startFromVisibleActivity(this);
                Toast.makeText(this, "Полевой режим включён", Toast.LENGTH_SHORT).show();
            } else if (requested == PendingStart.GPS) {
                GpsLockerService.startForUserFromVisibleContext(this);
                Toast.makeText(this, "Постоянный GPS Locker включён", Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException error) {
            binding = true;
            if (requested == PendingStart.FIELD) fieldMode.setChecked(false);
            if (requested == PendingStart.GPS) gpsLocker.setChecked(false);
            binding = false;
            Toast.makeText(this, "Android не разрешил запуск: " + safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private void saveAndClose() {
        if (!persistSettings()) return;
        if (!fieldMode.isChecked()) {
            DarkCatSettings.set(this, "darkcat_field_mode", false);
            FieldModeService.stop(this);
        }
        if (!gpsLocker.isChecked()) {
            GpsLockerService.stopUser(this);
        } else if (fieldMode.isChecked() && !FieldModeState.isRunning()) {
            showFieldSafety();
            return;
        } else if (!DarkCatSettings.gpsLockerUserRequested(this)) {
            beginPermissionFlow(PendingStart.GPS);
            return;
        } else if (fieldMode.isChecked()) {
            // Re-deliver the visible user action so Volume+ adapter changes are applied safely.
            try { FieldModeService.startFromVisibleActivity(this); }
            catch (RuntimeException error) {
                Toast.makeText(this, "Не удалось обновить полевой режим: " + safeMessage(error),
                        Toast.LENGTH_LONG).show();
                return;
            }
        }
        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean persistSettings() {
        float accuracy;
        int nextSequence;
        try {
            accuracy = Float.parseFloat(maxAccuracy.getText().toString().trim().replace(',', '.'));
            if (!(accuracy > 0f && accuracy <= 1000f)) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            maxAccuracy.setError("Введите значение от 1 до 1000 м");
            return false;
        }
        try {
            nextSequence = Integer.parseInt(currentSequence.getText().toString().trim());
            if (nextSequence < 1) throw new NumberFormatException();
        } catch (NumberFormatException invalid) {
            currentSequence.setError("Введите номер от 1");
            return false;
        }

        String selectedProvider = new String[]{DarkCatSettings.PROVIDER_OFF,
                DarkCatSettings.PROVIDER_NEXTCLOUD, DarkCatSettings.PROVIDER_WEBDAV,
                DarkCatSettings.PROVIDER_DARKCAT_API}[provider.getSelectedItemPosition()];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                && !DarkCatSettings.PROVIDER_OFF.equals(selectedProvider)) {
            Toast.makeText(this, "Синхронизация с учётными данными требует Android 6 или новее",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Map<String, String> credentials = new LinkedHashMap<>();
            credentials.put("nextcloud_share", nextcloudShare.getText().toString().trim());
            credentials.put("webdav_user", webdavUser.getText().toString().trim());
            if (nextcloudPassword.getText().length() > 0)
                credentials.put("nextcloud_password", nextcloudPassword.getText().toString());
            if (webdavPassword.getText().length() > 0)
                credentials.put("webdav_password", webdavPassword.getText().toString());
            try {
                SecureCredentialStore.putAll(this, credentials);
            } catch (IllegalStateException keystoreFailure) {
                Toast.makeText(this, "Не удалось защитить учётные данные; настройки не изменены",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }

        DarkCatSettings.setStorageMode(this, storageMode.getSelectedItemPosition() == 0
                ? StorageMode.VAULT : StorageMode.MEDIASTORE);
        DarkCatSettings.set(this, "darkcat_capture_mode", captureMode.getSelectedItemPosition() == 1
                ? DarkCatSettings.CAPTURE_SHARP : DarkCatSettings.CAPTURE_MAX_SPEED);
        persistPhotoResolution();
        persistFlash();
        DarkCatSettings.set(this, "darkcat_night_mode", nightMode.isChecked());
        DarkCatSettings.set(this, "darkcat_gps_max_accuracy", accuracy);
        DarkCatSettings.set(this, "darkcat_strict_gps", strictGps.isChecked());
        DarkCatSettings.set(this, "darkcat_sequence_enabled", sequenceEnabled.isChecked());
        DarkCatSettings.setCurrentPhotoSequence(this, nextSequence);
        DarkCatSettings.set(this, "darkcat_crosshair", new String[]{DarkCatSettings.CROSSHAIR_OFF,
                DarkCatSettings.CROSSHAIR_PREVIEW, DarkCatSettings.CROSSHAIR_STAMP}[crosshair.getSelectedItemPosition()]);
        DarkCatSettings.set(this, "darkcat_crosshair_color", new int[]{0xffffffff, 0xff00d060, 0xffff3030}[crosshairColor.getSelectedItemPosition()]);
        DarkCatSettings.set(this, "darkcat_crosshair_size", new int[]{24, 36, 52}[crosshairSize.getSelectedItemPosition()]);
        DarkCatSettings.set(this, "darkcat_crosshair_thickness", new int[]{1, 2, 4}[crosshairThickness.getSelectedItemPosition()]);
        DarkCatSettings.set(this, "darkcat_stamp_coordinates", stampCoordinates.isChecked());
        DarkCatSettings.set(this, "darkcat_stamp_accuracy", stampAccuracy.isChecked());
        DarkCatSettings.set(this, "darkcat_stamp_sequence", stampSequence.isChecked());
        DarkCatSettings.set(this, "darkcat_stamp_tags", stampTags.isChecked());
        DarkCatSettings.set(this, "darkcat_stamp_custom_text_enabled", stampCustom.isChecked());
        DarkCatSettings.set(this, "darkcat_stamp_custom_text", customStamp.getText().toString().trim());
        DarkCatSettings.set(this, "darkcat_volume_shutter", volumeShutter.isChecked());
        DarkCatSettings.set(this, "darkcat_haptic_success", HapticPreset.values()[hapticSuccess.getSelectedItemPosition()].name());
        DarkCatSettings.set(this, "darkcat_haptic_failure", HapticPreset.values()[hapticFailure.getSelectedItemPosition()].name());
        DarkCatSettings.set(this, "darkcat_watermark_enabled", watermarkEnabled.isChecked());
        DarkCatSettings.set(this, "darkcat_watermark_tiled", watermarkTiled.isChecked());
        DarkCatSettings.set(this, "darkcat_watermark_position", new String[]{"top_left", "top_right",
                "bottom_left", "bottom_right", "center"}[watermarkPosition.getSelectedItemPosition()]);

        DarkCatSettings.set(this, "darkcat_provider", selectedProvider);
        DarkCatSettings.set(this, "darkcat_webdav_base", webdavBase.getText().toString().trim());
        DarkCatSettings.set(this, "darkcat_remote_folder", remoteFolder.getText().toString().trim());
        DarkCatSettings.set(this, "darkcat_auto_upload", autoUpload.isChecked());
        DarkCatSettings.set(this, "darkcat_wifi_only", wifiOnly.isChecked());
        DarkCatSettings.set(this, "darkcat_delete_after_verified", deleteAfterVerified.isChecked());

        upstream.edit()
                .putBoolean(PreferenceKeys.PausePreviewPreferenceKey, pausePreview.isChecked())
                .putBoolean(PreferenceKeys.LocationPreferenceKey, recordLocation.isChecked())
                .putBoolean(PreferenceKeys.RequireLocationPreferenceKey, false)
                .putString(PreferenceKeys.WhiteBalancePreferenceKey, new String[]{"auto", "daylight",
                        "cloudy-daylight", "incandescent", "fluorescent"}[whiteBalance.getSelectedItemPosition()])
                .putString(PreferenceKeys.ExposurePreferenceKey, new String[]{"-1", "0", "1"}[brightness.getSelectedItemPosition()])
                .putString(PreferenceKeys.LockOrientationPreferenceKey,
                        new String[]{"none", "portrait", "landscape"}[orientation.getSelectedItemPosition()])
                .putString(PreferenceKeys.StampGPSFormatPreferenceKey, coordinateFormat.getSelectedItemPosition() == 1
                        ? "preference_stamp_gpsformat_dms" : "preference_stamp_gpsformat_default")
                .putString(PreferenceKeys.VolumeKeysPreferenceKey, "volume_nothing")
                .putBoolean(PreferenceKeys.VideoStabilizationPreferenceKey, videoStabilization.isChecked())
                .putBoolean(PreferenceKeys.RecordAudioPreferenceKey, recordAudio.isChecked())
                .apply();
        DarkCatPreferencePolicy.normalize(this);
        return true;
    }

    private int watermarkPositionIndex() {
        String value = DarkCatSettings.watermarkPosition(this);
        if ("top_left".equals(value)) return 0;
        if ("top_right".equals(value)) return 1;
        if ("bottom_left".equals(value)) return 2;
        if ("center".equals(value)) return 4;
        return 3;
    }

    private List<PhotoResolutionPolicy.SizeValue> supportedPhotoResolutions() {
        MainActivity activity = DarkCatUi.activeCameraActivity();
        if (activity == null || activity.getPreview() == null) return new ArrayList<>();
        List<CameraController.Size> sizes = activity.getPreview().getSupportedPictureSizes(false);
        ArrayList<PhotoResolutionPolicy.SizeValue> result = new ArrayList<>();
        if (sizes != null) for (CameraController.Size size : sizes) {
            if (size == null || size.width <= 0 || size.height <= 0 || containsSize(result, size.width, size.height)) continue;
            result.add(new PhotoResolutionPolicy.SizeValue(size.width, size.height));
        }
        java.util.Collections.sort(result, (left, right) -> Long.compare(right.pixels(), left.pixels()));
        return result;
    }

    private String[] resolutionLabels() {
        if (photoResolutionOptions.isEmpty()) return new String[]{"Камера открывается…"};
        String[] labels = new String[photoResolutionOptions.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = PhotoResolutionPolicy.label(photoResolutionOptions.get(i));
        return labels;
    }

    private int selectedResolutionIndex() {
        MainActivity activity = DarkCatUi.activeCameraActivity();
        if (activity != null && activity.getPreview() != null) {
            CameraController.Size current = activity.getPreview().getCurrentPictureSize();
            if (current != null) for (int i = 0; i < photoResolutionOptions.size(); i++) {
                PhotoResolutionPolicy.SizeValue candidate = photoResolutionOptions.get(i);
                if (candidate.width == current.width && candidate.height == current.height) return i;
            }
        }
        return 0;
    }

    private void persistPhotoResolution() {
        if (photoResolutionOptions.isEmpty() || photoResolution == null) return;
        int index = photoResolution.getSelectedItemPosition();
        if (index < 0 || index >= photoResolutionOptions.size()) return;
        MainActivity activity = DarkCatUi.activeCameraActivity();
        if (activity == null) return;
        PhotoResolutionPolicy.SizeValue selected = photoResolutionOptions.get(index);
        activity.getApplicationInterface().setCameraResolutionPref(selected.width, selected.height);
    }

    /** Uses the retained Camera2 controller preference rather than exposing upstream keys. */
    private int flashIndex() {
        MainActivity activity = DarkCatUi.activeCameraActivity();
        String value = activity == null ? "flash_auto" : activity.getApplicationInterface().getFlashPref();
        return valueIndex(value, new String[]{"flash_auto", "flash_off", "flash_on", "flash_torch"});
    }

    private void persistFlash() {
        if (flash == null) return;
        MainActivity activity = DarkCatUi.activeCameraActivity();
        if (activity == null || activity.getPreview() == null) return;
        String value = new String[]{"flash_auto", "flash_off", "flash_on", "flash_torch"}
                [flash.getSelectedItemPosition()];
        activity.getPreview().updateFlash(value);
    }

    private int orientationIndex() {
        String value = upstream.getString(PreferenceKeys.LockOrientationPreferenceKey, "none");
        if ("portrait".equals(value)) return 1;
        if ("landscape".equals(value)) return 2;
        return 0;
    }

    private static boolean containsSize(List<PhotoResolutionPolicy.SizeValue> values, int width, int height) {
        for (PhotoResolutionPolicy.SizeValue value : values)
            if (value.width == width && value.height == height) return true;
        return false;
    }

    private String watermarkLabel() {
        String uri = DarkCatSettings.watermarkUri(this);
        return uri == null || uri.trim().isEmpty() ? "Файл watermark не выбран" : "Выбран: " + uri;
    }

    private void applySequenceOnly() {
        try {
            int next = Integer.parseInt(currentSequence.getText().toString().trim());
            if (next < 1) throw new NumberFormatException();
            DarkCatSettings.setCurrentPhotoSequence(this, next);
            Toast.makeText(this, "Следующий номер: " + next, Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException invalid) {
            currentSequence.setError("Введите номер от 1");
        }
    }

    private void showTagManager() {
        TagRepository repository = new TagRepository(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(6), dp(18), 0);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        content.addView(rows);
        EditText input = new EditText(this);
        input.setHint("Новая метка, несколько слов или emoji");
        input.setSingleLine(true);
        content.addView(input);
        Button add = button("Добавить", v -> {
            if (!repository.add(input.getText().toString())) {
                Toast.makeText(this, "Пустая или уже существующая метка", Toast.LENGTH_SHORT).show();
            }
            input.setText("");
            renderTagRows(rows, repository);
        });
        content.addView(add, match());
        renderTagRows(rows, repository);
        new AlertDialog.Builder(this)
                .setTitle("Метки")
                .setView(content)
                .setPositiveButton("Готово", null)
                .setNeutralButton("Очистить выбранные", (dialog, which) -> repository.clearActive())
                .show();
    }

    private void renderTagRows(LinearLayout rows, TagRepository repository) {
        rows.removeAllViews();
        List<String> tags = repository.all();
        if (tags.isEmpty()) {
            note(rows, "Метки ещё не созданы.");
            return;
        }
        for (String tag : tags) {
            LinearLayout row = row(rows);
            CheckBox active = new CheckBox(this);
            active.setText(tag);
            active.setChecked(repository.isActive(tag));
            active.setOnCheckedChangeListener((button, checked) -> {
                if (repository.isActive(tag) != checked) repository.toggle(tag);
            });
            Button remove = button("Удалить", v -> {
                repository.remove(tag);
                renderTagRows(rows, repository);
            });
            row.addView(active, weight());
            row.addView(remove);
        }
    }

    private void updateProviderVisibility() {
        if (nextcloudFields == null || webdavFields == null) return;
        nextcloudFields.setVisibility(provider.getSelectedItemPosition() == 1 ? View.VISIBLE : View.GONE);
        webdavFields.setVisibility(provider.getSelectedItemPosition() == 2 ? View.VISIBLE : View.GONE);
    }

    private boolean granted(String permission) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBackgroundLocation() {
        return Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private int providerIndex() {
        String value = DarkCatSettings.provider(this);
        if (DarkCatSettings.PROVIDER_NEXTCLOUD.equals(value)) return 1;
        if (DarkCatSettings.PROVIDER_WEBDAV.equals(value)) return 2;
        if (DarkCatSettings.PROVIDER_DARKCAT_API.equals(value)) return 3;
        return 0;
    }

    private static int exposureIndex(String value) {
        try {
            int exposure = Integer.parseInt(value);
            return exposure < 0 ? 0 : exposure > 0 ? 2 : 1;
        } catch (NumberFormatException ignored) { return 1; }
    }

    private static int colorIndex(int value) {
        if (Color.green(value) > Color.red(value) && Color.green(value) > Color.blue(value)) return 1;
        if (Color.red(value) > Color.green(value) && Color.red(value) > Color.blue(value)) return 2;
        return 0;
    }

    private static int valueIndex(String value, String[] values) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private static String trimFloat(float value) {
        return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.US, "%.1f", value);
    }

    private static long seconds(long millis) { return Math.max(1L, millis / 1000L); }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void section(LinearLayout parent, String title, String summary) {
        sectionBoundaries.add(new SectionBoundary(categoryForSection(title), parent.getChildCount()));
        addSectionHeading(parent, title, summary);
    }

    private void subsection(LinearLayout parent, String title, String summary) {
        addSectionHeading(parent, title, summary);
    }

    private void addSectionHeading(LinearLayout parent, String title, String summary) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(0xff1565c0);
        heading.setTextSize(16f);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = match();
        params.topMargin = dp(22);
        parent.addView(heading, params);
        TextView detail = note(parent, summary);
        detail.setTextColor(Color.DKGRAY);
    }

    /** Each category page is a view of one persisted settings model; hidden groups retain values. */
    private void applyCategoryVisibility(LinearLayout content) {
        if (selectedCategory == null || selectedCategory.trim().isEmpty()) return;
        int first = -1;
        int last = content.getChildCount();
        for (SectionBoundary boundary : sectionBoundaries) {
            if (first < 0 && selectedCategory.equals(boundary.category)) first = boundary.startIndex;
            else if (first >= 0) { last = boundary.startIndex; break; }
        }
        if (first < 0) return;
        for (int index = 0; index < content.getChildCount(); index++) {
            content.getChildAt(index).setVisibility(index >= first && index < last
                    ? View.VISIBLE : View.GONE);
        }
    }

    private static String categoryForSection(String title) {
        if ("СЪЁМКА".equals(title)) return "Съёмка";
        if ("ГЕОЛОКАЦИЯ".equals(title)) return "GPS";
        if ("МЕТКИ".equals(title)) return "Метки и штамп";
        if ("ПОЛЕВОЙ РЕЖИМ".equals(title)) return "Полевой режим";
        if ("ХРАНИЛИЩЕ".equals(title)) return "Хранилище";
        if ("ГАЛЕРЕЯ".equals(title)) return "Галерея";
        if ("СИНХРОНИЗАЦИЯ".equals(title)) return "Синхронизация";
        if ("ВИДЕО".equals(title)) return "Видео";
        if ("РАСШИРЕННЫЕ".equals(title)) return "Расширенные";
        return title;
    }

    private static final class SectionBoundary {
        final String category;
        final int startIndex;
        SectionBoundary(String category, int startIndex) {
            this.category = category;
            this.startIndex = startIndex;
        }
    }

    private Switch toggle(LinearLayout parent, String text, boolean checked) {
        Switch toggle = new Switch(this);
        toggle.setText(text);
        toggle.setChecked(checked);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(5), 0, dp(5));
        parent.addView(toggle, match());
        return toggle;
    }

    private Spinner spinner(LinearLayout parent, String label, String[] values, int selected) {
        TextView title = note(parent, label);
        title.setTextColor(Color.DKGRAY);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(Math.max(0, Math.min(selected, values.length - 1)));
        parent.addView(spinner, match());
        return spinner;
    }

    private EditText field(LinearLayout parent, String label, boolean secret, boolean numeric) {
        TextView title = note(parent, label);
        title.setTextColor(Color.DKGRAY);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        if (secret) field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else if (numeric) field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        parent.addView(field, match());
        return field;
    }

    private TextView note(LinearLayout parent, String text) {
        TextView note = new TextView(this);
        note.setText(text);
        note.setTextSize(13f);
        note.setPadding(0, dp(4), 0, dp(5));
        parent.addView(note, match());
        return note;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row, match());
        return row;
    }

    private LinearLayout group(LinearLayout parent) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(10), 0, 0, 0);
        parent.addView(group, match());
        return group;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
