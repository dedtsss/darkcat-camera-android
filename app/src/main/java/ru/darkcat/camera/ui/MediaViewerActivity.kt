package ru.darkcat.camera.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ru.darkcat.camera.DarkCatApplication
import ru.darkcat.camera.data.MediaRecord
import ru.darkcat.camera.databinding.ActivityViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityViewerBinding
    private val repository by lazy { (application as DarkCatApplication).vaultRepository }
    private var record: MediaRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }
        val id = intent.getStringExtra(MEDIA_ID) ?: return finish()
        record = repository.get(id) ?: return finish()
        binding.editButton.isEnabled = record?.mimeType?.startsWith("image/") == true
        binding.editButton.setOnClickListener { editRecord() }
        loadRecord()
    }

    private fun loadRecord() {
        val current = record ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val bytes = runCatching { repository.decryptToBytes(current) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (bytes == null) {
                    toast("Unable to authenticate/decrypt this media")
                    return@withContext
                }
                if (current.mimeType.startsWith("image/")) {
                    binding.imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    binding.infoText.text = "#${current.sequenceNumber} · ${current.uploadStatus.name}\nGPS: ${current.metadata.latitude ?: "not available"}, ${current.metadata.longitude ?: "not available"}"
                } else {
                    binding.infoText.text = "#${current.sequenceNumber} · encrypted video\nVideo playback will use a streaming decryptor in the next slice."
                }
            }
        }
    }

    private fun editRecord() {
        val current = record ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val temp = runCatching { repository.createEditingTemp(current) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (temp == null) toast("Unable to open editor") else startActivity(Intent(this@MediaViewerActivity, EditorActivity::class.java).apply {
                    putExtra(EditorActivity.SOURCE_PATH, temp.absolutePath)
                    putExtra(EditorActivity.METADATA_JSON, ru.darkcat.camera.data.CaptureMetadataCodec.encode(current.metadata))
                })
            }
        }
    }

    companion object {
        const val MEDIA_ID = "media_id"
    }
}
