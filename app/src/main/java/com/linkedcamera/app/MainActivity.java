Warning: truncated output (original token count: 91905)
Total output lines: 7232

package com.linkedcamera.app;

import com.linkedcamera.app.cameracontroller.CameraController;
import com.linkedcamera.app.cameracontroller.CameraControllerManager;
import com.linkedcamera.app.cameracontroller.CameraControllerManager2;
import com.linkedcamera.app.preview.Preview;
import com.linkedcamera.app.preview.VideoProfile;
import com.linkedcamera.app.remotecontrol.BluetoothRemoteControl;
import com.linkedcamera.app.ui.DrawPreview;
import com.linkedcamera.app.ui.FolderChooserDialog;
import com.linkedcamera.app.ui.MainUI;
import com.linkedcamera.app.ui.ManualSeekbars;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import android.Manifest;
import android.app.Fragment;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.speech.tts.TextToSpeech;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.exifinterface.media.ExifInterface;

import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** The main Activity for Open Camera.
 */
public class MainActivity extends AppCompatActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {
    private static final String TAG = "MainActivity";

    private static int activity_count = 0;

    private boolean app_is_paused = true;

    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;

    // components: always non-null (after onCreate())
    private BluetoothRemoteControl bluetoothRemoteControl;
    private PermissionHandler permissionHandler;
    private SettingsManager settingsManager;
    private MainUI mainUI;
    private ManualSeekbars manualSeekbars;
    private MyApplicationInterface applicationInterface;
    private TextFormatter textFormatter;
    private SoundPoolManager soundPoolManager;
    private MagneticSensor magneticSensor;
    //private SpeechControl speechControl;

    private Preview preview;
    private ru.darkcat.camera.capture.MotionSampler darkCatMotionSampler;
    private ru.darkcat.camera.capture.BestFrameMonitor darkCatBestFrameMonitor;
    private ru.darkcat.camera.haptic.CaptureHapticController darkCatHaptics;
    private boolean darkCatFieldCameraKeptWarm;
    private boolean darkCatSharpCapturePending;
    private long darkCatLastExternalTriggerElapsedMs;
    private volatile ru.darkcat.camera.location.LocationFix darkCatPendingCaptureFix;
    private volatile long darkCatPendingCaptureWallTime;
    private OrientationEventListener orientationEventListener;
    private View.OnLayoutChangeListener layoutChangeListener;
    private int large_heap_memory;
    private boolean supports_auto_stabilise;
    private boolean supports_force_video_4k;
    private boolean supports_camera2;
    private SaveLocationHistory save_location_history; // save location for non-SAF
    private SaveLocationHistory save_location_history_saf; // save location for SAF (only initialised when SAF is used)
    private boolean saf_dialog_from_preferences; // if a SAF dialog is opened, this records whether we opened it from the Preferences
    private boolean camera_in_background; // whether the camera is covered by a fragment/dialog (such as settings or folder picker)
    private GestureDetector gestureDetector;
    private boolean screen_is_locked; // whether screen is "locked" - this is Open Camera's own lock to guard against accidental presses, not the standard Android lock
    private final Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable<>();
    private ValueAnimator gallery_save_anim;
    private boolean last_continuous_fast_burst; // whether the last photo operation was a continuous_fast_burst
    private Future<?> update_gallery_future;

    private TextToSpeech textToSpeech;
    private boolean textToSpeechSuccess;

    private AudioListener audio_listener; // may be null - created when needed

    //private boolean ui_placement_right = true;

    //private final boolean edge_to_edge_mode = false; // whether running always in edge-to-edge mode
    //private final boolean edge_to_edge_mode = true; // whether running always in edge-to-edge mode
    private final boolean edge_to_edge_mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM; // whether running always in edge-to-edge mode
    private boolean want_no_limits; // whether we want to run with FLAG_LAYOUT_NO_LIMITS
    private boolean set_window_insets_listener; // whether we've enabled a setOnApplyWindowInsetsListener()
    private int navigation_gap; // gap for navigation bar along bottom (portrait) or right (landscape)
    private int navigation_gap_landscape; // gap for navigation bar along left (portrait) or bottom (landscape); only set for edge_to_edge_mode==true
    private int navigation_gap_reverse_landscape; // gap for navigation bar along right (portrait) or top (landscape); only set for edge_to_edge_mode==true
    public static volatile boolean test_preview_want_no_limits; // test flag, if set to true then instead use test_preview_want_no_limits_value; needs to be static, as it needs to be set before activity is created to take effect
    public static volatile boolean test_preview_want_no_limits_value;
    public volatile boolean test_set_show_under_navigation; // test flag, the value of enable for the last call of showUnderNavigation() (or false if not yet called)
    public static volatile boolean test_force_system_orientation; // test flag, if set to true, that getSystemOrientation() returns test_system_orientation
    public static volatile SystemOrientation test_system_orientation = SystemOrientation.PORTRAIT;
    public static volatile boolean test_force_window_insets; // test flag, if set to true, then the OnApplyWindowInsetsListener will read from the following flags
    public static volatile Insets test_insets; // test insets for WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout()
    public static volatile Insets test_cutout_insets; // test insets for WindowInsets.Type.displayCutout()

    // whether this is a multi-camera device (note, this isn't simply having more than 1 camera, but also having more than one with the same facing)
    // note that in most cases, code should check the MultiCamButtonPreferenceKey preference as well as the is_multi_cam flag,
    // this can be done via isMultiCamEnabled().
    private boolean is_multi_cam;
    // These lists are lists of camera IDs with the same "facing" (front, back or external).
    // Only initialised if is_multi_cam==true.
    private List<Integer> back_camera_ids;
    private List<Integer> front_camera_ids;
    private List<Integer> other_camera_ids;

    private final ToastBoxer switch_video_toast = new ToastBoxer();
    private final ToastBoxer screen_locked_toast = new ToastBoxer();
    private final ToastBoxer stamp_toast = new ToastBoxer();
    private final ToastBoxer changed_auto_stabilise_toast = new ToastBoxer();
    private final ToastBoxer white_balance_lock_toast = new ToastBoxer();
    private final ToastBoxer exposure_lock_toast = new ToastBoxer();
    private final ToastBoxer audio_control_toast = new ToastBoxer();
    private final ToastBoxer store_location_toast = new ToastBoxer();
    private boolean block_startup_toast = false; // used when returning from Settings/Popup - if we're displaying a toast anyway, don't want to display the info toast too
    private String push_info_toast_text; // can be used to "push" extra text to the info text for showPhotoVideoToast()
    private boolean push_switched_camera = false; // whether to display animation for switching front/back cameras

    // application shortcuts:
    static private final String ACTION_SHORTCUT_CAMERA = "net.sourceforge.opencamera.SHORTCUT_CAMERA";
    static private final String ACTION_SHORTCUT_SELFIE = "net.sourceforge.opencamera.SHORTCUT_SELFIE";
    static private final String ACTION_SHORTCUT_VIDEO = "net.sourceforge.opencamera.SHORTCUT_VIDEO";
    static private final String ACTION_SHORTCUT_GALLERY = "net.sourceforge.opencamera.SHORTCUT_GALLERY";
    static private final String ACTION_SHORTCUT_SETTINGS = "net.sourceforge.opencamera.SHORTCUT_SETTINGS";

    private static final int CHOOSE_SAVE_FOLDER_SAF_CODE = 42;
    private static final int CHOOSE_GHOST_IMAGE_SAF_CODE = 43;
    private static final int CHOOSE_LOAD_SETTINGS_SAF_CODE = 44;

    // for testing; must be volatile for test project reading the state
    // n.b., avoid using static, as static variables are shared between different instances of an application,
    // and won't be reset in subsequent tests in a suite!
    public boolean is_test; // whether called from OpenCamera.test testing
    public volatile Bitmap gallery_bitmap;
    public volatile boolean test_low_memory;
    public volatile boolean test_have_angle;
    public volatile float test_angle;
    public volatile Uri test_last_saved_imageuri; // uri of last image; set if using scoped storage OR using SAF
    public volatile String test_last_saved_image; // filename (including full path) of last image; set if not using scoped storage nor using SAF (i.e., writing using File API)
    public static boolean test_force_supports_camera2; // okay to be static, as this is set for an entire test suite
    public volatile String test_save_settings_file;

    // update: notifications now removed due to needing permissions on Android 13+
    //private boolean has_notification;
    //private final String CHANNEL_ID = "open_camera_channel";
    //private final int image_saving_notification_id = 1;

    private static final float WATER_DENSITY_FRESHWATER = 1.0f;
    private static final float WATER_DENSITY_SALTWATER = 1.03f;
    private float mWaterDensity = 1.0f;

    // whether to lock to landscape orientation, or allow switching between portrait and landscape orientations
    //public static final boolean lock_to_landscape = true;
    public static final boolean lock_to_landscape = false;

    // handling for lock_to_landscape==false:

    public enum SystemOrientation {
        LANDSCAPE,
        PORTRAIT,
        REVERSE_LANDSCAPE
    }

    private MyDisplayListener displayListener;

    private boolean has_cached_system_orientation;
    private SystemOrientation cached_system_orientation;

    private boolean hasOldSystemOrientation;
    private SystemOrientation oldSystemOrientation;

    private boolean has_cached_display_rotation;
    private long cached_display_rotation_time_ms;
    private int cached_display_rotation;

