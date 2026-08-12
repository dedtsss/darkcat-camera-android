package ru.darkcat.camera.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.upload.UploadScheduler;
import ru.darkcat.camera.upload.UploadStateMachine;
import ru.darkcat.camera.vault.DarkCatCaptureCoordinator;
import ru.darkcat.camera.vault.RecoveryStore;
import ru.darkcat.camera.vault.VaultRepository;

/** Protected gallery. The activity never exposes thumbnails or coordinates on the lockscreen. */
public final class VaultActivity extends Activity {
    private VaultRepository repository;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Защищённое хранилище");
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        repository = new VaultRepository(this);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repository != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(16), dp(18), dp(28));

        TextView title = text("Защищённая галерея", 22f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        list.addView(title);
        TextView info = text("Материалы зашифрованы AES-256-GCM и хранятся под случайными UUID-именами. Содержимое экрана защищено от снимков.", 13f);
        info.setPadding(0, dp(4), 0, dp(10));
        list.addView(info);

        List<RecoveryStore.PendingCapture> pendingRecovery = repository.recoveryStore().listPending();
        int externalRecoveryCount = DarkCatCaptureCoordinator.pendingExternalCaptureCount(this);
        int recoveryCount = pendingRecovery.size() + externalRecoveryCount;
        TextView recovery = text("Ожидают восстановления: " + recoveryCount, 15f);
        recovery.setTextColor(recoveryCount > 0 ? 0xffb26a00 : 0xff2e7d32);
        list.addView(recovery);
        if (recoveryCount > 0) {
            Button resume = button("Продолжить обработку recovery", v -> {
                DarkCatCaptureCoordinator.resumePending(this);
                Toast.makeText(this, "Обработка продолжена в фоне", Toast.LENGTH_SHORT).show();
            });
            list.addView(resume, match());
            if (externalRecoveryCount > 0) {
                list.addView(text("Видео ожидают защищённого переноса: "
                        + externalRecoveryCount, 13f));
            }
            for (RecoveryStore.PendingCapture pending : pendingRecovery) {
                addRecovery(list, pending);
            }
        }

        List<MediaRecord> records = DarkCatDatabase.get(this).list();
        for (MediaRecord record : records) addRecord(list, record);
        if (records.isEmpty()) {
            TextView empty = text("Снимков в хранилище пока нет.", 16f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(28));
            list.addView(empty);
        }

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        Button sync = button("Синхронизация", v -> startActivity(new Intent(this, SyncActivity.class)));
        Button settings = button("Настройки", v -> startActivity(new Intent(this, DarkCatSettingsActivity.class)));
        navigation.addView(sync, weight());
        Button points = button("По точкам", v -> startActivity(new Intent(this, PointGalleryActivity.class)));
        navigation.addView(points, weight());
        navigation.addView(settings, weight());
        list.addView(navigation, match());
        scroll.addView(list);
        setContentView(scroll);
    }

    private void addRecovery(LinearLayout list, RecoveryStore.PendingCapture pending) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackgroundColor(0xfffff3e0);
        LinearLayout.LayoutParams params = match();
        params.bottomMargin = dp(7);
        String number = pending.sequenceNumber > 0
                ? "№ " + String.format(Locale.US, "%05d", pending.sequenceNumber)
                : "Без номера";
        card.addView(text(number + " · recovery · "
                + DateFormat.getDateTimeInstance().format(new Date(pending.capturedAt)), 14f));
        card.addView(text(pending.editRequested
                ? "Ожидает решения в редакторе"
                : "Ожидает фоновой обработки", 13f));
        if (pending.editRequested) {
            LinearLayout actions = new LinearLayout(this);
            Button open = button("Открыть редактор", v -> startActivity(new Intent(this, EditorActivity.class)
                    .putExtra(EditorActivity.EXTRA_RECOVERY_PATH, pending.mediaFile.getAbsolutePath())
                    .putExtra(EditorActivity.EXTRA_DISPLAY_NAME, pending.displayName)
                    .putExtra(EditorActivity.EXTRA_MIME, pending.mimeType)
                    .putExtra(ru.darkcat.camera.data.CaptureContext.EXTRA_CONTEXT_JSON,
                            pending.captureContextJson)));
            Button keep = button("Сохранить без правок", v -> {
                try {
                    DarkCatCaptureCoordinator.finalizeRecoveryWithoutEditing(this, pending.mediaFile);
                    Toast.makeText(this, "Recovery поставлен на защищённое сохранение", Toast.LENGTH_SHORT).show();
                    render();
                } catch (Exception error) {
                    Toast.makeText(this, "Recovery остаётся сохранённым; повторите позже", Toast.LENGTH_LONG).show();
                }
            });
            actions.addView(open, weight());
            actions.addView(keep, weight());
            card.addView(actions, match());
        }
        list.addView(card, params);
    }

    private void addRecord(LinearLayout list, MediaRecord record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(11), dp(10), dp(11));
        card.setBackgroundColor(0xfff1f1f1);
        LinearLayout.LayoutParams params = match();
        params.bottomMargin = dp(9);

        String number = record.sequenceNumber > 0
                ? "№ " + String.format(Locale.US, "%05d", record.sequenceNumber) : "Без номера";
        TextView header = text(number + " · " + DateFormat.getDateTimeInstance().format(new Date(record.createdAt)), 15f);
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(header);
        card.addView(text(mediaType(record.mimeType) + " · " + size(record.encryptedSize), 14f));
        String tags = tags(record.metadataJson);
        if (!tags.isEmpty()) card.addView(text("Теги: " + tags, 14f));
        TextView status = text("Состояние: " + status(record.status), 14f);
        if (record.status == MediaRecord.UploadStatus.FAILED_RETRYABLE
                || record.status == MediaRecord.UploadStatus.FAILED_PERMANENT) status.setTextColor(0xffb71c1c);
        card.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button open = button("Открыть", v -> startActivity(new Intent(this, MediaViewerActivity.class)
                .putExtra("media_id", record.id)));
        actions.addView(open, weight());
        if (UploadStateMachine.canEnqueue(record.status)) {
            Button retry = button("В очередь", v -> {
                UploadScheduler.enqueue(this, record.id);
                Toast.makeText(this, "Поставлено в очередь", Toast.LENGTH_SHORT).show();
                render();
            });
            actions.addView(retry, weight());
        }
        card.addView(actions, match());

        Button delete = button("Удалить локальный материал", v -> confirmDelete(record));
        delete.setTextColor(0xffb71c1c);
        card.addView(delete, match());
        list.addView(card, params);
    }

    private void confirmDelete(MediaRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить безвозвратно?")
                .setMessage("Будут удалены зашифрованный оригинал, миниатюра и запись очереди. Это действие нельзя отменить.")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    if (!repository.delete(record)) {
                        Toast.makeText(this, "Не удалось удалить все файлы; запись сохранена",
                                Toast.LENGTH_LONG).show();
                    }
                    render();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private static String mediaType(String mime) {
        return mime != null && mime.startsWith("video/") ? "Видео" : "Фото";
    }

    private static String status(MediaRecord.UploadStatus status) {
        switch (status) {
            case CAPTURED: return "кадр получен";
            case RECOVERY_PENDING: return "восстановление";
            case ENCRYPTED: return "зашифровано";
            case QUEUED: return "ожидает отправки";
            case UPLOADING: return "отправляется";
            case UPLOADED: return "загружено, не проверено";
            case VERIFIED: return "проверено";
            case FAILED_RETRYABLE: return "временная ошибка";
            case FAILED_PERMANENT: return "ошибка, нужен повтор";
            case LOCAL_DELETE_PENDING: return "ожидает локального удаления";
            case LOCAL_DELETED: return "локально удалено";
            default: return status.name();
        }
    }

    private static String tags(String metadata) {
        try {
            JSONArray array = new JSONObject(metadata).optJSONArray("tags");
            if (array == null) return "";
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (value.isEmpty()) continue;
                if (result.length() > 0) result.append(" · ");
                result.append(value);
            }
            return result.toString();
        } catch (Exception ignored) { return ""; }
    }

    private static String size(long bytes) {
        if (bytes < 1024L) return bytes + " Б";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f КБ", bytes / 1024d);
        return String.format(Locale.US, "%.1f МБ", bytes / (1024d * 1024d));
    }

    private TextView text(String value, float size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        return text;
    }

    private Button button(String value, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
