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
import ru.darkcat.camera.vault.DarkCatCaptureCoordinator;
import ru.darkcat.camera.vault.RecoveryStore;
import ru.darkcat.camera.vault.VaultRepository;

/** Object-based editor for EDIT captures. Video is deliberately excluded from this screen. */
public final class EditorActivity extends Activity {
    public static final String EXTRA_RECOVERY_PATH = "recovery_path";
    public static final String EXTRA_DISPLAY_NAME = "display_name";
    public static final String EXTRA_MIME = "mime";

    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private ObjectEditorView editorView;
    private LinearLayout root;
    private LinearLayout contextActions;
    private TextView status;
    private Button undoButton;
    private Button redoButton;
    private Button colorButton;
    private Button strokeButton;
    private Button deleteButton;
    private String recoveryPath;
    private boolean saving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        recoveryPath = getIntent().getStringExtra(EXTRA_RECOVERY_PATH);
        if (recoveryPath == null) { finish(); return; }
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(recoveryPath);
        } catch (OutOfMemoryError memoryPressure) {
            Toast.makeText(this, "Недостаточно памяти; recovery-снимок сохранён",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (bitmap == null) {
            Toast.makeText(this, "Не удалось открыть recovery-снимок", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildUi(bitmap);
    }

    private void buildUi(Bitmap bitmap) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        editorView = new ObjectEditorView(this);
        editorView.setSource(bitmap);
        root.addView(editorView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextColor(0xffeeeeee);
        status.setTextSize(12f);
        status.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = dp(12);
        status.setPadding(horizontal, dp(5), horizontal, dp(5));
        status.setBackgroundColor(0xff171717);
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = toolbar();
        addButton(tools, "Обрезка", v -> enterCrop());
        addButton(tools, "Рисование", v -> editorView.setTool(ObjectEditorView.Tool.FREEHAND));
        addButton(tools, "Линия", v -> editorView.setTool(ObjectEditorView.Tool.LINE));
        addButton(tools, "Прямоугольник", v -> editorView.setTool(ObjectEditorView.Tool.RECTANGLE));
        addButton(tools, "Овал", v -> editorView.setTool(ObjectEditorView.Tool.OVAL));
        addButton(tools, "Стрелка", v -> editorView.setTool(ObjectEditorView.Tool.ARROW));
        addButton(tools, "Текст", v -> promptForText());
        undoButton = addButton(tools, "Отменить", v -> editorView.undo());
        redoButton = addButton(tools, "Повторить", v -> editorView.redo());
        addButton(tools, "Сохранить", v -> save());
        toolsScroll.addView(tools, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(toolsScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        contextActions = toolbar();
        actionsScroll.addView(contextActions, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(actionsScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        editorView.setListener((description, hasSelection, canUndo, canRedo) -> {
            status.setText(description);
            undoButton.setEnabled(canUndo);
            redoButton.setEnabled(canRedo);
            updateContextActions(hasSelection);
        });
        setContentView(root);
        editorView.setTool(ObjectEditorView.Tool.SELECT);
    }

    private void enterCrop() { editorView.setTool(ObjectEditorView.Tool.CROP); }

    private void updateContextActions(boolean hasSelection) {
        contextActions.removeAllViews();
        if (editorView.getTool() == ObjectEditorView.Tool.CROP) {
            addButton(contextActions, "Применить", v -> {
                if (!editorView.applyCrop()) Toast.makeText(this, "Область обрезки слишком мала", Toast.LENGTH_SHORT).show();
            });
            addButton(contextActions, "Отмена", v -> editorView.cancelCrop());
            return;
        }
        addButton(contextActions, "Выбор", v -> editorView.setTool(ObjectEditorView.Tool.SELECT));
        colorButton = addButton(contextActions, "Цвет", v -> editorView.cycleSelectionColor());
        strokeButton = addButton(contextActions, "Толщина", v -> editorView.cycleSelectionStroke());
        deleteButton = addButton(contextActions, "Удалить объект", v -> editorView.deleteSelection());
        deleteButton.setEnabled(hasSelection);
        // Color and width also define the next object when nothing is selected.
        colorButton.setEnabled(true);
        strokeButton.setEnabled(true);
    }

    private void promptForText() {
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setHint("Текст метки");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int padding = dp(18);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(padding, 0, padding, 0);
        holder.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Добавить текст")
                .setView(holder)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) { input.setError("Введите текст"); return; }
            editorView.addText(text);
            dialog.dismiss();
        }));
        dialog.show();
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private LinearLayout toolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(0xff242424);
        return toolbar;
    }

    private Button addButton(LinearLayout parent, String title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(12f);
        button.setTextColor(Color.WHITE);
        button.setMinWidth(dp(76));
        button.setOnClickListener(listener);
        parent.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private void save() {
        if (saving) return;
        if (editorView.getTool() == ObjectEditorView.Tool.CROP) {
            Toast.makeText(this, "Сначала примените или отмените обрезку", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap flattened;
        try { flattened = editorView.render(); }
        catch (OutOfMemoryError error) {
            Toast.makeText(this, "Недостаточно памяти; recovery-снимок сохранён", Toast.LENGTH_LONG).show();
            return;
        }
        if (flattened == null) return;
        saving = true;
        setEnabledRecursively(root, false);
        status.setText("Сохранение в защищённое хранилище…");
        saveExecutor.execute(() -> saveFlattened(flattened));
    }

    private void saveFlattened(Bitmap flattened) {
        File original = new File(recoveryPath);
        File edited = new File(original.getParentFile(), original.getName() + ".edited-" + UUID.randomUUID() + ".jpg");
        File temporary = new File(edited.getAbsolutePath() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!flattened.compress(Bitmap.CompressFormat.JPEG, 100, output)) {
                    throw new java.io.IOException("JPEG encoder rejected the bitmap");
                }
                output.flush();
                output.getFD().sync();
            } finally {
                flattened.recycle();
            }
            if (!temporary.renameTo(edited)) throw new java.io.IOException("Unable to commit edited recovery file");

            VaultRepository repository = new VaultRepository(this);
            RecoveryStore store = repository.recoveryStore();
            RecoveryStore.PendingCapture originalPending = store.get(original);
            if (originalPending != null) {
                // Duplicate the durable journal before encryption. A crash leaves both recoverable.
                store.markPending(edited, originalPending.sequenceNumber, originalPending.displayName,
                        originalPending.mimeType, originalPending.capturedAt,
                        originalPending.captureContextJson, true);
            }
            String displayName = originalPending == null
                    ? getIntent().getStringExtra(EXTRA_DISPLAY_NAME) : originalPending.displayName;
            String mime = originalPending == null
                    ? getIntent().getStringExtra(EXTRA_MIME) : originalPending.mimeType;
            DarkCatCaptureCoordinator.finalizeEdited(this, edited.getAbsolutePath(), displayName,
                    mime == null ? "image/jpeg" : mime, CaptureContext.fromIntent(getIntent()));

            // finalizeEdited deletes the edited plaintext only after vault+DB commit succeeds.
            if (original.exists() && original.delete()) store.clear(original);
            runOnUiThread(() -> {
                Toast.makeText(this, "Снимок сохранён", Toast.LENGTH_SHORT).show();
                finish();
            });
        } catch (Exception error) {
            // Original and any completed edited recovery stay app-private for retry/recovery UI.
            runOnUiThread(() -> {
                saving = false;
                setEnabledRecursively(root, true);
                editorView.setTool(editorView.getTool());
                //noinspection SetTextI18n -- DarkCat's product UI is intentionally Russian here.
                status.setText("Сохранение не завершено; recovery-материал сохранён");
                Toast.makeText(this, "Ошибка защищённого сохранения; материал не потерян", Toast.LENGTH_LONG).show();
            });
        } finally {
            if (temporary.exists()) { // Partial temporary output has no complete JPEG and is safe to remove.
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    @Override public void onBackPressed() {
        if (saving) {
            Toast.makeText(this, "Дождитесь завершения защищённого сохранения", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Закрыть редактор?")
                .setMessage("Изменения не будут применены. Исходный recovery-снимок останется в защищённой области и не будет удалён.")
                .setNegativeButton("Продолжить редактирование", null)
                .setPositiveButton("Закрыть", (dialog, which) -> EditorActivity.super.onBackPressed())
                .show();
    }

    @Override protected void onDestroy() {
        if (isFinishing()) {
            saveExecutor.shutdown();
            if (recoveryPath != null) {
                new VaultRepository(this).cleanupDecryptedCacheQuietly(new File(recoveryPath));
            }
        }
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static void setEnabledRecursively(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEnabledRecursively(group.getChildAt(i), enabled);
        }
    }
}