    List<Integer> exposure_seekbar_values; // mapping from exposure_seekbar progress value to preview exposure compensation
    private int exposure_seekbar_values_zero; // index in exposure_seekbar_values that maps to zero preview exposure compensation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long debug_time = 0;
        if( MyDebug.LOG ) {
            Log.d(TAG, "onCreate: " + this);
            debug_time = System.currentTimeMillis();
        }
        activity_count++;
        if( MyDebug.LOG )
            Log.d(TAG, "activity_count: " + activity_count);
        //EdgeToEdge.enable(this, SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT)); // test edge-to-edge on pre-Android 15
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false); // initialise any unset preferences to their default values
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting default preference values: " + (System.currentTimeMillis() - debug_time));

        if( getIntent() != null && getIntent().getExtras() != null ) {
            // whether called from testing
            is_test = getIntent().getExtras().getBoolean("test_project");
            if( MyDebug.LOG )
                Log.d(TAG, "is_test: " + is_test);
        }
        /*if( getIntent() != null && getIntent().getExtras() != null ) {
            // whether called from Take Photo widget
            if( MyDebug.LOG )
                Log.d(TAG, "take_photo?: " + getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO));
        }*/
        if( MyDebug.LOG ) {
            // whether called from Take Photo widget
            Log.d(TAG, "take_photo?: " + TakePhoto.TAKE_PHOTO);
        }
        if( getIntent() != null && getIntent().getAction() != null ) {
            // invoked via the manifest shortcut?
            if( MyDebug.LOG )
                Log.d(TAG, "shortcut: " + getIntent().getAction());
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // determine whether we should support "auto stabilise" feature
        // risk of running out of memory on lower end devices, due to manipulation of large bitmaps
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if( MyDebug.LOG ) {
            Log.d(TAG, "large max memory = " + activityManager.getLargeMemoryClass() + "MB");
        }
        large_heap_memory = activityManager.getLargeMemoryClass();
        if( large_heap_memory >= 128 ) {
            supports_auto_stabilise = true;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "supports_auto_stabilise? " + supports_auto_stabilise);

        // hack to rule out phones unlikely to have 4K video, so no point even offering the option!
        // both S5 and Note 3 have 128MB standard and 512MB large heap (tested via Samsung RTL), as does Galaxy K Zoom
        if( activityManager.getLargeMemoryClass() >= 512 ) {
            supports_force_video_4k = true;
        }
        if( MyDebug.LOG )
            Log.d(TAG, "supports_force_video_4k? " + supports_force_video_4k);

        // set up components
        bluetoothRemoteControl = new BluetoothRemoteControl(this);
        permissionHandler = new PermissionHandler(this);
        settingsManager = new SettingsManager(this);
        mainUI = new MainUI(this);
        manualSeekbars = new ManualSeekbars();
        applicationInterface = new MyApplicationInterface(this, savedInstanceState);
        darkCatMotionSampler = new ru.darkcat.camera.capture.MotionSampler(this);
        darkCatHaptics = new ru.darkcat.camera.haptic.CaptureHapticController(
                new ru.darkcat.camera.haptic.AndroidCaptureHaptics(this));
        ru.darkcat.camera.vault.DarkCatCaptureCoordinator.resumePending(this);
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating application interface: " + (System.currentTimeMillis() - debug_time));
        textFormatter = new TextFormatter(this);
        soundPoolManager = new SoundPoolManager(this);
        magneticSensor = new MagneticSensor(this);
        //speechControl = new SpeechControl(this);

        // determine whether we support Camera2 API
        // must be done before setDeviceDefaults()
        initCamera2Support();

        // set some per-device defaults
        // must be done before creating the Preview (as setDeviceDefaults() may set Camera2 API)
        boolean has_done_first_time = sharedPreferences.contains(PreferenceKeys.FirstTimePreferenceKey);
        if( MyDebug.LOG )
            Log.d(TAG, "has_done_first_time: " + has_done_first_time);
        if( !has_done_first_time ) {
            // must be done after initCamera2Support()
            setDeviceDefaults();
        }
        applyDarkCatProductDefaults(sharedPreferences);

        boolean settings_is_open = settingsIsOpen();
        if( MyDebug.LOG )
            Log.d(TAG, "settings_is_open?: " + settings_is_open);
        // settings_is_open==true can happen if application is recreated when settings is open
        // to reproduce: go to settings, then turn screen off and on (and unlock)
        if( !settings_is_open ) {
            // set up window flags for normal operation
            setWindowFlagsForCamera();
        }
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting window flags: " + (System.currentTimeMillis() - debug_time));

        save_location_history = new SaveLocationHistory(this, PreferenceKeys.SaveLocationHistoryBasePreferenceKey, getStorageUtils().getSaveLocation());
        checkSaveLocations();
        if( applicationInterface.getStorageUtils().isUsingSAF() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "create new SaveLocationHistory for SAF");
            save_location_history_saf = new SaveLocationHistory(this, PreferenceKeys.SaveLocationHistorySAFBasePreferenceKey, getStorageUtils().getSaveLocationSAF());
        }
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after updating folder history: " + (System.currentTimeMillis() - debug_time));

        // set up sensors
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

        // accelerometer sensor (for device orientation)
        if( mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "found accelerometer");
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "no support for accelerometer");
        }
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating accelerometer sensor: " + (System.currentTimeMillis() - debug_time));

        // magnetic sensor (for compass direction)
        magneticSensor.initSensor(mSensorManager);
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating magnetic sensor: " + (System.currentTimeMillis() - debug_time));

        // clear any seek bars (just in case??)
        mainUI.closeExposureUI();

        // set up the camera and its preview
        preview = new Preview(applicationInterface, (this.findViewById(R.id.preview)));
        darkCatBestFrameMonitor = new ru.darkcat.camera.capture.BestFrameMonitor(
                preview.getView(), darkCatMotionSampler);
        ru.darkcat.camera.ui.DarkCatUi.install(this);
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating preview: " + (System.currentTimeMillis() - debug_time));

        if( settings_is_open ) {
            // must be done after creating preview
            setWindowFlagsForSettings();
        }

        {
            // don't show orientation animations
            // must be done after creating Preview (so we know if Camera2 API or not)
            WindowManager.LayoutParams layout = getWindow().getAttributes();
            // If locked to landscape, ROTATION_ANIMATION_SEAMLESS/JUMPCUT has the problem that when going to
            // Settings in portrait, we briefly see the UI change - this is because we set the flag
            // to no longer lock to landscape, and that change happens too quickly.
            // This isn't a problem when lock_to_landscape==false, and we want
            // ROTATION_ANIMATION_SEAMLESS so that there is no/minimal pause from the preview when
            // rotating the device. However if using old camera API, we get an ugly transition with
            // ROTATION_ANIMATION_SEAMLESS (probably related to not using TextureView?)
            if( lock_to_landscape || !preview.usingCamera2API() )
                layout.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
            else if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O )
                layout.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;
            else
                layout.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
            getWindow().setAttributes(layout);
        }

        // Setup multi-camera buttons (must be done after creating preview so we know which Camera API is being used,
        // and before initialising on-screen visibility).
        // We only allow the separate icon for switching cameras if:
        // - there are at least 2 types of "facing" camera, and
        // - there are at least 2 cameras with the same "facing".
        // If there are multiple cameras but all with different "facing", then the switch camera
        // icon is used to iterate over all cameras.
        // If there are more than two cameras, but all cameras have the same "facing, we still stick
        // with using the switch camera icon to iterate over all cameras.
        int n_cameras = preview.getCameraControllerManager().getNumberOfCameras();
        if( n_cameras > 2 ) {
            this.back_camera_ids = new ArrayList<>();
            this.front_camera_ids = new ArrayList<>();
            this.other_camera_ids = new ArrayList<>();
            for(int i=0;i<n_cameras;i++) {
                switch( preview.getCameraControllerManager().getFacing(i) ) {
                    case FACING_BACK:
                        back_camera_ids.add(i);
                        break;
                    case FACING_FRONT:
                        front_camera_ids.add(i);
                        break;
                    default:
                        // we assume any unknown cameras are also external
                        other_camera_ids.add(i);
                        break;
                }
            }
            boolean multi_same_facing = back_camera_ids.size() >= 2 || front_camera_ids.size() >= 2 || other_camera_ids.size() >= 2;
            int n_facing = 0;
            if( !back_camera_ids.isEmpty() )
                n_facing++;
            if( !front_camera_ids.isEmpty() )
                n_facing++;
            if( !other_camera_ids.isEmpty() )
                n_facing++;
            this.is_multi_cam = multi_same_facing && n_facing >= 2;
            //this.is_multi_cam = false; // test
            if( MyDebug.LOG ) {
                Log.d(TAG, "multi_same_facing: " + multi_same_facing);
                Log.d(TAG, "n_facing: " + n_facing);
                Log.d(TAG, "is_multi_cam: " + is_multi_cam);
            }

            if( !is_multi_cam ) {
                this.back_camera_ids = null;
                this.front_camera_ids = null;
                this.other_camera_ids = null;
            }
        }

        // initialise on-screen button visibility
        View switchCameraButton = findViewById(R.id.switch_camera);
        switchCameraButton.setVisibility(n_cameras > 1 ? View.VISIBLE : View.GONE);
        // switchMultiCameraButton visibility updated below in mainUI.updateOnScreenIcons(), as it also depends on user preference
        View speechRecognizerButton = findViewById(R.id.audio_control);
        speechRecognizerButton.setVisibility(View.GONE); // disabled by default, until the speech recognizer is created
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting button visibility: " + (System.currentTimeMillis() - debug_time));
        View pauseVideoButton = findViewById(R.id.pause_video);
        pauseVideoButton.setVisibility(View.GONE);
        View takePhotoVideoButton = findViewById(R.id.take_photo_when_video_recording);
        takePhotoVideoButton.setVisibility(View.GONE);
        View cancelPanoramaButton = findViewById(R.id.cancel_panorama);
        cancelPanoramaButton.setVisibility(View.GONE);

        // We initialise optional controls to invisible/gone, so they don't show while the camera is opening - the actual visibility is
        // set in cameraSetup().
        // Note that ideally we'd set this in the xml, but doing so for R.id.zoom causes a crash on Galaxy Nexus startup beneath
        // setContentView()!
        // To be safe, we also do so for take_photo and zoom_seekbar (we already know we've had no reported crashes for focus_seekbar,
        // however).
        View takePhotoButton = findViewById(R.id.take_photo);
        takePhotoButton.setVisibility(View.INVISIBLE);
        View zoomSeekbar = findViewById(R.id.zoom_seekbar);
        zoomSeekbar.setVisibility(View.INVISIBLE);

        // initialise state of on-screen icons
        mainUI.updateOnScreenIcons();

        if( MainActivity.lock_to_landscape ) {
            // listen for orientation event change (only required if lock_to_landscape==true
            // (MainUI.onOrientationChanged() does nothing if lock_to_landscape==false)
            orientationEventListener = new OrientationEventListener(this) {
                @Override
                public void onOrientationChanged(int orientation) {
                    MainActivity.this.mainUI.onOrientationChanged(orientation);
                }
            };
            if( MyDebug.LOG )
                Log.d(TAG, "onCreate: time after setting orientation event listener: " + (System.currentTimeMillis() - debug_time));
        }

        layoutChangeListener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if( MyDebug.LOG )
                    Log.d(TAG, "onLayoutChange");

                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode() ) {
                    Point display_size = new Point();
                    applicationInterface.getDisplaySize(display_size, true);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "    display width: " + display_size.x);
                        Log.d(TAG, "    display height: " + display_size.y);
                        Log.d(TAG, "    layoutUI display width: " + mainUI.layoutUI_display_w);
                        Log.d(TAG, "    layoutUI display height: " + mainUI.layoutUI_display_h);
                    }
                    // We need to call layoutUI when the window is resized without an orientation change -
                    // this can happen in split-screen or multi-window mode, where onConfigurationChanged
                    // is not guaranteed to be called.
                    // We check against the size of when layoutUI was last called, to avoid repeated calls
                    // when the resize is due to the device rotating and onConfigurationChanged is called -
                    // in fact we'd have a problem of repeatedly calling layoutUI, since doing layoutUI
                    // causes onLayoutChange() to be called again.
                    if( display_size.x != mainUI.layoutUI_display_w || display_size.y != mainUI.layoutUI_display_h ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "call layoutUI due to resize");
                        mainUI.layoutUI();
                    }
                }
            }
        };

        // set up take photo long click
        takePhotoButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if( !allowLongPress() ) {
                    // return false, so a regular click will still be triggered when the user releases the touch
                    return false;
                }
                return longClickedTakePhoto();
            }
        });
        // set up on touch listener so we can detect if we've released from a long click
        takePhotoButton.setOnTouchListener(new View.OnTouchListener() {
            // the suppressed warning ClickableViewAccessibility suggests calling view.performClick for ACTION_UP, but this
            // results in an additional call to clickedTakePhoto() - that is, if there is no long press, we get two calls to
            // clickedTakePhoto instead one one; and if there is a long press, we get one call to clickedTakePhoto where
            // there should be none.
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if( motionEvent.getAction() == MotionEvent.ACTION_UP ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "takePhotoButton ACTION_UP");
                    takePhotoButtonLongClickCancelled();
                    if( MyDebug.LOG )
                        Log.d(TAG, "takePhotoButton ACTION_UP done");
                }
                return false;
            }
        });

        // set up gallery button long click
        View galleryButton = findViewById(R.id.gallery);
        galleryButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if( !allowLongPress() ) {
                    // return false, so a regular click will still be triggered when the user releases the touch
                    return false;
                }
                //preview.showToast(null, "Long click");
                longClickedGallery();
                return true;
            }
        });

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting long click listeners: " + (System.currentTimeMillis() - debug_time));

        // listen for gestures
        gestureDetector = new GestureDetector(this, new MyGestureDetector());
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after creating gesture detector: " + (System.currentTimeMillis() - debug_time));

        setupSystemUiVisibilityListener();
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after setting system ui visibility listener: " + (System.currentTimeMillis() - debug_time));

        // show "about" dialog for first time use
        if( !has_done_first_time ) {
            if( !is_test ) {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                alertDialog.setTitle(R.string.app_name);
                alertDialog.setMessage(R.string.intro_text);
                alertDialog.setPositiveButton(android.R.string.ok, null);
                alertDialog.setNegativeButton(R.string.preference_online_help, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "online help");
                        launchOnlineHelp();
                    }
                });
                alertDialog.show();
            }

            setFirstTimeFlag();
        }

        {
            // handle What's New dialog
            int version_code = -1;
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                version_code = pInfo.versionCode;
            }
            catch(PackageManager.NameNotFoundException e) {
                MyDebug.logStackTrace(TAG, "NameNotFoundException exception trying to get version number", e);
            }
            if( version_code != -1 ) {
                int latest_version = sharedPreferences.getInt(PreferenceKeys.LatestVersionPreferenceKey, 0);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "version_code: " + version_code);
                    Log.d(TAG, "latest_version: " + latest_version);
                }
                //final boolean whats_new_enabled = false;
                final boolean whats_new_enabled = true;
                if( whats_new_enabled ) {
                    // whats_new_version is the version code that the What's New text is written for. Normally it will equal the
                    // current release (version_code), but it some cases we may want to leave it unchanged.
                    // E.g., we have a "What's New" for 1.44 (64), but then push out a quick fix for 1.44.1 (65). We don't want to
                    // show the dialog again to people who already received 1.44 (64), but we still want to show the dialog to people
                    // upgrading from earlier versions.
                    int whats_new_version = 1; // 1.0
                    whats_new_version = Math.min(whats_new_version, version_code); // whats_new_version should always be <= version_code, but just in case!
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "whats_new_version: " + whats_new_version);
                    }
                    final boolean force_whats_new = false;
                    //final boolean force_whats_new = true; // for testing
                    boolean allow_show_whats_new = sharedPreferences.getBoolean(PreferenceKeys.ShowWhatsNewPreferenceKey, true);
                    if( MyDebug.LOG )
                        Log.d(TAG, "allow_show_whats_new: " + allow_show_whats_new);
                    // don't show What's New if this is the first time the user has run
                    if( has_done_first_time && allow_show_whats_new && ( force_whats_new || whats_new_version > latest_version ) ) {
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                        alertDialog.setTitle(R.string.whats_new);
                        alertDialog.setMessage(R.string.whats_new_text);
                        alertDialog.setPositiveButton(android.R.string.ok, null);
                        alertDialog.show();
                    }
                }
                // We set the latest_version whether or not the dialog is shown - if we showed the first time dialog, we don't
                // want to then show the What's New dialog next time we run! Similarly if the user had disabled showing the dialog,
                // but then enables it, we still shouldn't show the dialog until the new time Open Camera upgrades.
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(PreferenceKeys.LatestVersionPreferenceKey, version_code);
                editor.apply();
            }
        }

        setModeFromIntents(savedInstanceState);

        // load icons
        preloadIcons(R.array.flash_icons);
        preloadIcons(R.array.focus_mode_icons);
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: time after preloading icons: " + (System.currentTimeMillis() - debug_time));

        // initialise text to speech engine
        textToSpeechSuccess = false;
        // run in separate thread so as to not delay startup time
        new Thread(new Runnable() {
            public void run() {
                textToSpeech = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "TextToSpeech initialised");
                        if( status == TextToSpeech.SUCCESS ) {
                            textToSpeechSuccess = true;
                            if( MyDebug.LOG )
                                Log.d(TAG, "TextToSpeech succeeded");
                        }
                        else {
                            if( MyDebug.LOG )
                                Log.d(TAG, "TextToSpeech failed");
                        }
                    }
                });
            }
        }).start();

        // handle on back behaviour
        popupOnBackPressedCallback = new PopupOnBackPressedCallback(false);
        this.getOnBackPressedDispatcher().addCallback(this, popupOnBackPressedCallback);
        pausePreviewOnBackPressedCallback = new PausePreviewOnBackPressedCallback(false);
        this.getOnBackPressedDispatcher().addCallback(this, pausePreviewOnBackPressedCallback);
        screenLockOnBackPressedCallback = new ScreenLockOnBackPressedCallback(false);
        this.getOnBackPressedDispatcher().addCallback(this, screenLockOnBackPressedCallback);

        // create notification channel - only needed on Android 8+
        // update: notifications now removed due to needing permissions on Android 13+
        /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            CharSequence name = "Open Camera Image Saving";
            String description = "Notification channel for processing and saving images in the background";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }*/

        // so we get the icons rotation even when rotating for the first time - see onSystemOrientationChanged
        this.hasOldSystemOrientation = true;
        this.oldSystemOrientation = getSystemOrientation();

        if( MyDebug.LOG )
            Log.d(TAG, "onCreate: total time for Activity startup: " + (System.currentTimeMillis() - debug_time));
    }

    /** Whether to use codepaths that are compatible with scoped storage.
     */
    public static boolean useScopedStorage() {
        //return false;
        //return true;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /** Whether this is a multi camera device, and the user preference is set to enable the multi-camera button.
     */
    public boolean isMultiCamEnabled() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return is_multi_cam && sharedPreferences.getBoolean(PreferenceKeys.MultiCamButtonPreferenceKey, true);
    }

    /** Whether this is a multi camera device, whether or not the user preference is set to enable
     *  the multi-camera button.
     */
    public boolean isMultiCam() {
        return is_multi_cam;
    }

    /* Returns the camera Id in use by the preview - or the one we requested, if the camera failed
     * to open.
     * Needed as Preview.getCameraId() returns 0 if camera_controller==null, but if the camera
     * fails to open, we want the switch camera icons to still work as expected!
     */
    private int getActualCameraId() {
        if( preview.getCameraController() == null )
            return applicationInterface.getCameraIdPref();
        else
            return preview.getCameraId();
    }

    /** Whether the icon switch_multi_camera should be displayed. This is if the following are all
     *  true:
     *  - The device is a multi camera device (MainActivity.is_multi_cam==true).
     *  - The user preference for using the separate icons is enabled
     *    (PreferenceKeys.MultiCamButtonPreferenceKey).
     *  - For the current camera ID, there are at least two cameras with the same front/back/external
     *    "facing" (e.g., imagine a device with two back cameras, but only one front camera - no point
     *    showing the multi-cam icon for just a single logical front camera).
     *  OR there are physical cameras for the current camera, and again the user preference
     *  PreferenceKeys.MultiCamButtonPreferenceKey is enabled.
     */
    public boolean showSwitchMultiCamIcon() {
        if( preview.hasPhysicalCameras() ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if( sharedPreferences.getBoolean(PreferenceKeys.MultiCamButtonPreferenceKey, true) )
                return true;
        }
        if( isMultiCamEnabled() ) {
            int cameraId = getActualCameraId();
            switch( preview.getCameraControllerManager().getFacing(cameraId) ) {
                case FACING_BACK:
                    if( back_camera_ids.size() > 1 )
                        return true;
                    break;
                case FACING_FRONT:
                    if( front_camera_ids.size() > 1 )
                        return true;
                    break;
                default:
                    if( other_camera_ids.size() > 1 )
                        return true;
                    break;
            }
        }
        return false;
    }

    /** Whether user preference is set to allow long press actions.
     */
    private boolean allowLongPress() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getBoolean(PreferenceKeys.AllowLongPressPreferenceKey, true);
    }

    /* This method sets the preference defaults which are set specific for a particular device.
     * This method should be called when Open Camera is run for the very first time after installation,
     * or when the user has requested to "Reset settings".
     */
    void setDeviceDefaults() {
        if( MyDebug.LOG )
            Log.d(TAG, "setDeviceDefaults");
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        //SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        //boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        //boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
        //boolean is_nexus = Build.MODEL.toLowerCase(Locale.US).contains("nexus");
        //boolean is_nexus6 = Build.MODEL.toLowerCase(Locale.US).contains("nexus 6");
        //boolean is_pixel_phone = Build.DEVICE != null && Build.DEVICE.equals("sailfish");
        //boolean is_pixel_xl_phone = Build.DEVICE != null && Build.DEVICE.equals("marlin");
        /*if( MyDebug.LOG ) {
            //Log.d(TAG, "is_samsung? " + is_samsung);
            //Log.d(TAG, "is_oneplus? " + is_oneplus);
            //Log.d(TAG, "is_nexus? " + is_nexus);
            //Log.d(TAG, "is_nexus6? " + is_nexus6);
            //Log.d(TAG, "is_pixel_phone? " + is_pixel_phone);
            //Log.d(TAG, "is_pixel_xl_phone? " + is_pixel_xl_phone);
        }*/
        /*if( is_samsung || is_oneplus ) {
            // The problems we used to have on Samsung Galaxy devices are now fixed, by setting
            // TEMPLATE_PREVIEW for the precaptureBuilder in CameraController2. This also fixes the
            // problems with OnePlus 3T having blue tinge if flash is on, and the scene is bright
            // enough not to need it
            if( MyDebug.LOG )
                Log.d(TAG, "set fake flash for camera2");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, true);
            editor.apply();
        }*/
		/*if( is_nexus6 ) {
			// Nexus 6 captureBurst() started having problems with Android 7 upgrade - images appeared in wrong order (and with wrong order of shutter speeds in exif info), as well as problems with the camera failing with serious errors
			// we set this even for Nexus 6 devices not on Android 7, as at some point they'll likely be upgraded to Android 7
			// Update: now fixed in v1.37, this was due to bug where we set RequestTag.CAPTURE for all captures in takePictureBurstExpoBracketing(), rather than just the last!
			if( MyDebug.LOG )
				Log.d(TAG, "disable fast burst for camera2");
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putBoolean(PreferenceKeys.Camera2FastBurstPreferenceKey, false);
			editor.apply();
		}*/
        if( is_samsung && !is_test ) {
            // Samsung Galaxy devices (including S10e, S24) have problems with HDR/expo - base images come out with wrong exposures.
            // This can be fixed by not using fast bast, allowing us to adjust the preview exposure to match.
            if( MyDebug.LOG )
                Log.d(TAG, "disable fast burst for camera2");
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PreferenceKeys.Camera2FastBurstPreferenceKey, false);
            editor.apply();
        }
        if( supports_camera2 && !is_test ) {
            // n.b., when testing, we explicitly decide whether to run with Camera2 API or not
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            int n_cameras = manager2.getNumberOfCameras();
            boolean all_supports_camera2 = true; // whether all cameras have at least LIMITED support for Camera2 (risky to default to Camera2 if any cameras are LEGACY, as not easy to test such devices)
            for(int i=0;i<n_cameras && all_supports_camera2;i++) {
                if( !manager2.allowCamera2Support(i) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera " + i + " doesn't have at least LIMITED support for Camera2 API");
                    all_supports_camera2 = false;
                }
            }

            if( all_supports_camera2 ) {
                boolean default_to_camera2 = false;
                boolean is_google = Build.MANUFACTURER.toLowerCase(Locale.US).contains("google");
                boolean is_nokia = Build.MANUFACTURER.toLowerCase(Locale.US).contains("hmd global");
                boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
                if( is_google && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S )
                    default_to_camera2 = true;
                else if( is_nokia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P )
                    default_to_camera2 = true;
                else if( is_samsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S )
                    default_to_camera2 = true;
                else if( is_oneplus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE )
                    default_to_camera2 = true;

                if( default_to_camera2 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "default to camera2 API");
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2");
                    editor.apply();
                }
            }
        }
    }

    /** Applies the small, opinionated DarkCat product surface once per installation/update.
     *  The full upstream controls remain available from Advanced settings. */
    private void applyDarkCatProductDefaults(SharedPreferences sharedPreferences) {
        if( sharedPreferences.getBoolean("darkcat_product_defaults_v3", false) )
            return;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if( supports_camera2 )
            editor.putString(PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2");
        editor.putString(PreferenceKeys.QualityPreferenceKey, "100");
        editor.putString(PreferenceKeys.OptimiseFocusPreferenceKey, "preference_photo_optimise_focus_latency");
        editor.putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std");
        editor.putString(PreferenceKeys.RawPreferenceKey, "preference_raw_no");
        editor.putString(PreferenceKeys.StampPreferenceKey, "preference_stamp_no");
        editor.putBoolean(PreferenceKeys.PausePreviewPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.ShowWhenLockedPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.LocationPreferenceKey, true);
        editor.putBoolean(PreferenceKeys.RequireLocationPreferenceKey, false);
        editor.putBoolean(PreferenceKeys.MultiCamButtonPreferenceKey, true);
        // DarkCat handles only Volume+ itself so Volume- remains normal volume control.
        editor.putString(PreferenceKeys.VolumeKeysPreferenceKey, "volume_nothing");
        editor.putBoolean("darkcat_product_defaults_v3", true);
        editor.apply();
    }

    /** Switches modes if required, if called from a relevant intent/tile.
     */
    private void setModeFromIntents(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "setModeFromIntents");
        if( savedInstanceState != null ) {
            // If we're restoring from a saved state, we shouldn't be resetting any modes
            if( MyDebug.LOG )
                Log.d(TAG, "restoring from saved state");
            return;
        }
        boolean done_facing = false;
        String action = this.getIntent().getAction();
        if( MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(action) || MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from video intent");
            applicationInterface.setVideoPref(true);
        }
        else if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action) || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from photo intent");
            applicationInterface.setVideoPref(false);
        }
        else if( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileService.TILE_ID.equals(action)) || ACTION_SHORTCUT_CAMERA.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from quick settings tile or application shortcut for Open Camera: photo mode");
            applicationInterface.setVideoPref(false);
        }
        else if( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileServiceVideo.TILE_ID.equals(action)) || ACTION_SHORTCUT_VIDEO.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from quick settings tile or application shortcut for Open Camera: video mode");
            applicationInterface.setVideoPref(true);
        }
        else if( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileServiceFrontCamera.TILE_ID.equals(action)) || ACTION_SHORTCUT_SELFIE.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from quick settings tile or application shortcut for Open Camera: selfie mode");
            done_facing = true;
            applicationInterface.switchToCamera(true);
        }
        else if( ACTION_SHORTCUT_GALLERY.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from application shortcut for Open Camera: gallery");
            openGallery();
        }
        else if( ACTION_SHORTCUT_SETTINGS.equals(action) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "launching from application shortcut for Open Camera: settings");
            openSettings();
        }

        Bundle extras = this.getIntent().getExtras();
        if( extras != null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "handle intent extra information");
            if( !done_facing ) {
                int camera_facing = extras.getInt("android.intent.extras.CAMERA_FACING", -1);
                if( camera_facing == 0 || camera_facing == 1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "found android.intent.extras.CAMERA_FACING: " + camera_facing);
                    applicationInterface.switchToCamera(camera_facing==1);
                    done_facing = true;
                }
            }
            if( !done_facing ) {
                if( extras.getInt("android.intent.extras.LENS_FACING_FRONT", -1) == 1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "found android.intent.extras.LENS_FACING_FRONT");
                    applicationInterface.switchToCamera(true);
                    done_facing = true;
                }
            }
            if( !done_facing ) {
                if( extras.getInt("android.intent.extras.LENS_FACING_BACK", -1) == 1 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "found android.intent.extras.LENS_FACING_BACK");
                    applicationInterface.switchToCamera(false);
                    done_facing = true;
                }
            }
            if( !done_facing ) {
                if( extras.getBoolean("android.intent.extra.USE_FRONT_CAMERA", false) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "found android.intent.extra.USE_FRONT_CAMERA");
                    applicationInterface.switchToCamera(true);
                    done_facing = true;
                }
            }
        }

        // N.B., in practice the hasSetCameraId() check is pointless as we don't save the camera ID in shared preferences, so it will always
        // be false when application is started from onCreate(), unless resuming from saved instance (in which case we shouldn't be here anyway)
        if( !done_facing && !applicationInterface.hasSetCameraId() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "initialise to back camera");
            // most devices have first camera as back camera anyway so this wouldn't be needed, but some (e.g., LG G6) have first camera
            // as front camera, so we should explicitly switch to back camera
            applicationInterface.switchToCamera(false);
        }
    }

    /** Determine whether we support Camera2 API.
     */
    private void initCamera2Support() {
        if( MyDebug.LOG )
            Log.d(TAG, "initCamera2Support");
        supports_camera2 = false;
        {
            // originally we allowed Camera2 if all cameras support at least LIMITED
            // as of 1.45, we allow Camera2 if at least one camera supports at least LIMITED - this
            // is to support devices that might have a camera with LIMITED or better support, but
            // also a LEGACY camera
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            supports_camera2 = false;
            int n_cameras = manager2.getNumberOfCameras();
            if( n_cameras == 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "Camera2 reports 0 cameras");
                supports_camera2 = false;
            }
            for(int i=0;i<n_cameras && !supports_camera2;i++) {
                if( manager2.allowCamera2Support(i) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera " + i + " has at least limited support for Camera2 API");
                    supports_camera2 = true;
                }
            }
        }

        //test_force_supports_camera2 = true; // test
        if( test_force_supports_camera2 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "forcing supports_camera2");
            supports_camera2 = true;
        }

        if( MyDebug.LOG )
            Log.d(TAG, "supports_camera2? " + supports_camera2);

        // handle the switch from a boolean preference_use_camera2 to String preference_camera_api
        // that occurred in v1.48
        if( supports_camera2 ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if( !sharedPreferences.contains(PreferenceKeys.CameraAPIPreferenceKey) // doesn't have the new key set yet
                    && sharedPreferences.contains("preference_use_camera2") // has the old key set
                    && sharedPreferences.getBoolean("preference_use_camera2", false) // and camera2 was enabled
            ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "transfer legacy camera2 boolean preference to new api option");
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2");
                editor.remove("preference_use_camera2"); // remove the old key, just in case
                editor.apply();
            }
        }
    }

    /** Handles users updating to a version with scoped storage (this could be Android 10 users upgrading
     *  to the version of Open Camera with scoped storage; or users who later upgrade to Android 10).
     *  With scoped storage, we no longer support saving outside of DCIM/ when not using SAF.
     *  This updates if necessary both the current save location, and the save folder history.
     */
    private void checkSaveLocations() {
        if( MyDebug.LOG )
            Log.d(TAG, "checkSaveLocations");
        if( useScopedStorage() ) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean any_changes = false;
            String save_location = getStorageUtils().getSaveLocation();
            CheckSaveLocationResult res = checkSaveLocation(save_location);
            if( !res.res ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "save_…61905 tokens truncated…, can both change depending on
            // device orientation (because application can e.g. be in landscape mode even if device
            // has switched to portrait)
        }
        else if( set_window_insets_listener && !edge_to_edge_mode ) {
            Point display_size = new Point();
            applicationInterface.getDisplaySize(display_size, true);
            int display_width = Math.max(display_size.x, display_size.y);
            int display_height = Math.min(display_size.x, display_size.y);
            double display_aspect_ratio = ((double)display_width)/(double)display_height;
            double preview_aspect_ratio = preview.getCurrentPreviewAspectRatio();
            if( MyDebug.LOG ) {
                Log.d(TAG, "display_aspect_ratio: " + display_aspect_ratio);
                Log.d(TAG, "preview_aspect_ratio: " + preview_aspect_ratio);
            }
            boolean preview_is_wide = preview_aspect_ratio > display_aspect_ratio + 1.0e-5f;
            if( test_preview_want_no_limits ) {
                preview_is_wide = test_preview_want_no_limits_value;
            }
            if( preview_is_wide ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "preview is wide, set want_no_limits");
                this.want_no_limits = true;

                if( !old_want_no_limits ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "need to change to FLAG_LAYOUT_NO_LIMITS");
                    // Ideally we'd just go straight to FLAG_LAYOUT_NO_LIMITS mode, but then all calls to onApplyWindowInsets()
                    // end up returning a value of 0 for the navigation_gap! So we need to wait until we know the navigation_gap.
                    if( navigation_gap != 0 ) {
                        // already have navigation gap, can go straight into no limits mode
                        if( MyDebug.LOG )
                            Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS");
                        showUnderNavigation(true);
                        // need to layout the UI again due to now taking the navigation gap into account
                        if( MyDebug.LOG )
                            Log.d(TAG, "layout UI due to changing want_no_limits behaviour");
                        mainUI.layoutUI();
                    }
                    else {
                        if( MyDebug.LOG )
                            Log.d(TAG, "but navigation_gap is 0");
                    }
                }
            }
            else if( old_want_no_limits && navigation_gap != 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "clear FLAG_LAYOUT_NO_LIMITS");
                showUnderNavigation(false);
                // need to layout the UI again due to no longer taking the navigation gap into account
                if( MyDebug.LOG )
                    Log.d(TAG, "layout UI due to changing want_no_limits behaviour");
                mainUI.layoutUI();
            }
        }

        if( this.supportsForceVideo4K() && preview.usingCamera2API() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "using Camera2 API, so can disable the force 4K option");
            this.disableForceVideo4K();
        }
        if( this.supportsForceVideo4K() && preview.getVideoQualityHander().getSupportedVideoSizes() != null ) {
            for(CameraController.Size size : preview.getVideoQualityHander().getSupportedVideoSizes()) {
                if( size.width >= 3840 && size.height >= 2160 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "camera natively supports 4K, so can disable the force option");
                    this.disableForceVideo4K();
                }
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after handling Force 4K option: " + (System.currentTimeMillis() - debug_time));

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        {
            if( MyDebug.LOG )
                Log.d(TAG, "set up zoom");
            if( MyDebug.LOG )
                Log.d(TAG, "has_zoom? " + preview.supportsZoom());
            SeekBar zoomSeekBar = findViewById(R.id.zoom_seekbar);

            if( preview.supportsZoom() ) {
                zoomSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                zoomSeekBar.setMax(preview.getMaxZoom());
                zoomSeekBar.setProgress(preview.getMaxZoom()-preview.getCameraController().getZoom());
                zoomSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    private long last_haptic_time;

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "zoom onProgressChanged: " + progress);
                        // note we zoom even if !fromUser, as various other UI controls (multitouch, volume key zoom)
                        // indirectly set zoom via this method, from setting the zoom slider
                        // if hasSmoothZoom()==true, then the preview already handled zooming to the current value
                        if( !preview.hasSmoothZoom() ) {
                            int new_zoom_factor = preview.getMaxZoom() - progress;
                            if( fromUser && preview.getCameraController() != null ) {
                                float old_zoom_ratio = preview.getZoomRatio();
                                float new_zoom_ratio = preview.getZoomRatio(new_zoom_factor);
                                if( new_zoom_ratio != old_zoom_ratio ) {
                                    last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                                }
                            }
                            preview.zoomTo(new_zoom_factor, false, true);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

                if( sharedPreferences.getBoolean(PreferenceKeys.ShowZoomSliderControlsPreferenceKey, true) ) {
                    if( !mainUI.inImmersiveMode() ) {
                        zoomSeekBar.setVisibility(View.VISIBLE);
                    }
                }
                else {
                    zoomSeekBar.setVisibility(View.INVISIBLE); // should be INVISIBLE not GONE, as the focus_seekbar is aligned to be left to this; in future we might want this similarly for exposure panel
                }
            }
            else {
                zoomSeekBar.setVisibility(View.INVISIBLE); // should be INVISIBLE not GONE, as the focus_seekbar is aligned to be left to this; in future we might want this similarly for the exposure panel
            }
            if( MyDebug.LOG )
                Log.d(TAG, "cameraSetup: time after setting up zoom: " + (System.currentTimeMillis() - debug_time));

            View takePhotoButton = findViewById(R.id.take_photo);
            if( sharedPreferences.getBoolean(PreferenceKeys.ShowTakePhotoPreferenceKey, true) ) {
                if( !mainUI.inImmersiveMode() ) {
                    takePhotoButton.setVisibility(View.VISIBLE);
                }
            }
            else {
                takePhotoButton.setVisibility(View.INVISIBLE);
            }
        }
        {
            if( MyDebug.LOG )
                Log.d(TAG, "set up manual focus");
            setManualFocusSeekbar(false);
            setManualFocusSeekbar(true);
        }
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting up manual focus: " + (System.currentTimeMillis() - debug_time));
        {
            if( preview.supportsISORange()) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set up iso");
                final SeekBar iso_seek_bar = findViewById(R.id.iso_seekbar);
                iso_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                //setProgressSeekbarExponential(iso_seek_bar, preview.getMinimumISO(), preview.getMaximumISO(), preview.getCameraController().getISO());
                manualSeekbars.setProgressSeekbarISO(iso_seek_bar, preview.getMinimumISO(), preview.getMaximumISO(), preview.getCameraController().getISO());
                iso_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    private long last_haptic_time;

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "iso seekbar onProgressChanged: " + progress);
						/*double frac = progress/(double)iso_seek_bar.getMax();
						if( MyDebug.LOG )
							Log.d(TAG, "exposure_time frac: " + frac);
						double scaling = MainActivity.seekbarScaling(frac);
						if( MyDebug.LOG )
							Log.d(TAG, "exposure_time scaling: " + scaling);
						int min_iso = preview.getMinimumISO();
						int max_iso = preview.getMaximumISO();
						int iso = min_iso + (int)(scaling * (max_iso - min_iso));*/
						/*int min_iso = preview.getMinimumISO();
						int max_iso = preview.getMaximumISO();
						int iso = (int)exponentialScaling(frac, min_iso, max_iso);*/
                        // n.b., important to update even if fromUser==false (e.g., so this works when user changes ISO via clicking
                        // the ISO buttons rather than moving the slider directly, see MainUI.setupExposureUI())
                        preview.setISO( manualSeekbars.getISO(progress) );
                        mainUI.updateSelectedISOButton();
                        if( fromUser ) {
                            last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                if( preview.supportsExposureTime() ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "set up exposure time");
                    final SeekBar exposure_time_seek_bar = findViewById(R.id.exposure_time_seekbar);
                    exposure_time_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                    //setProgressSeekbarExponential(exposure_time_seek_bar, preview.getMinimumExposureTime(), preview.getMaximumExposureTime(), preview.getCameraController().getExposureTime());
                    manualSeekbars.setProgressSeekbarShutterSpeed(exposure_time_seek_bar, preview.getMinimumExposureTime(), preview.getMaximumExposureTime(), preview.getCameraController().getExposureTime());
                    exposure_time_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                        private long last_haptic_time;

                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "exposure_time seekbar onProgressChanged: " + progress);
							/*double frac = progress/(double)exposure_time_seek_bar.getMax();
							if( MyDebug.LOG )
								Log.d(TAG, "exposure_time frac: " + frac);
							long min_exposure_time = preview.getMinimumExposureTime();
							long max_exposure_time = preview.getMaximumExposureTime();
							long exposure_time = exponentialScaling(frac, min_exposure_time, max_exposure_time);*/
                            preview.setExposureTime( manualSeekbars.getExposureTime(progress) );
                            if( fromUser ) {
                                last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });
                }
            }
        }
        setManualWBSeekbar();
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting up iso: " + (System.currentTimeMillis() - debug_time));
        {
            exposure_seekbar_values = null;
            if( preview.supportsExposures() ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "set up exposure compensation");
                final int min_exposure = preview.getMinimumExposure();
                SeekBar exposure_seek_bar = findViewById(R.id.exposure_seekbar);
                exposure_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state

                final int exposure_seekbar_n_repeated_zero = 3; // how many times to repeat 0 for R.id.exposure_seekbar, so that it "sticks" to zero when changing seekbar

                //exposure_seek_bar.setMax( preview.getMaximumExposure() - min_exposure + exposure_seekbar_n_repeated_zero-1 );
                //exposure_seek_bar.setProgress( preview.getCurrentExposure() - min_exposure );

                exposure_seekbar_values = new ArrayList<>();
                int current_exposure = preview.getCurrentExposure();
                int current_progress = 0;
                for(int i=min_exposure;i<=preview.getMaximumExposure();i++) {
                    exposure_seekbar_values.add(i);
                    if( i == 0 ) {
                        exposure_seekbar_values_zero = exposure_seekbar_values.size()-1;
                        exposure_seekbar_values_zero += (exposure_seekbar_n_repeated_zero-1)/2; // centre within the region of zeroes
                        for(int j=0;j<exposure_seekbar_n_repeated_zero-1;j++) {
                            exposure_seekbar_values.add(i);
                        }
                    }
                    if( i == current_exposure ) {
                        if( i == 0 ) {
                            current_progress += exposure_seekbar_values_zero;
                        }
                        else {
                            current_progress = exposure_seekbar_values.size()-1;
                        }
                    }
                }
                exposure_seek_bar.setMax( exposure_seekbar_values.size()-1 );
                exposure_seek_bar.setProgress( current_progress );
                exposure_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    private long last_haptic_time;

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "exposure seekbar onProgressChanged: " + progress);
                        if( exposure_seekbar_values == null ) {
                            Log.e(TAG, "exposure_seekbar_values is null");
                            return;
                        }
                        int new_exposure = getExposureSeekbarValue(progress);
                        if( fromUser ) {
                            // check if not scrolling past the repeated zeroes
                            if( preview.getCurrentExposure() != new_exposure ) {
                                last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                            }
                        }
                        preview.setExposure(new_exposure);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting up exposure: " + (System.currentTimeMillis() - debug_time));

        // On-screen icons such as exposure lock, white balance lock, face detection etc are made visible if necessary in
        // MainUI.showGUI()
        // However still need to enable visibility of icons where visibility depends on camera setup - e.g., exposure button
        // not supported for high speed video frame rates - see testTakeVideoFPSHighSpeedManual().
        // (Disabling is done in checkDisableGUIIcons(), called below.)
        View exposureButton = findViewById(R.id.exposure);
        //exposureButton.setVisibility(supportsExposureButton() && !mainUI.inImmersiveMode() ? View.VISIBLE : View.GONE);
        if( supportsExposureButton() && !mainUI.inImmersiveMode() )
            exposureButton.setVisibility(View.VISIBLE);

        // needed as availability of some icons is per-camera (e.g., flash, RAW)
        // for making icons visible, this is done elsewhere in call to MainUI.showGUI()
        if( checkDisableGUIIcons() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "cameraSetup: need to layoutUI as we hid some icons");
            mainUI.layoutUI();
        }

        // need to update some icons, e.g., white balance and exposure lock due to them being turned off when pause/resuming
        mainUI.updateOnScreenIcons();

        mainUI.setPopupIcon(); // needed so that the icon is set right even if no flash mode is set when starting up camera (e.g., switching to front camera with no flash)
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting popup icon: " + (System.currentTimeMillis() - debug_time));

        mainUI.setTakePhotoIcon();
        mainUI.setSwitchCameraContentDescription();
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: time after setting take photo icon: " + (System.currentTimeMillis() - debug_time));

        if( !block_startup_toast ) {
            this.showPhotoVideoToast(false);
        }
        block_startup_toast = false;
        if( MyDebug.LOG )
            Log.d(TAG, "cameraSetup: total time for cameraSetup: " + (System.currentTimeMillis() - debug_time));

        this.getApplicationInterface().getDrawPreview().setDimPreview(false);

        if( push_switched_camera ) {
            push_switched_camera = false;
            View switchCameraButton = findViewById(R.id.switch_camera);
            switchCameraButton.animate().rotationBy(180).setDuration(250).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }
    }

    public static long performHapticFeedback(SeekBar seekBar, long last_haptic_time) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(seekBar.getContext());
        if( sharedPreferences.getBoolean(PreferenceKeys.AllowHapticFeedbackPreferenceKey, true) ) {
            long time_ms = System.currentTimeMillis();
            if( time_ms > last_haptic_time + 16 ) {
                last_haptic_time = time_ms;
                // SEGMENT_TICK or SEGMENT_TICK doesn't work on Galaxy S24+ at least, even though on Android 14!
                /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ) {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK);
                }
                else*/ {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
        }
        return last_haptic_time;
    }

    public void setManualFocusSeekbarProgress(final boolean is_target_distance, float focus_distance) {
        final SeekBar focusSeekBar = findViewById(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar);
        ManualSeekbars.setProgressSeekbarScaled(focusSeekBar, 0.0, preview.getMinimumFocusDistance(), focus_distance);
    }

    private void setManualFocusSeekbar(final boolean is_target_distance) {
        if( MyDebug.LOG )
            Log.d(TAG, "setManualFocusSeekbar");
        final SeekBar focusSeekBar = findViewById(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar);
        focusSeekBar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
        setManualFocusSeekbarProgress(is_target_distance, is_target_distance ? preview.getCameraController().getFocusBracketingTargetDistance() : preview.getCameraController().getFocusDistance());
        focusSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            private boolean has_saved_zoom;
            private int saved_zoom_factor;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if( !is_target_distance && applicationInterface.isFocusBracketingSourceAutoPref() ) {
                    // source is set from continuous focus, not by changing the seekbar
                    if( fromUser ) {
                        // but if user has manually changed, then exit auto mode
                        applicationInterface.setFocusBracketingSourceAutoPref(false);
                        mainUI.destroyPopup(); // need to recreate popup
                    }
                    else {
                        return;
                    }
                }
                double frac = progress/(double)focusSeekBar.getMax();
                double scaling = ManualSeekbars.seekbarScaling(frac);
                float focus_distance = (float)(scaling * preview.getMinimumFocusDistance());
                preview.setFocusDistance(focus_distance, is_target_distance, true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if( MyDebug.LOG )
                    Log.d(TAG, "manual focus seekbar: onStartTrackingTouch");
                has_saved_zoom = false;
                if( preview.supportsZoom() ) {
                    int focus_assist = applicationInterface.getFocusAssistPref();
                    if( focus_assist > 0 && preview.getCameraController() != null ) {
                        has_saved_zoom = true;
                        saved_zoom_factor = preview.getCameraController().getZoom();
                        if( MyDebug.LOG )
                            Log.d(TAG, "zoom by " + focus_assist + " for focus assist, zoom factor was: " + saved_zoom_factor);
                        int new_zoom_factor = preview.getScaledZoomFactor(focus_assist);
                        preview.getCameraController().setZoom(new_zoom_factor);
                    }
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if( MyDebug.LOG )
                    Log.d(TAG, "manual focus seekbar: onStopTrackingTouch");
                if( has_saved_zoom && preview.getCameraController() != null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "unzoom for focus assist, zoom factor was: " + saved_zoom_factor);
                    preview.getCameraController().setZoom(saved_zoom_factor);
                }
                preview.stoppedSettingFocusDistance(is_target_distance);
            }
        });
        setManualFocusSeekBarVisibility(is_target_distance);
    }

    public boolean showManualFocusSeekbar(final boolean is_target_distance) {
        if( (applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.FocusBracketing) && !preview.isVideo() ) {
            return true; // both seekbars shown in focus bracketing mode
        }
        if( is_target_distance ) {
            return false; // target seekbar only shown in focus bracketing mode
        }
        boolean is_visible = preview.getCurrentFocusValue() != null && this.getPreview().getCurrentFocusValue().equals("focus_mode_manual2");
        return is_visible;
    }

    void setManualFocusSeekBarVisibility(final boolean is_target_distance) {
        boolean is_visible = showManualFocusSeekbar(is_target_distance);
        SeekBar focusSeekBar = findViewById(is_target_distance ? R.id.focus_bracketing_target_seekbar : R.id.focus_seekbar);
        final int visibility = is_visible ? View.VISIBLE : View.GONE;
        focusSeekBar.setVisibility(visibility);
        if( is_visible ) {
            applicationInterface.getDrawPreview().updateSettings(); // needed so that we reset focus_seekbars_margin_left, as the focus seekbars can only be updated when visible
        }
    }

    public void setManualWBSeekbar() {
        if( MyDebug.LOG )
            Log.d(TAG, "setManualWBSeekbar");
        if( preview.getSupportedWhiteBalances() != null && preview.supportsWhiteBalanceTemperature() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "set up manual white balance");
            SeekBar white_balance_seek_bar = findViewById(R.id.white_balance_seekbar);
            white_balance_seek_bar.setOnSeekBarChangeListener(null); // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
            final int minimum_temperature = preview.getMinimumWhiteBalanceTemperature();
            final int maximum_temperature = preview.getMaximumWhiteBalanceTemperature();
			/*
			// white balance should use linear scaling
			white_balance_seek_bar.setMax(maximum_temperature - minimum_temperature);
			white_balance_seek_bar.setProgress(preview.getCameraController().getWhiteBalanceTemperature() - minimum_temperature);
			*/
            manualSeekbars.setProgressSeekbarWhiteBalance(white_balance_seek_bar, minimum_temperature, maximum_temperature, preview.getCameraController().getWhiteBalanceTemperature());
            white_balance_seek_bar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                private long last_haptic_time;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "white balance seekbar onProgressChanged: " + progress);
                    //int temperature = minimum_temperature + progress;
                    //preview.setWhiteBalanceTemperature(temperature);
                    preview.setWhiteBalanceTemperature( manualSeekbars.getWhiteBalanceTemperature(progress) );
                    if( fromUser ) {
                        last_haptic_time = performHapticFeedback(seekBar, last_haptic_time);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }

    public boolean supportsAutoStabilise() {
        if( applicationInterface.isRawOnly() )
            return false; // if not saving JPEGs, no point having auto-stabilise mode, as it won't affect the RAW images
        if( applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama )
            return false; // not supported in panorama mode
        return this.supports_auto_stabilise;
    }

    /** Returns whether the device supports auto-level at all. Most callers probably want to use
     *  supportsAutoStabilise() which also checks whether auto-level is allowed with current options.
     */
    public boolean deviceSupportsAutoStabilise() {
        return this.supports_auto_stabilise;
    }

    public boolean supportsDRO() {
        if( applicationInterface.isRawOnly(MyApplicationInterface.PhotoMode.DRO) )
            return false; // if not saving JPEGs, no point having DRO mode, as it won't affect the RAW images
        return true;
    }

    public boolean supportsHDR() {
        // we also require the device have sufficient memory to do the processing
        return large_heap_memory >= 128 && preview.supportsExpoBracketing();
    }

    public boolean supportsExpoBracketing() {
        if( applicationInterface.isImageCaptureIntent() )
            return false; // don't support expo bracketing mode if called from image capture intent
        return preview.supportsExpoBracketing();
    }

    public boolean supportsFocusBracketing() {
        if( applicationInterface.isImageCaptureIntent() )
            return false; // don't support focus bracketing mode if called from image capture intent
        return preview.supportsFocusBracketing();
    }

    /** Whether we support the auto mode for setting source focus distance for focus bracketing mode.
     *  Note the caller should still separately call supportsFocusBracketing() to see if focus
     *  bracketing is supported in the first place.
     */
    public boolean supportsFocusBracketingSourceAuto() {
        return preview.supportsFocus() && preview.getSupportedFocusValues().contains("focus_mode_continuous_picture");
    }

    public boolean supportsPanorama() {
        // don't support panorama mode if called from image capture intent
        // in theory this works, but problem that currently we'd end up doing the processing on the UI thread, so risk ANR
        if( applicationInterface.isImageCaptureIntent() )
            return false;
        // require 256MB just to be safe, due to the large number of images that may be created
        // remember to update the FAQ "Why isn't Panorama supported on my device?" if this changes
        return large_heap_memory >= 256 && applicationInterface.getGyroSensor().hasSensors();
        //return false; // currently blocked for release
    }

    public boolean supportsFastBurst() {
        if( applicationInterface.isImageCaptureIntent() )
            return false; // don't support burst mode if called from image capture intent
        // require 512MB just to be safe, due to the large number of images that may be created
        return( preview.usingCamera2API() && large_heap_memory >= 512 && preview.supportsBurst() );
    }

    public boolean supportsNoiseReduction() {
        // we require Android 7 to limit to more modern devices (for performance reasons)
        return( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && preview.usingCamera2API() && large_heap_memory >= 512 && preview.supportsBurst() && preview.supportsExposureTime() );
        //return false; // currently blocked for release
    }

    /** Whether the Camera vendor extension is supported (see
     * https://developer.android.com/reference/android/hardware/camera2/CameraExtensionCharacteristics ).
     */
    public boolean supportsCameraExtension(int extension) {
        return preview.supportsCameraExtension(extension);
    }

    /** Whether RAW mode would be supported for various burst modes (expo bracketing etc).
     *  Note that caller should still separately check preview.supportsRaw() if required.
     */
    public boolean supportsBurstRaw() {
        return( large_heap_memory >= 512 );
    }

    public boolean supportsOptimiseFocusLatency() {
        // whether to support optimising focus for latency
        // in theory this works on any device, as well as old or Camera2 API, but restricting this for now to avoid risk of poor default behaviour
        // on older devices
        return( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && preview.usingCamera2API() );
    }

    public boolean supportsPreviewBitmaps() {
        // In practice we only use TextureView on Android 5+ (with Camera2 API enabled) anyway, but have put an explicit check here -
        return preview.getView() instanceof TextureView && large_heap_memory >= 128;
    }

    public boolean supportsPreShots() {
        // Need at least Android 5+ for TextureView
        // Need at least Android 8+ for video encoding classes
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && preview.getView() instanceof TextureView && large_heap_memory >= 512;
    }

    private int maxExpoBracketingNImages() {
        return preview.maxExpoBracketingNImages();
    }

    public boolean supportsForceVideo4K() {
        return this.supports_force_video_4k;
    }

    public boolean supportsCamera2() {
        return this.supports_camera2;
    }

    private void disableForceVideo4K() {
        this.supports_force_video_4k = false;
    }

    public Preview getPreview() {
        return this.preview;
    }

    public boolean isCameraInBackground() {
        return this.camera_in_background;
    }

    public boolean isAppPaused() {
        return this.app_is_paused;
    }

    public BluetoothRemoteControl getBluetoothRemoteControl() {
        return bluetoothRemoteControl;
    }

    public PermissionHandler getPermissionHandler() {
        return permissionHandler;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public MainUI getMainUI() {
        return this.mainUI;
    }

    public ManualSeekbars getManualSeekbars() {
        return this.manualSeekbars;
    }

    public MyApplicationInterface getApplicationInterface() {
        return this.applicationInterface;
    }

    public TextFormatter getTextFormatter() {
        return this.textFormatter;
    }

    SoundPoolManager getSoundPoolManager() {
        return this.soundPoolManager;
    }

    public LocationSupplier getLocationSupplier() {
        return this.applicationInterface.getLocationSupplier();
    }

    public StorageUtils getStorageUtils() {
        return this.applicationInterface.getStorageUtils();
    }

    public File getImageFolder() {
        return this.applicationInterface.getStorageUtils().getImageFolder();
    }

    public ToastBoxer getChangedAutoStabiliseToastBoxer() {
        return changed_auto_stabilise_toast;
    }

    private String getPhotoModeString(MyApplicationInterface.PhotoMode photo_mode, boolean string_for_std) {
        String photo_mode_string = null;
        switch( photo_mode ) {
            case Standard:
                if( string_for_std )
                    photo_mode_string = getResources().getString(R.string.photo_mode_standard_full);
                break;
            case DRO:
                photo_mode_string = getResources().getString(R.string.photo_mode_dro);
                break;
            case HDR:
                photo_mode_string = getResources().getString(R.string.photo_mode_hdr);
                break;
            case ExpoBracketing:
                photo_mode_string = getResources().getString(R.string.photo_mode_expo_bracketing_full);
                break;
            case FocusBracketing: {
                photo_mode_string = getResources().getString(R.string.photo_mode_focus_bracketing_full);
                int n_images = applicationInterface.getFocusBracketingNImagesPref();
                photo_mode_string += " (" + n_images + ")";
                break;
            }
            case FastBurst: {
                photo_mode_string = getResources().getString(R.string.photo_mode_fast_burst_full);
                int n_images = applicationInterface.getBurstNImages();
                photo_mode_string += " (" + n_images + ")";
                break;
            }
            case NoiseReduction:
                photo_mode_string = getResources().getString(R.string.photo_mode_noise_reduction_full);
                break;
            case Panorama:
                photo_mode_string = getResources().getString(R.string.photo_mode_panorama_full);
                break;
            case X_Auto:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_auto_full);
                break;
            case X_HDR:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_hdr_full);
                break;
            case X_Night:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_night_full);
                break;
            case X_Bokeh:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_bokeh_full);
                break;
            case X_Beauty:
                photo_mode_string = getResources().getString(R.string.photo_mode_x_beauty_full);
                break;
        }
        return photo_mode_string;
    }

    /** Displays a toast with information about the current preferences.
     *  If always_show is true, the toast is always displayed; otherwise, we only display
     *  a toast if it's important to notify the user (i.e., unusual non-default settings are
     *  set). We want a balance between not pestering the user too much, whilst also reminding
     *  them if certain settings are on.
     */
    private void showPhotoVideoToast(boolean always_show) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "showPhotoVideoToast");
            Log.d(TAG, "always_show? " + always_show);
        }
        CameraController camera_controller = preview.getCameraController();
        if( camera_controller == null || this.camera_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "camera not open or in background");
            return;
        }
        String toast_string;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean simple = true;
        boolean video_high_speed = preview.isVideoHighSpeed();
        MyApplicationInterface.PhotoMode photo_mode = applicationInterface.getPhotoMode();
        if( preview.isVideo() ) {
            VideoProfile profile = preview.getVideoProfile();

            String extension_string = profile.fileExtension;
            if( !profile.fileExtension.equals("mp4") ) {
                simple = false;
            }

            String bitrate_string;
            if( profile.videoBitRate >= 10000000 )
                bitrate_string = profile.videoBitRate/1000000 + "Mbps";
            else if( profile.videoBitRate >= 10000 )
                bitrate_string = profile.videoBitRate/1000 + "Kbps";
            else
                bitrate_string = profile.videoBitRate + "bps";
            String bitrate_value = applicationInterface.getVideoBitratePref();
            if( !bitrate_value.equals("default") ) {
                simple = false;
            }

            double capture_rate = profile.videoCaptureRate;
            String capture_rate_string = (capture_rate < 9.5f) ? new DecimalFormat("#0.###").format(capture_rate) : String.valueOf((int) (profile.videoCaptureRate + 0.5));
            toast_string = getResources().getString(R.string.video) + ": " + profile.videoFrameWidth + "x" + profile.videoFrameHeight + "\n" +
                    capture_rate_string + getResources().getString(R.string.fps) + (video_high_speed ? " [" + getResources().getString(R.string.high_speed) + "]" : "") + ", " + bitrate_string + " (" + extension_string + ")";

            String fps_value = applicationInterface.getVideoFPSPref();
            if( !fps_value.equals("default") || video_high_speed ) {
                simple = false;
            }

            float capture_rate_factor = applicationInterface.getVideoCaptureRateFactor();
            if( Math.abs(capture_rate_factor - 1.0f) > 1.0e-5 ) {
                toast_string += "\n" + getResources().getString(R.string.preference_video_capture_rate) + ": " + capture_rate_factor + "x";
                simple = false;
            }

            {
                CameraController.TonemapProfile tonemap_profile = applicationInterface.getVideoTonemapProfile();
                if( tonemap_profile != CameraController.TonemapProfile.TONEMAPPROFILE_OFF && preview.supportsTonemapCurve() ) {
                    if( applicationInterface.getVideoTonemapProfile() != CameraController.TonemapProfile.TONEMAPPROFILE_OFF && preview.supportsTonemapCurve() ) {
                        int string_id = 0;
                        switch( tonemap_profile ) {
                            case TONEMAPPROFILE_REC709:
                                string_id = R.string.preference_video_rec709;
                                break;
                            case TONEMAPPROFILE_SRGB:
                                string_id = R.string.preference_video_srgb;
                                break;
                            case TONEMAPPROFILE_LOG:
                                string_id = R.string.video_log;
                                break;
                            case TONEMAPPROFILE_GAMMA:
                                string_id = R.string.preference_video_gamma;
                                break;
                            case TONEMAPPROFILE_JTVIDEO:
                                string_id = R.string.preference_video_jtvideo;
                                break;
                            case TONEMAPPROFILE_JTLOG:
                                string_id = R.string.preference_video_jtlog;
                                break;
                            case TONEMAPPROFILE_JTLOG2:
                                string_id = R.string.preference_video_jtlog2;
                                break;
                        }
                        if( string_id != 0 ) {
                            simple = false;
                            toast_string += "\n" + getResources().getString(string_id);
                            if( tonemap_profile == CameraController.TonemapProfile.TONEMAPPROFILE_GAMMA ) {
                                toast_string += " " + applicationInterface.getVideoProfileGamma();
                            }
                        }
                        else {
                            Log.e(TAG, "unknown tonemap_profile: " + tonemap_profile);
                        }
                    }
                }
            }

            boolean record_audio = applicationInterface.getRecordAudioPref();
            if( !record_audio ) {
                toast_string += "\n" + getResources().getString(R.string.audio_disabled);
                simple = false;
            }
            String max_duration_value = sharedPreferences.getString(PreferenceKeys.VideoMaxDurationPreferenceKey, "0");
            if( !max_duration_value.isEmpty() && !max_duration_value.equals("0") ) {
                String [] entries_array = getResources().getStringArray(R.array.preference_video_max_duration_entries);
                String [] values_array = getResources().getStringArray(R.array.preference_video_max_duration_values);
                int index = Arrays.asList(values_array).indexOf(max_duration_value);
                if( index != -1 ) { // just in case!
                    String entry = entries_array[index];
                    toast_string += "\n" + getResources().getString(R.string.max_duration) +": " + entry;
                    simple = false;
                }
            }
            long max_filesize = applicationInterface.getVideoMaxFileSizeUserPref();
            if( max_filesize != 0 ) {
                toast_string += "\n" + getResources().getString(R.string.max_filesize) +": ";
                if( max_filesize >= 1024*1024*1024 ) {
                    long max_filesize_gb = max_filesize/(1024*1024*1024);
                    toast_string += max_filesize_gb + getResources().getString(R.string.gb_abbreviation);
                }
                else {
                    long max_filesize_mb = max_filesize/(1024*1024);
                    toast_string += max_filesize_mb + getResources().getString(R.string.mb_abbreviation);
                }
                simple = false;
            }
            if( applicationInterface.getVideoFlashPref() && preview.supportsFlash() ) {
                toast_string += "\n" + getResources().getString(R.string.preference_video_flash);
                simple = false;
            }
        }
        else {
            if( photo_mode == MyApplicationInterface.PhotoMode.Panorama ) {
                // don't show resolution in panorama mode
                toast_string = "";
            }
            else {
                toast_string = getResources().getString(R.string.photo);
                CameraController.Size current_size = preview.getCurrentPictureSize();
                toast_string += " " + current_size.width + "x" + current_size.height;
            }

            String photo_mode_string = getPhotoModeString(photo_mode, false);
            if( photo_mode_string != null ) {
                toast_string += (toast_string.isEmpty() ? "" : "\n") + getResources().getString(R.string.photo_mode) + ": " + photo_mode_string;
                if( photo_mode != MyApplicationInterface.PhotoMode.DRO && photo_mode != MyApplicationInterface.PhotoMode.HDR && photo_mode != MyApplicationInterface.PhotoMode.NoiseReduction )
                    simple = false;
            }

            if( preview.supportsFocus() && preview.getSupportedFocusValues().size() > 1 && photo_mode != MyApplicationInterface.PhotoMode.FocusBracketing ) {
                String focus_value = preview.getCurrentFocusValue();
                if( focus_value != null && !focus_value.equals("focus_mode_auto") && !focus_value.equals("focus_mode_continuous_picture") ) {
                    String focus_entry = preview.findFocusEntryForValue(focus_value);
                    if( focus_entry != null ) {
                        toast_string += "\n" + focus_entry;
                    }
                }
            }

            if( applicationInterface.getAutoStabilisePref() ) {
                // important as users are sometimes confused at the behaviour if they don't realise the option is on
                toast_string += (toast_string.isEmpty() ? "" : "\n") + getResources().getString(R.string.preference_auto_stabilise);
                simple = false;
            }
        }
        if( applicationInterface.getFaceDetectionPref() ) {
            // important so that the user realises why touching for focus/metering areas won't work - easy to forget that face detection has been turned on!
            toast_string += "\n" + getResources().getString(R.string.preference_face_detection);
            simple = false;
        }
        if( !video_high_speed ) {
            //manual ISO only supported for high speed video
            String iso_value = applicationInterface.getISOPref();
            if( !iso_value.equals(CameraController.ISO_DEFAULT) ) {
                toast_string += "\nISO: " + iso_value;
                if( preview.supportsExposureTime() ) {
                    long exposure_time_value = applicationInterface.getExposureTimePref();
                    toast_string += " " + preview.getExposureTimeString(exposure_time_value);
                }
                simple = false;
            }
            int current_exposure = camera_controller.getExposureCompensation();
            if( current_exposure != 0 ) {
                toast_string += "\n" + preview.getExposureCompensationString(current_exposure);
                simple = false;
            }
        }
        try {
            String scene_mode = camera_controller.getSceneMode();
            String white_balance = camera_controller.getWhiteBalance();
            String color_effect = camera_controller.getColorEffect();
            if( scene_mode != null && !scene_mode.equals(CameraController.SCENE_MODE_DEFAULT) ) {
                toast_string += "\n" + getResources().getString(R.string.scene_mode) + ": " + mainUI.getEntryForSceneMode(scene_mode);
                simple = false;
            }
            if( white_balance != null && !white_balance.equals(CameraController.WHITE_BALANCE_DEFAULT) ) {
                toast_string += "\n" + getResources().getString(R.string.white_balance) + ": " + mainUI.getEntryForWhiteBalance(white_balance);
                if( white_balance.equals("manual") && preview.supportsWhiteBalanceTemperature() ) {
                    toast_string += " " + camera_controller.getWhiteBalanceTemperature();
                }
                simple = false;
            }
            if( color_effect != null && !color_effect.equals(CameraController.COLOR_EFFECT_DEFAULT) ) {
                toast_string += "\n" + getResources().getString(R.string.color_effect) + ": " + mainUI.getEntryForColorEffect(color_effect);
                simple = false;
            }
        }
        catch(RuntimeException e) {
            // catch runtime error from camera_controller old API from camera.getParameters()
            MyDebug.logStackTrace(TAG, "failed to get info from camera controller", e);
        }
        String lock_orientation = applicationInterface.getLockOrientationPref();
        if( !lock_orientation.equals("none") && photo_mode != MyApplicationInterface.PhotoMode.Panorama ) {
            // panorama locks to portrait, but don't want to display that in the toast
            String [] entries_array = getResources().getStringArray(R.array.preference_lock_orientation_entries);
            String [] values_array = getResources().getStringArray(R.array.preference_lock_orientation_values);
            int index = Arrays.asList(values_array).indexOf(lock_orientation);
            if( index != -1 ) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + entry;
                simple = false;
            }
        }
        String timer = sharedPreferences.getString(PreferenceKeys.TimerPreferenceKey, "0");
        if( !timer.equals("0") && photo_mode != MyApplicationInterface.PhotoMode.Panorama ) {
            String [] entries_array = getResources().getStringArray(R.array.preference_timer_entries);
            String [] values_array = getResources().getStringArray(R.array.preference_timer_values);
            int index = Arrays.asList(values_array).indexOf(timer);
            if( index != -1 ) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + getResources().getString(R.string.preference_timer) + ": " + entry;
                simple = false;
            }
        }
        String repeat = applicationInterface.getRepeatPref();
        if( !repeat.equals("1") ) {
            String [] entries_array = getResources().getStringArray(R.array.preference_burst_mode_entries);
            String [] values_array = getResources().getStringArray(R.array.preference_burst_mode_values);
            int index = Arrays.asList(values_array).indexOf(repeat);
            if( index != -1 ) { // just in case!
                String entry = entries_array[index];
                toast_string += "\n" + getResources().getString(R.string.preference_burst_mode) + ": " + entry;
                simple = false;
            }
        }
		/*if( audio_listener != null ) {
			toast_string += "\n" + getResources().getString(R.string.preference_audio_noise_control);
		}*/

        if( MyDebug.LOG ) {
            Log.d(TAG, "toast_string: " + toast_string);
            Log.d(TAG, "simple?: " + simple);
            Log.d(TAG, "push_info_toast_text: " + push_info_toast_text);
        }
        final boolean use_fake_toast = true;
        if( !simple || always_show ) {
            if( push_info_toast_text != null ) {
                toast_string = push_info_toast_text + "\n" + toast_string;
            }
            preview.showToast(switch_video_toast, toast_string, use_fake_toast);
        }
        else if( push_info_toast_text != null ) {
            preview.showToast(switch_video_toast, push_info_toast_text, use_fake_toast);
        }
        push_info_toast_text = null; // reset
    }

    private void freeAudioListener(boolean wait_until_done) {
        if( MyDebug.LOG )
            Log.d(TAG, "freeAudioListener");
        if( audio_listener != null ) {
            audio_listener.release(wait_until_done);
            audio_listener = null;
        }
        mainUI.audioControlStopped();
    }

    private void startAudioListener() {
        if( MyDebug.LOG )
            Log.d(TAG, "startAudioListener");
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            // we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
            if( MyDebug.LOG )
                Log.d(TAG, "check for record audio permission");
            if( ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "record audio permission not available");
                applicationInterface.requestRecordAudioPermission();
                return;
            }
        }

        MyAudioTriggerListenerCallback callback = new MyAudioTriggerListenerCallback(this);
        audio_listener = new AudioListener(callback);
        if( audio_listener.status() ) {
            preview.showToast(audio_control_toast, R.string.audio_listener_started, true);

            audio_listener.start();
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            String sensitivity_pref = sharedPreferences.getString(PreferenceKeys.AudioNoiseControlSensitivityPreferenceKey, "0");
            int audio_noise_sensitivity;
            switch(sensitivity_pref) {
                case "3":
                    audio_noise_sensitivity = 50;
                    break;
                case "2":
                    audio_noise_sensitivity = 75;
                    break;
                case "1":
                    audio_noise_sensitivity = 125;
                    break;
                case "-1":
                    audio_noise_sensitivity = 150;
                    break;
                case "-2":
                    audio_noise_sensitivity = 200;
                    break;
                case "-3":
                    audio_noise_sensitivity = 400;
                    break;
                default:
                    // default
                    audio_noise_sensitivity = 100;
                    break;
            }
            callback.setAudioNoiseSensitivity(audio_noise_sensitivity);
            mainUI.audioControlStarted();
        }
        else {
            audio_listener.release(true); // shouldn't be needed, but just to be safe
            audio_listener = null;
            preview.showToast(null, R.string.audio_listener_failed);
        }
    }

    public boolean hasAudioControl() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String audio_control = sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none");
        /*if( audio_control.equals("voice") ) {
            return speechControl.hasSpeechRecognition();
        }
        else*/ if( audio_control.equals("noise") ) {
            return true;
        }
        return false;
    }

	/*void startAudioListeners() {
		initAudioListener();
		// no need to restart speech recognizer, as we didn't free it in stopAudioListeners(), and it's controlled by a user button
	}*/

    public void stopAudioListeners() {
        freeAudioListener(true);
        /*if( speechControl.hasSpeechRecognition() ) {
            // no need to free the speech recognizer, just stop it
            speechControl.stopListening();
        }*/
    }

    public void initLocation() {
        if( MyDebug.LOG )
            Log.d(TAG, "initLocation");
        if( app_is_paused ) {
            if( MyDebug.LOG )
                Log.d(TAG, "initLocation: app is paused!");
            // we shouldn't need this (as we only call initLocation() when active), but just in case we end up here after onPause...
            // in fact this happens when we need to grant permission for location - the call to initLocation() from
            // MainActivity.onRequestPermissionsResult()->PermissionsHandler.onRequestPermissionsResult() will be when the application
            // is still paused - so we won't do anything here, but instead initLocation() will be called after when resuming.
        }
        else if( camera_in_background ) {
            if( MyDebug.LOG )
                Log.d(TAG, "initLocation: camera in background!");
            // we will end up here if app is pause/resumed when camera in background (settings, dialog, etc)
        }
        else if( !applicationInterface.getLocationSupplier().setupLocationListener() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "location permission not available, so request permission");
            permissionHandler.requestLocationPermission();
        }
    }

    private void initGyroSensors() {
        if( MyDebug.LOG )
            Log.d(TAG, "initGyroSensors");
        if( applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama ) {
            applicationInterface.getGyroSensor().enableSensors();
        }
        else {
            applicationInterface.getGyroSensor().disableSensors();
        }
    }

    void speak(String text) {
        if( textToSpeech != null && textToSpeechSuccess ) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if( MyDebug.LOG )
            Log.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHandler.onRequestPermissionsResult(requestCode, grantResults);
    }

    public void restartOpenCamera() {
        if( MyDebug.LOG )
            Log.d(TAG, "restartOpenCamera");
        this.waitUntilImageQueueEmpty();
        // see http://stackoverflow.com/questions/2470870/force-application-to-restart-on-first-activity
        Intent intent = this.getBaseContext().getPackageManager().getLaunchIntentForPackage( this.getBaseContext().getPackageName() );
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        this.startActivity(intent);
    }

    public void takePhotoButtonLongClickCancelled() {
        if( MyDebug.LOG )
            Log.d(TAG, "takePhotoButtonLongClickCancelled");
        if( preview.getCameraController() != null && preview.getCameraController().isContinuousBurstInProgress() ) {
            preview.getCameraController().stopContinuousBurst();
        }
    }

    // for testing:
    public SaveLocationHistory getSaveLocationHistory() {
        return this.save_location_history;
    }

    public SaveLocationHistory getSaveLocationHistorySAF() {
        return this.save_location_history_saf;
    }

    public void usedFolderPicker() {
        if( applicationInterface.getStorageUtils().isUsingSAF() ) {
            save_location_history_saf.updateFolderHistory(getStorageUtils().getSaveLocationSAF(), true);
        }
        else {
            save_location_history.updateFolderHistory(getStorageUtils().getSaveLocation(), true);
        }
    }

    public boolean hasThumbnailAnimation() {
        return this.applicationInterface.hasThumbnailAnimation();
    }

    /*public boolean testHasNotification() {
        return has_notification;
    }*/
}
