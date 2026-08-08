package ru.darkcat.camera.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.VideoView;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.vault.VaultRepository;

public final class MediaViewerActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
        try { MediaRecord record = DarkCatDatabase.get(this).get(getIntent().getStringExtra("media_id")); java.io.File file = new VaultRepository(this).decryptToCache(record); if (record.mimeType.startsWith("video/")) { VideoView view = new VideoView(this); view.setVideoPath(file.getAbsolutePath()); view.setLayoutParams(new ViewGroup.LayoutParams(-1,-1)); setContentView(view); view.start(); } else { ImageView view = new ImageView(this); view.setScaleType(ImageView.ScaleType.FIT_CENTER); view.setImageURI(android.net.Uri.fromFile(file)); setContentView(view); } } catch (Exception error) { finish(); }
    }
}
