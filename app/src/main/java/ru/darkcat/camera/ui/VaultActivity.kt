package ru.darkcat.camera.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import ru.darkcat.camera.DarkCatApplication
import ru.darkcat.camera.R
import ru.darkcat.camera.data.MediaRecord
import ru.darkcat.camera.data.UploadStatus
import ru.darkcat.camera.databinding.ActivityVaultBinding
import java.text.DateFormat
import java.util.Date

class VaultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVaultBinding
    private val repository by lazy { (application as DarkCatApplication).vaultRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render(repository.list())
    }

    private fun render(records: List<MediaRecord>) {
        binding.vaultContainer.removeAllViews()
        if (records.isEmpty()) {
            binding.vaultContainer.addView(TextView(this).apply {
                text = "No protected media yet"
                setTextColor(getColor(R.color.darkcat_muted))
                setPadding(16)
            })
            return
        }
        records.forEach { record -> binding.vaultContainer.addView(createRow(record)) }
    }

    private fun createRow(record: MediaRecord): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(8)
        val thumbnail = ImageView(this@VaultActivity).apply {
            layoutParams = LinearLayout.LayoutParams(104, 104)
            scaleType = ImageView.ScaleType.CENTER_CROP
            repository.thumbnail(record)?.let { bytes -> setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) }
            contentDescription = "Media ${record.sequenceNumber}"
        }
        addView(thumbnail)
        val details = LinearLayout(this@VaultActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(8)
            addView(TextView(this@VaultActivity).apply {
                text = "#${record.sequenceNumber} · ${if (record.mimeType.startsWith("video")) "VIDEO" else "PHOTO"}"
                setTextColor(getColor(R.color.darkcat_text))
            })
            addView(TextView(this@VaultActivity).apply {
                text = "${DateFormat.getDateTimeInstance().format(Date(record.metadata.originalCaptureTimestamp))}\n${record.uploadStatus.display()}"
                setTextColor(getColor(R.color.darkcat_muted))
            })
            addView(Button(this@VaultActivity).apply {
                text = "Open"
                setOnClickListener { startActivity(Intent(this@VaultActivity, MediaViewerActivity::class.java).putExtra(MediaViewerActivity.MEDIA_ID, record.id)) }
            })
        }
        addView(details)
        addView(Button(this@VaultActivity).apply {
            text = "Retry"
            isEnabled = record.uploadStatus == UploadStatus.FAILED_RETRYABLE || record.uploadStatus == UploadStatus.FAILED_PERMANENT
            setOnClickListener { repository.retryUpload(record.id); render(repository.list()) }
        })
        addView(Button(this@VaultActivity).apply {
            text = "Delete"
            setOnClickListener { repository.delete(record.id); render(repository.list()) }
        })
    }

    private fun UploadStatus.display(): String = name.lowercase().replace('_', ' ')
}
