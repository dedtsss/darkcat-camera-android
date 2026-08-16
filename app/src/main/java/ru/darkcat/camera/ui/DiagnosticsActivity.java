package ru.darkcat.camera.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import ru.darkcat.camera.diagnostics.CameraDiagnosticsExporter;
import ru.darkcat.camera.catlog.CatDiagnosticsExporter;
import ru.darkcat.camera.catlog.CatLog;

@SuppressLint("SetTextI18n") // This Russian-only diagnostics surface is intentionally built in code.
public final class DiagnosticsActivity extends Activity {
    private TextView report;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Диагностика / CAT Log");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        TextView catStatus = new TextView(this);
        catStatus.setTextIsSelectable(true);
        layout.addView(catStatus);
        Button start = action("Начать CAT session", v -> { CatLog.startSession(); refreshCatStatus(catStatus); });
        Button stop = action("Остановить CAT session", v -> { CatLog.stopSession(); refreshCatStatus(catStatus); });
        Button note = action("Добавить заметку", v -> addNote(catStatus));
        Button mark = action("Отметить проблему", v -> { CatLog.markProblem(); refreshCatStatus(catStatus); });
        Button exportCat = action("Экспорт диагностики", v -> exportCat(catStatus));
        Button clear = action("Очистить CAT Log", v -> clearCat(catStatus));
        layout.addView(start); layout.addView(stop); layout.addView(note); layout.addView(mark); layout.addView(exportCat); layout.addView(clear);
        Button export = action("Обновить текущий Camera JSON", v -> generate());
        layout.addView(export);
        report = new TextView(this);
        report.setTextIsSelectable(true);
        report.setMovementMethod(new ScrollingMovementMethod());
        layout.addView(report, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(layout);
        refreshCatStatus(catStatus);
        generate();
    }

    private Button action(String label, android.view.View.OnClickListener listener) {
        Button button = new Button(this); button.setAllCaps(false); button.setText(label); button.setOnClickListener(listener); return button;
    }

    private void refreshCatStatus(TextView target) {
        target.setText("CAT Log локально: " + CatLog.status().toString());
    }

    private void addNote(TextView status) {
        EditText input = new EditText(this); input.setHint("Кратко опишите симптом"); input.setSingleLine(false);
        new AlertDialog.Builder(this).setTitle("Заметка CAT Log").setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> { CatLog.note(input.getText().toString()); refreshCatStatus(status); }).show();
    }

    private void clearCat(TextView status) {
        new AlertDialog.Builder(this).setTitle("Очистить CAT Log?")
                .setMessage("Удаляются только локальные диагностические события, не фото и не Vault.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> { CatLog.clear(); refreshCatStatus(status); }).show();
    }

    private void exportCat(TextView status) {
        status.setText("CAT Log: создаётся ZIP…");
        new Thread(() -> {
            try {
                File file = CatDiagnosticsExporter.export(this);
                runOnUiThread(() -> {
                    refreshCatStatus(status);
                    Intent share = new Intent(Intent.ACTION_SEND).setType("application/zip")
                            .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "Экспорт диагностики"));
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Не удалось создать CAT ZIP: " + error.getClass().getSimpleName()));
            }
        }, "darkcat-cat-export").start();
    }

    private void generate() {
        try {
            File file = CameraDiagnosticsExporter.export(this, getIntent().getStringExtra("selected_camera"));
            report.setText(read(file) + "\n\nФайл: " + file.getAbsolutePath());
            Toast.makeText(this, "Диагностика сохранена", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            report.setText("Не удалось создать диагностику: " + error.getMessage());
        }
    }

    private static String read(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
        }
        return new String(bytes, 0, offset, StandardCharsets.UTF_8);
    }
}
