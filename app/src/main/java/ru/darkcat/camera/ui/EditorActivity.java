package ru.darkcat.camera.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.editor.ObjectEditorView;
import ru.darkcat.camera.gallery.GalleryItem;
import ru.darkcat.camera.gallery.GalleryRepository;
import ru.darkcat.camera.gallery.MediaStoreCaptureStore;
import ru.darkcat.camera.vault.DarkCatCaptureCoordinator;
import ru.darkcat.camera.vault.RecoveryStore;
import ru.darkcat.camera.vault.VaultRepository;

/** Explicit quick editor. It never opens automatically after capture. */
public final class EditorActivity extends Activity {
    public static final String EXTRA_RECOVERY_PATH = "recovery_path";
    public static final String EXTRA_DISPLAY_NAME = "display_name";
    public static final String EXTRA_MIME = "mime";
    public static final String EXTRA_EDITOR_SOURCE = "editor_source";
    public static final String EXTRA_EDITOR_SOURCE_ID = "editor_source_id";

    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private ObjectEditorView editorView;
    private LinearLayout root, contextActions;
    private TextView status;
    private Button undoButton, redoButton;
    private String recoveryPath, editorSource, editorSourceId;
    private boolean saving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        recoveryPath = getIntent().getStringExtra(EXTRA_RECOVERY_PATH);
        editorSource = getIntent().getStringExtra(EXTRA_EDITOR_SOURCE);
        editorSourceId = getIntent().getStringExtra(EXTRA_EDITOR_SOURCE_ID);
        if (recoveryPath == null) { finish(); return; }
        Bitmap bitmap;
        try { bitmap = BitmapFactory.decodeFile(recoveryPath); }
        catch (OutOfMemoryError error) { toast("Недостаточно памяти; исходный снимок сохранён", Toast.LENGTH_LONG); finish(); return; }
        if (bitmap == null) { toast("Не удалось открыть снимок", Toast.LENGTH_LONG); finish(); return; }
        buildUi(bitmap);
    }

    private void buildUi(Bitmap bitmap) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        editorView = new ObjectEditorView(this); editorView.setSource(bitmap);
        root.addView(editorView, new LinearLayout.LayoutParams(-1, 0, 1f));
        status = new TextView(this); status.setTextColor(0xffeeeeee); status.setTextSize(12f); status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12), dp(5), dp(12), dp(5)); status.setBackgroundColor(0xff171717);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this); toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = toolbar();
        addButton(tools, "Обрезка", v -> editorView.setTool(ObjectEditorView.Tool.CROP));
        addButton(tools, "Рисование", v -> editorView.setTool(ObjectEditorView.Tool.FREEHAND));
        addButton(tools, "Линия", v -> editorView.setTool(ObjectEditorView.Tool.LINE));
        addButton(tools, "Прямоугольник", v -> editorView.setTool(ObjectEditorView.Tool.RECTANGLE));
        addButton(tools, "Овал", v -> editorView.setTool(ObjectEditorView.Tool.OVAL));
        addButton(tools, "Стрелка", v -> editorView.setTool(ObjectEditorView.Tool.ARROW));
        addButton(tools, "Текст", v -> promptForText());
        undoButton = addButton(tools, "Отменить", v -> editorView.undo());
        redoButton = addButton(tools, "Повторить", v -> editorView.redo());
        addButton(tools, "Сохранить", v -> save());
        toolsScroll.addView(tools); root.addView(toolsScroll, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView actionsScroll = new HorizontalScrollView(this); actionsScroll.setHorizontalScrollBarEnabled(false);
        contextActions = toolbar(); actionsScroll.addView(contextActions); root.addView(actionsScroll, new LinearLayout.LayoutParams(-1, -2));
        editorView.setListener((description, hasSelection, canUndo, canRedo) -> {
            status.setText(description); undoButton.setEnabled(canUndo); redoButton.setEnabled(canRedo); updateContextActions(hasSelection);
        });
        setContentView(root); editorView.setTool(ObjectEditorView.Tool.SELECT);
    }

    private void updateContextActions(boolean hasSelection) {
        contextActions.removeAllViews();
        if (editorView.getTool() == ObjectEditorView.Tool.CROP) {
            addButton(contextActions, "Применить", v -> { if (!editorView.applyCrop()) toast("Область обрезки слишком мала", Toast.LENGTH_SHORT); });
            addButton(contextActions, "Отмена", v -> editorView.cancelCrop()); return;
        }
        addButton(contextActions, "Выбор", v -> editorView.setTool(ObjectEditorView.Tool.SELECT));
        addButton(contextActions, "Цвет", v -> editorView.cycleSelectionColor());
        addButton(contextActions, "Толщина", v -> editorView.cycleSelectionStroke());
        Button delete = addButton(contextActions, "Удалить объект", v -> editorView.deleteSelection()); delete.setEnabled(hasSelection);
    }

    private void promptForText() {
        EditText input = new EditText(this); input.setSingleLine(false); input.setMaxLines(3); input.setHint("Текст метки");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout holder = new LinearLayout(this); holder.setPadding(dp(18), 0, dp(18), 0); holder.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Добавить текст").setView(holder)
                .setNegativeButton("Отмена", null).setPositiveButton("Добавить", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString().trim(); if (text.isEmpty()) { input.setError("Введите текст"); return; }
            editorView.addText(text); dialog.dismiss();
        }));
        dialog.show(); input.requestFocus(); if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void save() {
        if (saving) return;
        if (editorView.getTool() == ObjectEditorView.Tool.CROP) { toast("Сначала примените или отмените обрезку", Toast.LENGTH_SHORT); return; }
        Bitmap flattened;
        try { flattened = editorView.render(); }
        catch (OutOfMemoryError error) { toast("Недостаточно памяти; исходный снимок сохранён", Toast.LENGTH_LONG); return; }
        if (flattened == null) return;
        saving = true; setEnabledRecursively(root, false);
        status.setText(isPublicGalleryEdit() ? "Сохранение в Галерею…" : "Сохранение в защищённое хранилище…");
        saveExecutor.execute(() -> saveFlattened(flattened));
    }

    private void saveFlattened(Bitmap flattened) {
        File original = new File(recoveryPath);
        VaultRepository vault = new VaultRepository(this);
        boolean publicGallery = isPublicGalleryEdit();
        File outputRoot = publicGallery
                ? new File(getCacheDir(), "darkcat-editor-publish")
                : (isGalleryEditor() ? vault.recoveryDir() : original.getParentFile());
        if (!outputRoot.exists() && !outputRoot.mkdirs()) {
            flattened.recycle();
            runOnUiThread(() -> {
                saving = false;
                setEnabledRecursively(root, true);
                status.setText("Сохранение не завершено; исходный снимок сохранён");
                toast("Ошибка подготовки сохранения; исходный снимок не потерян", Toast.LENGTH_LONG);
            });
            return;
        }
        File edited = new File(outputRoot,
                original.getName() + ".edited-" + UUID.randomUUID() + ".jpg");
        File temporary = new File(edited.getAbsolutePath() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!flattened.compress(Bitmap.CompressFormat.JPEG, 100, output)) throw new java.io.IOException("JPEG encoder rejected bitmap");
                output.flush(); output.getFD().sync();
            } finally { flattened.recycle(); }
            if (!temporary.renameTo(edited)) throw new java.io.IOException("unable to publish edited JPEG");
            RecoveryStore store = vault.recoveryStore();
            RecoveryStore.PendingCapture originalPending = store.get(original);
            String displayName = originalPending == null ? getIntent().getStringExtra(EXTRA_DISPLAY_NAME) : originalPending.displayName;
            String mime = originalPending == null ? getIntent().getStringExtra(EXTRA_MIME) : originalPending.mimeType;
            if (mime == null) mime = "image/jpeg";
            CaptureContext context = CaptureContext.fromIntent(getIntent());
            if (publicGallery) {
                GalleryItem source = new GalleryRepository(this).get(editorSource, editorSourceId);
                int sequence = source == null ? 0 : source.sequenceNumber;
                Bitmap output = BitmapFactory.decodeFile(edited.getAbsolutePath());
                if (output == null) throw new java.io.IOException("edited JPEG cannot be decoded");
                new MediaStoreCaptureStore().saveBitmap(this, output, editedName(displayName), sequence,
                        System.currentTimeMillis(), context);
                if (!edited.delete()) throw new java.io.IOException("editor staging cleanup failed");
            } else {
                if (isGalleryEditor()) {
                    GalleryItem source = new GalleryRepository(this).get(editorSource, editorSourceId);
                    int sequence = source == null ? 0 : source.sequenceNumber;
                    store.markPending(edited, sequence, editedName(displayName), mime, System.currentTimeMillis(),
                            context.toJson().toString(), false);
                } else if (originalPending != null) {
                    store.markPending(edited, originalPending.sequenceNumber, originalPending.displayName,
                            originalPending.mimeType, originalPending.capturedAt, originalPending.captureContextJson, false);
                }
                DarkCatCaptureCoordinator.finalizeEdited(this, edited.getAbsolutePath(), displayName, mime, context);
                if (!isGalleryEditor() && original.exists() && original.delete()) store.clear(original);
            }
            if (isGalleryEditor()) cleanupEditorWorkspaceQuietly(original);
            runOnUiThread(() -> { toast("Снимок сохранён", Toast.LENGTH_SHORT); finish(); });
        } catch (Exception error) {
            if (publicGallery && edited.exists()) { // A failed Gallery publish must never become a Vault recovery item.
                //noinspection ResultOfMethodCallIgnored
                edited.delete();
            }
            runOnUiThread(() -> {
                saving = false; setEnabledRecursively(root, true); editorView.setTool(editorView.getTool());
                status.setText("Сохранение не завершено; исходный снимок сохранён");
                toast("Ошибка сохранения; исходный снимок не потерян", Toast.LENGTH_LONG);
            });
        } finally { if (temporary.exists()) { //noinspection ResultOfMethodCallIgnored
            temporary.delete();
        } }
    }

    @Override public void onBackPressed() {
        if (saving) { toast("Дождитесь завершения сохранения", Toast.LENGTH_SHORT); return; }
        new AlertDialog.Builder(this).setTitle("Закрыть редактор?")
                .setMessage("Правки не будут применены; исходный снимок не изменится.")
                .setNegativeButton("Продолжить", null).setPositiveButton("Закрыть", (d, w) -> EditorActivity.super.onBackPressed()).show();
    }

    @Override protected void onDestroy() {
        if (isFinishing()) {
            saveExecutor.shutdown();
            if (recoveryPath != null) { cleanupEditorWorkspaceQuietly(new File(recoveryPath)); new VaultRepository(this).cleanupDecryptedCacheQuietly(new File(recoveryPath)); }
        }
        super.onDestroy();
    }

    private boolean isGalleryEditor() { return editorSource != null && editorSourceId != null; }
    private boolean isPublicGalleryEdit() { return isGalleryEditor() && GalleryItem.Source.MEDIASTORE.name().equals(editorSource); }
    private static String editedName(String name) {
        String base = name == null || name.trim().isEmpty() ? "DarkCat-edit.jpg" : name.trim(); int dot = base.lastIndexOf('.');
        return dot <= 0 ? base + "-edit.jpg" : base.substring(0, dot) + "-edit" + base.substring(dot);
    }
    private void cleanupEditorWorkspaceQuietly(File file) {
        if (file == null) return;
        try {
            File root = new File(getFilesDir(), "darkcat-editor").getCanonicalFile(); File candidate = file.getCanonicalFile();
            if (root.equals(candidate.getParentFile()) && candidate.getName().endsWith(".jpg")) { //noinspection ResultOfMethodCallIgnored
                candidate.delete();
            }
        } catch (Exception ignored) { }
    }
    private LinearLayout toolbar() { LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setBackgroundColor(0xff242424); return bar; }
    private Button addButton(LinearLayout parent, String title, View.OnClickListener listener) { Button b = new Button(this); b.setAllCaps(false); b.setText(title); b.setTextSize(12f); b.setTextColor(Color.WHITE); b.setMinWidth(dp(76)); b.setOnClickListener(listener); parent.addView(b); return b; }
    private void toast(String text, int duration) { Toast.makeText(this, text, duration).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static void setEnabledRecursively(View view, boolean enabled) { view.setEnabled(enabled); if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) setEnabledRecursively(group.getChildAt(i), enabled); } }
}
