package ru.darkcat.camera.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.upload.UploadScheduler;
import ru.darkcat.camera.vault.VaultRepository;

import java.text.DateFormat;
import java.util.Date;

public final class VaultActivity extends Activity {
    private VaultRepository repository;
    @Override public void onCreate(Bundle state) { super.onCreate(state); getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE); repository = new VaultRepository(this); render(); }
    @Override protected void onResume() { super.onResume(); if (repository != null) render(); }
    private void render() {
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(18,18,18,18);
        TextView title = new TextView(this); title.setText("DarkCat Vault • protected gallery"); title.setTextSize(20); list.addView(title);
        TextView info = new TextView(this); info.setText("Encrypted UUID media only. Queue and retry state are stored in SQLite."); list.addView(info);
        for (MediaRecord record : DarkCatDatabase.get(this).list()) addRecord(list, record);
        Button settings = new Button(this); settings.setText("DarkCat settings"); settings.setOnClickListener(v -> startActivity(new Intent(this, DarkCatSettingsActivity.class))); list.addView(settings);
        scroll.addView(list); setContentView(scroll);
    }
    private void addRecord(LinearLayout list, MediaRecord record) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0,22,0,22);
        TextView text = new TextView(this); text.setText("#" + record.sequenceNumber + "  " + record.displayName + "\n" + DateFormat.getDateTimeInstance().format(new Date(record.createdAt)) + "\n" + MediaRecord.metadataSummary(record.metadataJson) + "\nStatus: " + record.status.name()); row.addView(text);
        LinearLayout actions = new LinearLayout(this); Button open = action("Open"); open.setOnClickListener(v -> startActivity(new Intent(this, MediaViewerActivity.class).putExtra("media_id", record.id))); actions.addView(open);
        Button edit = action("Edit"); edit.setOnClickListener(v -> edit(record)); actions.addView(edit);
        Button retry = action("Retry upload"); retry.setOnClickListener(v -> { UploadScheduler.enqueue(this, record.id); Toast.makeText(this,"Upload queued",Toast.LENGTH_SHORT).show(); }); actions.addView(retry);
        Button delete = action("Delete"); delete.setOnClickListener(v -> { repository.delete(record); render(); }); actions.addView(delete); row.addView(actions); list.addView(row);
    }
    private Button action(String title) { Button button = new Button(this); button.setText(title); return button; }
    private void edit(MediaRecord record) { try { java.io.File source = repository.decryptToCache(record); startActivity(new Intent(this, EditorActivity.class).putExtra(EditorActivity.EXTRA_RECOVERY_PATH, source.getAbsolutePath()).putExtra(EditorActivity.EXTRA_DISPLAY_NAME, record.displayName).putExtra(EditorActivity.EXTRA_MIME, record.mimeType)); } catch (Exception error) { Toast.makeText(this,"Could not decrypt media",Toast.LENGTH_LONG).show(); } }
}
