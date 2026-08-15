package ru.darkcat.camera.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import ru.darkcat.camera.diagnostics.CameraDiagnosticsExporter;

@SuppressLint("SetTextI18n") // This Russian-only diagnostics surface is intentionally built in code.
public final class DiagnosticsActivity extends Activity {
    private TextView report;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Диагностика камеры");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        Button export = new Button(this);
        export.setText("Обновить и сохранить JSON");
        export.setOnClickListener(v -> generate());
        layout.addView(export);
        report = new TextView(this);
        report.setTextIsSelectable(true);
        report.setMovementMethod(new ScrollingMovementMethod());
        layout.addView(report, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(layout);
        generate();
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
