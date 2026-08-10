package ru.darkcat.camera.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.ConnectivityManager;
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

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.upload.UploadQueueSummary;
import ru.darkcat.camera.upload.UploadScheduler;

/** Simple offline-first queue view. Upload state never gates camera capture. */
public final class SyncActivity extends Activity {
    private LinearLayout content;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        setTitle("Синхронизация");
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(28));

        TextView title = text("Синхронизация", 22f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(title);
        TextView provider = text("Провайдер: " + providerLabel(DarkCatSettings.provider(this)), 15f);
        provider.setPadding(0, dp(4), 0, dp(12));
        content.addView(provider);

        UploadQueueSummary summary = DarkCatDatabase.get(this).queueSummary();
        LinearLayout counters = new LinearLayout(this);
        counters.setOrientation(LinearLayout.VERTICAL);
        counters.setPadding(dp(12), dp(10), dp(12), dp(10));
        counters.setBackgroundColor(0xffeeeeee);
        counters.addView(text("В хранилище: " + summary.inVault, 15f));
        counters.addView(text("Ожидают отправки: " + summary.waiting, 15f));
        counters.addView(text("Отправляются: " + summary.uploading, 15f));
        counters.addView(text("Загружено, ожидает проверки: " + summary.uploaded, 15f));
        counters.addView(text("Проверено: " + summary.verified, 15f));
        TextView errors = text("Ошибки: " + summary.errors, 15f);
        if (summary.errors > 0) errors.setTextColor(0xffb71c1c);
        counters.addView(errors);
        long lastSuccess = DarkCatSettings.lastSuccessfulSync(this);
        counters.addView(text("Последняя успешная связь: " + (lastSuccess > 0L
                ? DateFormat.getDateTimeInstance().format(new Date(lastSuccess)) : "—"), 15f));
        content.addView(counters, match());

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button send = button("Отправить сейчас", v -> withNetworkPolicyConfirmation(this::enqueueAll));
        Button retry = button("Повторить ошибки", v -> withNetworkPolicyConfirmation(this::enqueueErrors));
        Button settings = button("Настройки", v -> startActivity(new Intent(this, DarkCatSettingsActivity.class)));
        actions.addView(send, weight());
        actions.addView(retry, weight());
        actions.addView(settings, weight());
        content.addView(actions, match());

        TextView offline = text("Съёмка и защищённая очередь работают без сети. HTTP 2xx сам по себе не удаляет локальный материал.", 13f);
        offline.setPadding(0, dp(10), 0, dp(16));
        content.addView(offline);

        for (MediaRecord record : DarkCatDatabase.get(this).list()) addRecord(record);
        if (DarkCatDatabase.get(this).list().isEmpty()) {
            TextView empty = text("Очередь пуста.", 16f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, 0);
            content.addView(empty);
        }

        scroll.addView(content);
        setContentView(scroll);
    }

    private void enqueueAll() {
        if (providerDisabled()) return;
        UploadScheduler.enqueueAllPending(this);
        Toast.makeText(this, "Очередь передана фоновому отправщику", Toast.LENGTH_SHORT).show();
        render();
    }

    private void enqueueErrors() {
        if (providerDisabled()) return;
        int count = 0;
        for (MediaRecord record : DarkCatDatabase.get(this).list()) {
            if (record.status == MediaRecord.UploadStatus.FAILED_RETRYABLE
                    || record.status == MediaRecord.UploadStatus.FAILED_PERMANENT) {
                UploadScheduler.enqueue(this, record.id);
                count++;
            }
        }
        Toast.makeText(this, count == 0 ? "Ошибок для повтора нет" : "Повтор поставлен в очередь: " + count,
                Toast.LENGTH_SHORT).show();
        render();
    }

    private boolean providerDisabled() {
        String provider = DarkCatSettings.provider(this);
        if (DarkCatSettings.PROVIDER_OFF.equals(provider) || DarkCatSettings.PROVIDER_LOCAL.equals(provider)) {
            Toast.makeText(this, "Сначала выберите провайдер синхронизации", Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    private void withNetworkPolicyConfirmation(Runnable action) {
        if (!DarkCatSettings.wifiOnly(this) || !activeNetworkMetered()) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Использовать мобильный интернет?")
                .setMessage("Включена политика «Только Wi-Fi», а текущая сеть тарифицируется. Отправить эту очередь через мобильную сеть?")
                .setPositiveButton("Использовать", (dialog, which) -> {
                    DarkCatSettings.set(this, "darkcat_wifi_only", false);
                    try { action.run(); }
                    finally { DarkCatSettings.set(this, "darkcat_wifi_only", true); }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private boolean activeNetworkMetered() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return manager == null || manager.isActiveNetworkMetered();
    }

    private void addRecord(MediaRecord record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        LinearLayout.LayoutParams cardParams = match();
        cardParams.bottomMargin = dp(8);
        card.setBackgroundColor(0xfff5f5f5);
        String number = record.sequenceNumber > 0
                ? "№ " + String.format(java.util.Locale.US, "%05d", record.sequenceNumber) : "Без номера";
        TextView header = text(number + " · " + DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT).format(new Date(record.createdAt)), 15f);
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(header);
        card.addView(text("Размер: " + size(record.encryptedSize) + " · " + status(record.status), 14f));
        String tags = tags(record.metadataJson);
        if (!tags.isEmpty()) card.addView(text("Теги: " + tags, 14f));
        if (record.lastError != null && !record.lastError.trim().isEmpty()) {
            TextView error = text("Ошибка: " + record.lastError, 13f);
            error.setTextColor(0xffb71c1c);
            card.addView(error);
        }
        if (record.status == MediaRecord.UploadStatus.FAILED_RETRYABLE
                || record.status == MediaRecord.UploadStatus.FAILED_PERMANENT
                || record.status == MediaRecord.UploadStatus.ENCRYPTED
                || record.status == MediaRecord.UploadStatus.UPLOADED) {
            Button retry = button("Поставить в очередь", v -> withNetworkPolicyConfirmation(() -> {
                if (providerDisabled()) return;
                UploadScheduler.enqueue(this, record.id);
                Toast.makeText(this, "Запись поставлена в очередь", Toast.LENGTH_SHORT).show();
                render();
            }));
            card.addView(retry, match());
        }
        content.addView(card, cardParams);
    }

    private static String providerLabel(String provider) {
        if (DarkCatSettings.PROVIDER_NEXTCLOUD.equals(provider)) return "Nextcloud";
        if (DarkCatSettings.PROVIDER_WEBDAV.equals(provider)) return "WebDAV";
        if (DarkCatSettings.PROVIDER_DARKCAT_API.equals(provider)) return "DarkCat API";
        return "Выключено";
    }

    private static String status(MediaRecord.UploadStatus status) {
        switch (status) {
            case CAPTURED: return "кадр получен";
            case RECOVERY_PENDING: return "восстановление";
            case ENCRYPTED: return "в хранилище";
            case QUEUED: return "ожидает отправки";
            case UPLOADING: return "отправляется";
            case UPLOADED: return "загружено, проверка не завершена";
            case VERIFIED: return "проверено";
            case FAILED_RETRYABLE: return "временная ошибка";
            case FAILED_PERMANENT: return "ошибка, нужен повтор";
            case LOCAL_DELETE_PENDING: return "локальное удаление ожидает";
            case LOCAL_DELETED: return "локально удалено после проверки";
            default: return status.name();
        }
    }

    private static String tags(String metadata) {
        try {
            JSONArray values = new JSONObject(metadata).optJSONArray("tags");
            if (values == null) return "";
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < values.length(); i++) {
                String value = values.optString(i, "").trim();
                if (value.isEmpty()) continue;
                if (result.length() > 0) result.append(" · ");
                result.append(value);
            }
            return result.toString();
        } catch (Exception ignored) { return ""; }
    }

    private static String size(long bytes) {
        if (bytes < 1024L) return bytes + " Б";
        if (bytes < 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f КБ", bytes / 1024d);
        return String.format(java.util.Locale.US, "%.1f МБ", bytes / (1024d * 1024d));
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
