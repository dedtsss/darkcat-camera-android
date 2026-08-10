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
    private java.io.File decryptedSessionFile;
    private VaultRepository repository;
    private VideoView videoView;
    private ImageView imageView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        try {
            MediaRecord record = DarkCatDatabase.get(this).get(
                    getIntent().getStringExtra("media_id"));
            if (record == null) throw new java.io.IOException("vault record missing");
            repository = new VaultRepository(this);
            decryptedSessionFile = repository.decryptToCache(record);
            if (record.mimeType != null && record.mimeType.startsWith("video/")) {
                videoView = new VideoView(this);
                videoView.setVideoPath(decryptedSessionFile.getAbsolutePath());
                videoView.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
                setContentView(videoView);
                videoView.start();
            } else {
                imageView = new ImageView(this);
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setImageURI(android.net.Uri.fromFile(decryptedSessionFile));
                setContentView(imageView);
            }
        } catch (Exception error) {
            finish();
        }
    }

    @Override protected void onDestroy() {
        if (videoView != null) videoView.stopPlayback();
        if (imageView != null) imageView.setImageDrawable(null);
        if (repository != null) repository.cleanupDecryptedCacheQuietly(decryptedSessionFile);
        super.onDestroy();
    }
}
