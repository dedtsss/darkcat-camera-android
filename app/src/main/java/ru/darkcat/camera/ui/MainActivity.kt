package ru.darkcat.camera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.os.Bundle
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ru.darkcat.camera.DarkCatApplication
import ru.darkcat.camera.R
import ru.darkcat.camera.data.CaptureMetadata
import ru.darkcat.camera.data.TempFiles
import ru.darkcat.camera.location.LocationProvider
import ru.darkcat.camera.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { (application as DarkCatApplication).vaultRepository }
    private val locationProvider by lazy { LocationProvider(this) }
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var videoTempFile: File? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var mediaMode = MediaMode.PHOTO
    private var captureMode = CaptureMode.FAST
    private var flashMode = FlashMode.OFF
    private var orientationDegrees = 0
    private var stampEnabled = false
    private var pendingVideoStart = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) bindCamera() else toast(getString(R.string.camera_permission_message)) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { locationProvider.start() }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { pendingVideoStart = false; startVideoRecording() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        TempFiles.cleanupStale(this)
        configureUi()
        if (hasPermission(Manifest.permission.CAMERA)) bindCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        locationProvider.start()
    }

    override fun onPause() {
        if (recording != null) recording?.stop()
        super.onPause()
        locationProvider.stop()
    }

    override fun onDestroy() {
        recording?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun configureUi() {
        binding.photoModeButton.setOnClickListener { selectPhotoMode() }
        binding.videoModeButton.setOnClickListener { selectVideoMode() }
        binding.modeButton.setOnClickListener {
            captureMode = if (captureMode == CaptureMode.FAST) CaptureMode.EDIT else CaptureMode.FAST
            binding.modeButton.text = captureMode.name
        }
        binding.crosshairButton.setOnClickListener {
            binding.crosshair.visibility = if (binding.crosshair.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.gridButton.setOnClickListener {
            binding.gridOverlay.visibility = if (binding.gridOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.stampButton.setOnClickListener {
            stampEnabled = !stampEnabled
            binding.stampButton.text = if (stampEnabled) "Stamp: ON" else "Stamp"
        }
        binding.vaultButton.setOnClickListener { startActivity(Intent(this, VaultActivity::class.java)) }
        binding.switchCameraButton.setOnClickListener { switchCamera() }
        binding.flashButton.setOnClickListener { cycleFlash() }
        binding.advancedButton.setOnClickListener { showExposureControl() }
        binding.shutterButton.setOnClickListener {
            if (mediaMode == MediaMode.PHOTO) capturePhoto() else toggleVideoRecording()
        }
        binding.viewFinder.setOnTouchListener { _, event -> handlePreviewTouch(event) }
        binding.zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) camera?.cameraControl?.setLinearZoom(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        orientationListener.enable()
    }

    private fun selectPhotoMode() {
        if (recording != null) return
        mediaMode = MediaMode.PHOTO
        binding.shutterButton.text = "CAPTURE PHOTO"
    }

    private fun selectVideoMode() {
        if (recording != null) return
        mediaMode = MediaMode.VIDEO
        binding.shutterButton.text = "START VIDEO"
        if (flashMode == FlashMode.ON || flashMode == FlashMode.AUTO) flashMode = FlashMode.OFF
        applyFlash()
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                cameraProvider = future.get()
                val provider = cameraProvider ?: return@runCatching
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                if (!provider.hasCamera(selector)) {
                    toast("Selected camera is not available")
                    return@runCatching
                }
                provider.unbindAll()
                val preview = Preview.Builder().setTargetRotation(currentRotation()).build()
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(currentRotation())
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                        ),
                    )
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                camera = provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
                preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                applyFlash()
            }.onFailure { error -> toast("Camera could not start: ${error.message ?: "unknown error"}") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {
        if (recording != null) return
        val provider = cameraProvider ?: return
        val nextLens = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val selector = CameraSelector.Builder().requireLensFacing(nextLens).build()
        if (!provider.hasCamera(selector)) {
            toast("Front/rear camera is not available")
            return
        }
        lensFacing = nextLens
        bindCamera()
    }

    private fun cycleFlash() {
        flashMode = when (flashMode) {
            FlashMode.OFF -> if (mediaMode == MediaMode.PHOTO) FlashMode.AUTO else FlashMode.TORCH
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
        applyFlash()
    }

    private fun applyFlash() {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
        binding.flashButton.visibility = if (hasFlash) View.VISIBLE else View.INVISIBLE
        binding.flashButton.text = "Flash: ${flashMode.name}"
        camera?.cameraControl?.enableTorch(flashMode == FlashMode.TORCH)
        imageCapture?.flashMode = when (flashMode) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    private fun handlePreviewTouch(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val currentCamera = camera ?: return true
        val point = binding.viewFinder.meteringPointFactory.createPoint(event.x, event.y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        currentCamera.cameraControl.startFocusAndMetering(action)
        return true
    }

    private fun capturePhoto() {
        requestLocationIfNeeded()
        val capture = imageCapture ?: return toast("Camera is not ready")
        val file = TempFiles.create(this, "photo", ".jpg")
        val metadata = captureMetadata()
        val options = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT })
            .build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK); play(MediaActionSound.SHUTTER_CLICK) }
                if (captureMode == CaptureMode.EDIT) {
                    openEditor(file, metadata)
                } else {
                    persistImage(file, metadata)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                toast("Photo capture failed: ${exception.message ?: "unknown error"}")
            }
        })
    }

    private fun persistImage(source: File, metadata: CaptureMetadata) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                repository.storeImage(source, metadata).also { repository.queueUpload(it.id) }
            }.onSuccess { record -> launch(Dispatchers.Main) { toast("Saved #${record.sequenceNumber} to protected vault") } }
                .onFailure { error -> launch(Dispatchers.Main) { toast("Vault save failed: ${error.message}") } }
            source.delete()
        }
    }

    private fun toggleVideoRecording() {
        if (recording != null) {
            binding.shutterButton.text = "FINALIZING"
            recording?.stop()
            return
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            pendingVideoStart = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startVideoRecording()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startVideoRecording() {
        val capture = videoCapture ?: return toast("Video camera is not ready")
        requestLocationIfNeeded()
        val file = TempFiles.create(this, "video", ".mp4")
        videoTempFile = file
        var pending = capture.output.prepareRecording(this, FileOutputOptions.Builder(file).build())
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            if (event is VideoRecordEvent.Start) {
                binding.shutterButton.text = "STOP VIDEO"
            } else if (event is VideoRecordEvent.Finalize) {
                val finishedFile = videoTempFile
                videoTempFile = null
                recording?.close()
                recording = null
                binding.shutterButton.text = "START VIDEO"
                if (!event.hasError() && finishedFile?.exists() == true) {
                    val metadata = captureMetadata()
                    lifecycleScope.launch(Dispatchers.IO) {
                        runCatching {
                            repository.storeVideo(finishedFile, metadata, event.recordingStats.recordedDurationNanos / 1_000_000L)
                                .also { repository.queueUpload(it.id) }
                        }.onSuccess { launch(Dispatchers.Main) { toast("Video saved to protected vault") } }
                            .onFailure { launch(Dispatchers.Main) { toast("Video save failed: ${it.message}") } }
                        finishedFile.delete()
                    }
                } else {
                    finishedFile?.delete()
                    toast("Video capture failed")
                }
            }
        }
    }

    private fun openEditor(file: File, metadata: CaptureMetadata) {
        startActivity(Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.SOURCE_PATH, file.absolutePath)
            putExtra(EditorActivity.METADATA_JSON, ru.darkcat.camera.data.CaptureMetadataCodec.encode(metadata))
        })
    }

    private fun captureMetadata(): CaptureMetadata {
        val location = locationProvider.snapshot()
        return CaptureMetadata(
            originalCaptureTimestamp = System.currentTimeMillis(),
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracy,
            altitudeMeters = location?.takeIf { it.hasAltitude() }?.altitude,
            orientationDegrees = orientationDegrees,
            stampEnabled = stampEnabled,
        )
    }

    private fun requestLocationIfNeeded() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun showExposureControl() {
        val exposureState = camera?.cameraInfo?.exposureState
            ?: return toast("Exposure control is not available")
        if (!exposureState.isExposureCompensationSupported) return toast("Exposure compensation is not supported")
        val seekBar = SeekBar(this).apply {
            max = exposureState.exposureCompensationRange.upper - exposureState.exposureCompensationRange.lower
            progress = exposureState.exposureCompensationIndex - exposureState.exposureCompensationRange.lower
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) camera?.cameraControl?.setExposureCompensationIndex(progress + exposureState.exposureCompensationRange.lower)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        AlertDialog.Builder(this).setTitle("Exposure compensation").setView(seekBar).setPositiveButton("Done", null).show()
    }

    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                orientationDegrees = when {
                    orientation < 45 || orientation >= 315 -> 0
                    orientation < 135 -> 90
                    orientation < 225 -> 180
                    else -> 270
                }
                imageCapture?.targetRotation = currentRotation()
                videoCapture?.targetRotation = currentRotation()
            }
        }
    }

    private fun currentRotation(): Int = windowManager.defaultDisplay.rotation

    private fun hasPermission(permission: String): Boolean = ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private enum class MediaMode { PHOTO, VIDEO }
    private enum class CaptureMode { FAST, EDIT }
    private enum class FlashMode { OFF, AUTO, ON, TORCH }
}
