package ru.darkcat.camera.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.core.content.FileProvider;

import ru.darkcat.camera.gallery.GalleryItem;
import ru.darkcat.camera.gallery.GalleryRepository;
import ru.darkcat.camera.vault.VaultRepository;

/** The normal DarkCat gallery: one local timeline for encrypted Vault and MediaStore Gallery. */
public final class GalleryActivity extends Activity {
    private GalleryRepository repository;
    private final ArrayList<GalleryItem> items = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private GridView grid;
    private boolean selectionMode;
    private LinearLayout selectionActions;
    private Button select;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Галерея DarkCat");
        repository = new GalleryRepository(this);
        build();
    }

    @Override protected void onResume() { super.onResume(); if (grid != null) refresh(); }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(14));
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Button all = button("Все кадры", v -> refresh());
        Button points = button("По точкам", v -> startActivity(new Intent(this, PointGalleryActivity.class)));
        Button settings = button("Настройки", v -> startActivity(new Intent(this, DarkCatSettingsRootActivity.class)));
        select = button("Выбрать", v -> toggleSelectionMode());
        nav.addView(all, weight()); nav.addView(points, weight()); nav.addView(select, weight()); nav.addView(settings, weight());
        root.addView(nav, match());
        selectionActions = new LinearLayout(this);
        selectionActions.setGravity(Gravity.CENTER_VERTICAL);
        selectionActions.addView(button("Поделиться", v -> shareSelected()), weight());
        selectionActions.addView(button("Удалить", v -> deleteSelected()), weight());
        root.addView(selectionActions, match());
        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(148));
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(new GalleryAdapter());
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleItem(items.get(position));
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        refresh();
    }

    private void refresh() {
        items.clear();
        items.addAll(repository.list());
        selected.retainAll(keys(items));
        ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
        updateSelectionUi();
    }

    private final class GalleryAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(GalleryActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(4), dp(4), dp(4), dp(6));
                card.setBackgroundColor(0xffeeeeee);
                ImageView image = new ImageView(GalleryActivity.this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(image, new LinearLayout.LayoutParams(-1, dp(120)));
                TextView label = new TextView(GalleryActivity.this);
                label.setTextSize(12f); label.setMaxLines(2); label.setPadding(dp(3), dp(4), dp(3), 0);
                card.addView(label, new LinearLayout.LayoutParams(-1, -2));
                holder = new Holder(image, label); card.setTag(holder); convertView = card;
            } else holder = (Holder) convertView.getTag();
            GalleryItem item = items.get(position);
            holder.image.setImageDrawable(null);
            loadThumbnail(holder.image, item);
            String seq = item.sequenceNumber > 0 ? String.format(Locale.US, "№%05d", item.sequenceNumber) : "Без №";
            holder.label.setText(seq + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(item.createdAt)) + "\n" + (item.source == GalleryItem.Source.VAULT ? "Vault" : "Галерея"));
            convertView.setBackgroundColor(selected.contains(key(item)) ? 0xffb9f6ca : 0xffeeeeee);
            convertView.setOnClickListener(v -> {
                if (selectionMode) toggleItem(item);
                else startActivity(new Intent(GalleryActivity.this, MediaViewerActivity.class)
                        .putExtra(MediaViewerActivity.EXTRA_GALLERY_SOURCE, item.source.name())
                        .putExtra(MediaViewerActivity.EXTRA_GALLERY_ID, item.id));
            });
            return convertView;
        }
    }

    private void loadThumbnail(ImageView target, GalleryItem item) {
        try {
            if (item.source == GalleryItem.Source.MEDIASTORE) {
                target.setImageURI(item.publicUri);
            } else {
                VaultRepository vault = new VaultRepository(this);
                java.io.File thumb = vault.decryptThumbnailToCache(item.vaultRecord);
                if (thumb == null) return;
                Bitmap bitmap = BitmapFactory.decodeFile(thumb.getAbsolutePath());
                target.setImageBitmap(bitmap);
                vault.cleanupDecryptedCacheQuietly(thumb);
            }
        } catch (Exception ignored) { }
    }

    private void toggleSelectionMode() {
        selectionMode = !selectionMode;
        if (!selectionMode) selected.clear();
        ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
        updateSelectionUi();
    }

    private void toggleItem(GalleryItem item) {
        selectionMode = true;
        String key = key(item);
        if (!selected.add(key)) selected.remove(key);
        ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        if (selectionActions == null || select == null) return;
        selectionActions.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        select.setText(selectionMode ? "Готово " + selected.size() : "Выбрать");
    }

    private void shareSelected() {
        List<GalleryItem> selectedItems = selectedItems();
        if (selectedItems.isEmpty()) { toast("Сначала выберите снимки"); return; }
        ArrayList<Uri> uris = new ArrayList<>();
        try {
            for (GalleryItem item : selectedItems) {
                if (item.source == GalleryItem.Source.MEDIASTORE) uris.add(item.publicUri);
                else {
                    File decrypted = new VaultRepository(this).decryptForShare(item.vaultRecord);
                    if (decrypted == null) throw new java.io.IOException("Vault source unavailable");
                    uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", decrypted));
                }
            }
            Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Поделиться снимками"));
        } catch (Exception error) { toast("Не удалось подготовить общий доступ"); }
    }

    private void deleteSelected() {
        List<GalleryItem> selectedItems = selectedItems();
        if (selectedItems.isEmpty()) { toast("Сначала выберите снимки"); return; }
        new AlertDialog.Builder(this).setTitle("Удалить выбранные снимки?")
                .setMessage("Будут удалены " + selectedItems.size() + " локальных оригинала(ов).")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    int removed = 0;
                    for (GalleryItem item : selectedItems) if (repository.delete(item)) removed++;
                    selected.clear(); selectionMode = false; refresh();
                    toast(removed == selectedItems.size() ? "Снимки удалены" : "Удалено: " + removed + " из " + selectedItems.size());
                }).show();
    }

    private List<GalleryItem> selectedItems() {
        ArrayList<GalleryItem> result = new ArrayList<>();
        for (GalleryItem item : items) if (selected.contains(key(item))) result.add(item);
        return result;
    }

    private static Set<String> keys(List<GalleryItem> source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (GalleryItem item : source) result.add(key(item));
        return result;
    }

    private static String key(GalleryItem item) { return item.source.name() + ":" + item.id; }
    private void toast(String text) { android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show(); }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this); button.setAllCaps(false); button.setText(text); button.setOnClickListener(listener); return button;
    }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1f); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class Holder { final ImageView image; final TextView label; Holder(ImageView image, TextView label) { this.image = image; this.label = label; } }
}
