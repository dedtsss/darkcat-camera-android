package ru.darkcat.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.List;

import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.location.GpsLockerService;
import ru.darkcat.camera.location.LocationRepository;
import ru.darkcat.camera.location.GpsState;
import ru.darkcat.camera.stamp.TechnicalStampFormatter;
import ru.darkcat.camera.tags.TagRepository;

/** Minimal technical stamp preview, rendered with the same formatter/settings as the JPEG path. */
public final class TechnicalStampView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint background = new Paint();
    private int outputWidth = 4, outputHeight = 3;
    private final LocationRepository.Listener locationListener = ignored -> postInvalidate();

    public TechnicalStampView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        background.setColor(0xee000000);
        text.setColor(Color.WHITE);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
    }

    public void setOutputSize(int width, int height) {
        if (width > 0 && height > 0 && (width != outputWidth || height != outputHeight)) {
            outputWidth = width; outputHeight = height; invalidate();
        }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); LocationRepository.addListener(locationListener); }
    @Override protected void onDetachedFromWindow() { LocationRepository.removeListener(locationListener); super.onDetachedFromWindow(); }

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
        CaptureOverlayGeometry.Frame frame = CaptureOverlayGeometry.fitOutputInViewport(getWidth(), getHeight(), outputWidth, outputHeight);
        float size = Math.max(12f, Math.min(frame.width, frame.height) * .026f);
        float padding = Math.max(7f, size * .55f);
        float lineHeight = size * 1.28f;
        text.setTextSize(size);
        float width = 0f;
        for (String line : lines) width = Math.max(width, text.measureText(line));
        float right = frame.right() - padding;
        float bottom = frame.bottom() - padding;
        float left = Math.max(frame.left + padding, right - width - padding * 2f);
        float top = Math.max(frame.top + padding, bottom - lines.size() * lineHeight - padding * 2f);
        canvas.drawRect(left, top, frame.right(), frame.bottom(), background);
        float y = top + padding - text.ascent();
        for (String line : lines) {
            canvas.drawText(line, left + padding, y, text);
            y += lineHeight;
        }
        postInvalidateDelayed(1_000L);
    }
}
