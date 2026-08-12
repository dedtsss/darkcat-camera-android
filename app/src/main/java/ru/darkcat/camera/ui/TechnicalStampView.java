package ru.darkcat.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.List;

import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.location.GpsLockerService;
import ru.darkcat.camera.location.GpsState;
import ru.darkcat.camera.stamp.TechnicalStampFormatter;
import ru.darkcat.camera.tags.TagRepository;

/** Minimal technical stamp preview, rendered with the same formatter/settings as the JPEG path. */
public final class TechnicalStampView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint background = new Paint();

    public TechnicalStampView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        background.setColor(0xee000000);
        text.setColor(Color.WHITE);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!DarkCatSettings.stampCoordinates(getContext()) && !DarkCatSettings.stampSequence(getContext())
                && !DarkCatSettings.stampTags(getContext()) && !DarkCatSettings.stampCustomText(getContext())) return;
        GpsState state = GpsLockerService.currentState(getContext());
        Double lat = state.getFix() == null ? null : state.getFix().getLatitude();
        Double lon = state.getFix() == null ? null : state.getFix().getLongitude();
        Float accuracy = state.getFix() == null || !state.getFix().hasAccuracy()
                ? null : state.getFix().getAccuracyMeters();
        List<String> lines = TechnicalStampFormatter.lines(lat, lon, accuracy,
                DarkCatSettings.sequenceEnabled(getContext()) ? DarkCatSettings.currentPhotoSequence(getContext()) : null,
                new TagRepository(getContext()).active(), DarkCatSettings.customStampText(getContext()),
                DarkCatSettings.stampCoordinates(getContext()), DarkCatSettings.stampAccuracy(getContext()),
                DarkCatSettings.stampSequence(getContext()), DarkCatSettings.stampTags(getContext()),
                DarkCatSettings.stampCustomText(getContext()));
        if (lines.isEmpty()) return;
        float size = Math.max(12f, Math.min(getWidth(), getHeight()) * .026f);
        float padding = Math.max(7f, size * .55f);
        float lineHeight = size * 1.28f;
        text.setTextSize(size);
        float width = 0f;
        for (String line : lines) width = Math.max(width, text.measureText(line));
        float right = getWidth() - padding;
        float bottom = getHeight() - padding;
        float left = Math.max(padding, right - width - padding * 2f);
        float top = Math.max(padding, bottom - lines.size() * lineHeight - padding * 2f);
        canvas.drawRect(left, top, getWidth(), getHeight(), background);
        float y = top + padding - text.ascent();
        for (String line : lines) {
            canvas.drawText(line, left + padding, y, text);
            y += lineHeight;
        }
        postInvalidateDelayed(1_000L);
    }
}
