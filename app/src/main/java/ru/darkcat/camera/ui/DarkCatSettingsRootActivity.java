package ru.darkcat.camera.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.linkedcamera.app.R;

/** Category root for focused 0.5 settings pages. */
public final class DarkCatSettingsRootActivity extends Activity {
    public static final String EXTRA_CATEGORY = "darkcat_settings_category";
    private static final String[][] CATEGORIES = {
            {"Съёмка", "Режим кадра, 4:3, вспышка и Night"},
            {"GPS", "Live accuracy, GPS Locker и пороги"},
            {"Метки и штамп", "Номер, теги, OCR-штамп, перекрестие"},
            {"Полевой режим", "Volume+, haptics и service-owned камера"},
            {"Хранилище", "Vault или MediaStore Gallery"},
            {"Синхронизация", "Очередь, провайдер и retry"},
            {"Галерея", "Все кадры и точки съёмки"},
            {"Видео", "Стабилизация и звук; ring buffer не используется"},
            {"CAT Log", "Локальные события, отметка проблемы и ZIP export"},
            {"Расширенные", "Диагностика и inherited Linked Camera controls"}
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setTitle("Настройки DarkCat");
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(14), dp(18), dp(24));
        TextView intro = new TextView(this); intro.setText("Камера 0.5 · основные категории"); intro.setTextSize(19f);
        intro.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); list.addView(intro);
        for (String[] category : CATEGORIES) addCategory(list, category[0], category[1]);
        ScrollView scroll = new ScrollView(this); scroll.addView(list); setContentView(scroll);
    }

    private void addCategory(LinearLayout list, String name, String description) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8)); card.setBackgroundColor(0xfff1f1f1);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8);
        TextView title = new TextView(this); title.setText(name); title.setTextSize(17f); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView detail = new TextView(this); detail.setText(description); detail.setTextSize(13f);
        Button open = new Button(this); open.setAllCaps(false); open.setText("Открыть");
        int testId = categoryButtonId(name);
        if (testId != View.NO_ID) open.setId(testId);
        open.setOnClickListener(v -> startActivity("CAT Log".equals(name)
                ? new Intent(this, DiagnosticsActivity.class)
                : new Intent(this, DarkCatSettingsActivity.class).putExtra(EXTRA_CATEGORY, name)));
        card.addView(title); card.addView(detail); card.addView(open); list.addView(card, params);
    }

    /** Stable selectors for the focused CAT settings flows; labels and layout stay unchanged. */
    private static int categoryButtonId(String name) {
        if ("Съёмка".equals(name)) return R.id.cat_ui_settings_capture;
        if ("Полевой режим".equals(name)) return R.id.cat_ui_settings_field;
        return View.NO_ID;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
