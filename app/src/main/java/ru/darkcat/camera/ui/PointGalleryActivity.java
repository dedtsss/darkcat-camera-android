package ru.darkcat.camera.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.MediaRecord;
import ru.darkcat.camera.point.PointClusterer;
import ru.darkcat.camera.point.PointMedia;
import ru.darkcat.camera.point.ShootingPoint;

/** Derived shooting-point view; ordinary Vault timeline remains the default gallery. */
public final class PointGalleryActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Галерея · По точкам");
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(28));
        List<PointMedia> media = new ArrayList<>();
        for (MediaRecord record : DarkCatDatabase.get(this).list()) {
            PointMedia item = toPointMedia(record);
            if (item != null) media.add(item);
        }
        List<ShootingPoint> points = new PointClusterer().cluster(media);
        TextView summary = text("Точек: " + points.size() + " · кадров: " + media.size(), 17f);
        summary.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(summary);
        if (points.isEmpty()) {
            TextView empty = text("Для группировки нужны сохранённые снимки с координатами.", 15f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            content.addView(empty);
        }
        for (ShootingPoint point : points) addPoint(content, point);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void addPoint(LinearLayout content, ShootingPoint point) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(10), dp(11), dp(10));
        card.setBackgroundColor(0xfff1f1f1);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        card.addView(text("Точка " + point.displayNumber() + " · фото 1 из " + point.media().size(), 16f));
        card.addView(text("pointUuid: " + point.pointUuid(), 12f));
        card.addView(text("Состояние: " + point.lifecycle() + " · "
                + DateFormat.getDateTimeInstance().format(new Date(point.firstTimestampMillis()))
                + " — " + DateFormat.getDateTimeInstance().format(new Date(point.lastTimestampMillis())), 13f));
        if (!Double.isNaN(point.centerLatitude())) {
            card.addView(text(String.format(Locale.US, "Центр: %.6f, %.6f", point.centerLatitude(), point.centerLongitude()), 13f));
        } else {
            card.addView(text("Центр: без координат", 13f));
        }
        content.addView(card, params);
    }

    private static PointMedia toPointMedia(MediaRecord record) {
        try {
            JSONObject object = new JSONObject(record.metadataJson);
            Double latitude = number(object, "latitude");
            Double longitude = number(object, "longitude");
            Double accuracy = number(object, "accuracy");
            return new PointMedia(record.id, object.optLong("capturedAt", record.createdAt), latitude, longitude,
                    accuracy == null ? null : accuracy.floatValue());
        } catch (Exception ignored) { return null; }
    }

    private static Double number(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return null;
        double value = object.optDouble(key, Double.NaN);
        return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
    }

    private TextView text(String value, float size) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        return result;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
