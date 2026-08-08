package ru.darkcat.camera.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.view.ScaleGestureDetector
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ru.darkcat.camera.DarkCatApplication
import ru.darkcat.camera.data.CaptureMetadataCodec
import ru.darkcat.camera.data.TempFiles
import ru.darkcat.camera.databinding.ActivityEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditorBinding
    private val repository by lazy { (application as DarkCatApplication).vaultRepository }
    private lateinit var sourceFile: File
    private var metadataJson: String = ""
    private var bitmap: Bitmap? = null
    private val imageMatrix = Matrix()
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sourceFile = File(intent.getStringExtra(SOURCE_PATH) ?: return finish())
        metadataJson = intent.getStringExtra(METADATA_JSON).orEmpty()
        bitmap = android.graphics.BitmapFactory.decodeFile(sourceFile.absolutePath)
        binding.editorImage.setImageBitmap(bitmap)
        binding.editorImage.scaleType = android.widget.ImageView.ScaleType.MATRIX
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                imageMatrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                binding.editorImage.imageMatrix = imageMatrix
                return true
            }
        })
        binding.editorImage.setOnTouchListener { _, event -> scaleDetector.onTouchEvent(event); true }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancelAndFinish()
        })
        binding.rotateButton.setOnClickListener { rotate() }
        binding.cancelButton.setOnClickListener { cancelAndFinish() }
        binding.saveButton.setOnClickListener { save() }
    }

    private fun rotate() {
        val current = bitmap ?: return
        bitmap = Bitmap.createBitmap(current, 0, 0, current.width, current.height, Matrix().apply { postRotate(90f) }, true)
        if (bitmap !== current) current.recycle()
        imageMatrix.reset()
        binding.editorImage.imageMatrix = imageMatrix
        binding.editorImage.setImageBitmap(bitmap)
    }

    private fun save() {
        binding.saveButton.isEnabled = false
        val current = bitmap ?: return
        val output = TempFiles.create(this, "editor-output", ".jpg")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                output.outputStream().use { stream -> check(current.compress(Bitmap.CompressFormat.JPEG, 95, stream)) }
                val record = repository.storeImage(output, CaptureMetadataCodec.decode(metadataJson))
                repository.queueUpload(record.id)
                sourceFile.delete()
                withContext(Dispatchers.Main) {
                    toast("Edited photo saved #${record.sequenceNumber}")
                    finish()
                }
            } catch (error: Throwable) {
                output.delete()
                withContext(Dispatchers.Main) {
                    binding.saveButton.isEnabled = true
                    toast("Editor save failed: ${error.message}")
                }
            } finally {
                output.delete()
            }
        }
    }

    private fun cancelAndFinish() {
        sourceFile.delete()
        finish()
    }

    companion object {
        const val SOURCE_PATH = "source_path"
        const val METADATA_JSON = "metadata_json"
    }
}
