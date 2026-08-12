package ru.darkcat.camera.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.gallery.GalleryItem;
import ru.darkcat.camera.gallery.GalleryRepository;
import ru.darkcat.camera.vault.VaultRepository;

/** Full-screen-ish local viewer with explicit edit/share/delete actions and swipe navigation. */
public final class MediaViewerActivity extends Activity {
    public static final String EXTRA_GALLERY_SOURCE = "gallery_source";
    public static final String EXTRA_GALLERY_ID = "gallery_item_id";
    private GalleryRepository repository;
    private List<GalleryItem> timeline;
    private int index;
    private FrameLayout canvas;
    private TextView caption;
    private File decryptedSessionFile;
    private VideoView videoView;
    private ImageView imageView;
    private GestureDetector gestures;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new GalleryRepository(this);
        timeline = repository.list();
        String source = getIntent().getStringExtra(EXTRA_GALLERY_SOURCE);
        String id = getIntent().getStringExtra(EXTRA_GALLERY_ID);
        if (id == null) { source = GalleryItem.Source.VAULT.name(); id = getIntent().getStringExtra("media_id"); }
        index = find(source, id);
        if (index < 0) { finish(); return; }
        build(); loadCurrent();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        canvas = new FrameLayout(this); canvas.setBackgroundColor(Color.BLACK);
        root.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1f));
        caption = new TextView(this); caption.setTextColor(Color.WHITE); caption.setTextSize(13f); caption.setPadding(dp(10), dp(5), dp(10), dp(4));
        root.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(android.view.Gravity.CENTER); controls.setBackgroundColor(0xff202124);
        controls.addView(button("‹", v -> move(-1)), weight());
        controls.addView(button("Редактор", v -> edit()), weight());
        controls.addView(button("Поделиться", v -> share()), weight());
        controls.addView(button("Удалить", v -> confirmDelete()), weight());
        controls.addView(button("›", v -> move(1)), weight());
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onFling(MotionEvent start, MotionEvent end, float vx, float vy) {
                if (start == null || end == null || Math.abs(start.getX() - end.getX()) < dp(72)
                        || Math.abs(vx) < Math.abs(vy)) return false;
                move(start.getX() > end.getX() ? 1 : -1); return true;
            }
        });
        canvas.setOnTouchListener((v, event) -> gestures.onTouchEvent(event));
    }

    private void loadCurrent() {
        cleanupCurrent(); canvas.removeAllViews();
        GalleryItem item = timeline.get(index);
        try {
            if (item.isVideo()) {
                videoView = new VideoView(this);
                if (item.source == GalleryItem.Source.VAULT) {
                    decryptedSessionFile = new VaultRepository(this).decryptToCache(item.vaultRecord);
                    videoView.setVideoPath(decryptedSessionFile.getAbsolutePath());
                } else videoView.setVideoURI(item.publicUri);
                canvas.addView(videoView, new FrameLayout.LayoutParams(-1, -1)); videoView.start();
            } else {
                imageView = new ImageView(this); imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (item.source == GalleryItem.Source.VAULT) {
                    decryptedSessionFile = new VaultRepository(this).decryptToCache(item.vaultRecord);
                    imageView.setImageURI(android.net.Uri.fromFile(decryptedSessionFile));
                } else imageView.setImageURI(item.publicUri);
                canvas.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
            }
            String number = item.sequenceNumber > 0 ? String.format(java.util.Locale.US, "№%05d", item.sequenceNumber) : "Без номера";
            caption.setText(number + " · " + item.displayName + " · "
                    + DateFormat.getDateTimeInstance().format(new Date(item.createdAt)) + " · "
                    + (item.source == GalleryItem.Source.VAULT ? "Vault" : "Галерея"));
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось открыть снимок", Toast.LENGTH_LONG).show(); finish();
        }
    }

    private void move(int offset) {
        if (timeline.isEmpty()) return;
        int next = index + offset;
        if (next < 0 || next >= timeline.size()) return;
        index = next; loadCurrent();
    }

    private void edit() {
        GalleryItem item = timeline.get(index);
        if (item.isVideo()) { Toast.makeText(this, "Редактор доступен для фото", Toast.LENGTH_SHORT).show(); return; }
        try {
            GalleryRepository.EditorInput input = repository.prepareEditorInput(item);
            startActivity(new Intent(this, EditorActivity.class)
                    .putExtra(EditorActivity.EXTRA_RECOVERY_PATH, input.file.getAbsolutePath())
                    .putExtra(EditorActivity.EXTRA_DISPLAY_NAME, input.displayName)
                    .putExtra(EditorActivity.EXTRA_MIME, input.mimeType)
                    .putExtra(EditorActivity.EXTRA_EDITOR_SOURCE, input.source.name())
                    .putExtra(EditorActivity.EXTRA_EDITOR_SOURCE_ID, input.sourceId)
                    .putExtra(CaptureContext.EXTRA_CONTEXT_JSON, input.captureContext.toJson().toString()));
        } catch (Exception error) { Toast.makeText(this, "Не удалось подготовить редактор", Toast.LENGTH_LONG).show(); }
    }

    private void share() {
        GalleryItem item = timeline.get(index);
        try {
            android.net.Uri uri;
            if (item.source == GalleryItem.Source.MEDIASTORE) uri = item.publicUri;
            else {
                File shareFile = new VaultRepository(this).decryptForShare(item.vaultRecord);
                uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", shareFile);
            }
            Intent intent = new Intent(Intent.ACTION_SEND).setType(item.mimeType == null ? "image/jpeg" : item.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Поделиться снимком"));
        } catch (Exception error) { Toast.makeText(this, "Не удалось подготовить общий доступ", Toast.LENGTH_LONG).show(); }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle("Удалить снимок?")
                .setMessage("Будет удалён выбранный локальный оригинал.")
                .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (dialog, which) -> {
                    GalleryItem item = timeline.get(index);
                    if (!repository.delete(item)) { Toast.makeText(this, "Удаление не завершено", Toast.LENGTH_LONG).show(); return; }
                    timeline = repository.list();
                    if (timeline.isEmpty()) { finish(); return; }
                    index = Math.min(index, timeline.size() - 1); loadCurrent();
                }).show();
    }

    private int find(String source, String id) {
        if (id == null) return -1;
        for (int i = 0; i < timeline.size(); i++) {
            GalleryItem item = timeline.get(i);
            if (item.id.equals(id) && item.source.name().equals(source)) return i;
        }
        return -1;
    }

    private void cleanupCurrent() {
        if (videoView != null) videoView.stopPlayback();
        if (imageView != null) imageView.setImageDrawable(null);
        if (decryptedSessionFile != null) new VaultRepository(this).cleanupDecryptedCacheQuietly(decryptedSessionFile);
        decryptedSessionFile = null; videoView = null; imageView = null;
    }
    @Override protected void onDestroy() { cleanupCurrent(); super.onDestroy(); }
    private Button button(String text, View.OnClickListener listener) { Button b = new Button(this); b.setAllCaps(false); b.setText(text); b.setTextSize(11f); b.setOnClickListener(listener); return b; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1f); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
