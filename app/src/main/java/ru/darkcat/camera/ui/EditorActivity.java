package ru.darkcat.camera.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import ja.burhanrashid52.photoeditor.PhotoEditor;
import ja.burhanrashid52.photoeditor.PhotoEditorView;
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder;
import ja.burhanrashid52.photoeditor.shape.ShapeType;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.vault.DarkCatCaptureCoordinator;

import java.io.File;

/** In-app photo editor for EDIT mode. Video is deliberately excluded from this screen. */
public final class EditorActivity extends Activity {
    public static final String EXTRA_RECOVERY_PATH = "recovery_path";
    public static final String EXTRA_DISPLAY_NAME = "display_name";
    public static final String EXTRA_MIME = "mime";
    private PhotoEditorView editorView;
    private PhotoEditor editor;
    private String recoveryPath;
    private Bitmap bitmap;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
        recoveryPath = getIntent().getStringExtra(EXTRA_RECOVERY_PATH);
        if (recoveryPath == null) { finish(); return; }
        bitmap = BitmapFactory.decodeFile(recoveryPath);
        if (bitmap == null) { Toast.makeText(this, "Could not open recovery photo", Toast.LENGTH_LONG).show(); finish(); return; }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        editorView = new PhotoEditorView(this); editorView.getSource().setImageBitmap(bitmap);
        editor = new PhotoEditor.Builder(this, editorView).setPinchTextScalable(true).build();
        root.addView(editorView, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout toolbar = new LinearLayout(this); toolbar.setOrientation(LinearLayout.HORIZONTAL); toolbar.setBackgroundColor(0xff202020);
        addButton(toolbar, "Crop", v -> cropCenter()); addButton(toolbar, "Rotate", v -> rotate());
        addButton(toolbar, "Text", v -> editor.addText("Note", Color.WHITE));
        addButton(toolbar, "Arrow", v -> arrow()); addButton(toolbar, "Line", v -> line());
        addButton(toolbar, "Undo", v -> editor.undo()); addButton(toolbar, "Redo", v -> editor.redo());
        addButton(toolbar, "Save", v -> save()); root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void addButton(LinearLayout parent, String title, View.OnClickListener listener) { Button button = new Button(this); button.setText(title); button.setTextSize(10); button.setOnClickListener(listener); parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1)); }
    private void cropCenter() {
        if (bitmap == null) return; int size = Math.min(bitmap.getWidth(), bitmap.getHeight()); int left = (bitmap.getWidth() - size) / 2; int top = (bitmap.getHeight() - size) / 2;
        Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, size, size); editorView.getSource().setImageBitmap(cropped); bitmap.recycle(); bitmap = cropped;
    }
    private void rotate() {
        if (bitmap == null) return; Matrix matrix = new Matrix(); matrix.postRotate(90); Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true); editorView.getSource().setImageBitmap(rotated); bitmap.recycle(); bitmap = rotated;
    }
    private void arrow() { editor.setShape(new ShapeBuilder().withShapeType(new ShapeType.Arrow()).withShapeColor(Color.RED).withShapeSize(8f)); editor.setBrushDrawingMode(true); }
    private void line() { editor.setShape(new ShapeBuilder().withShapeType(ShapeType.Line.INSTANCE).withShapeColor(Color.YELLOW).withShapeSize(6f)); editor.setBrushDrawingMode(true); }
    private void save() {
        String outputPath = recoveryPath + ".edited.jpg";
        editor.saveAsFile(outputPath, new PhotoEditor.OnSaveListener() {
            @Override public void onSuccess(String imagePath) {
                try {
                    DarkCatCaptureCoordinator.finalizeEdited(EditorActivity.this, imagePath, getIntent().getStringExtra(EXTRA_DISPLAY_NAME), getIntent().getStringExtra(EXTRA_MIME), CaptureContext.fromIntent(getIntent()));
                    // The original recovery file is retained until the edited vault commit succeeded.
                    new File(recoveryPath).delete(); new File(imagePath).delete(); finish();
                } catch (Exception error) { Toast.makeText(EditorActivity.this, "Secure save failed; recovery material retained", Toast.LENGTH_LONG).show(); }
            }
            @Override public void onFailure(Exception exception) { Toast.makeText(EditorActivity.this, "Editor save failed; recovery material retained", Toast.LENGTH_LONG).show(); }
        });
    }
    @Override public void onBackPressed() { Toast.makeText(this, "Recovery photo retained. Save or discard explicitly.", Toast.LENGTH_SHORT).show(); }
}
