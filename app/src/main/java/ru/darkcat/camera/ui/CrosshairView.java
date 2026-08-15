package ru.darkcat.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import ru.darkcat.camera.data.DarkCatSettings;

public final class CrosshairView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int outputWidth = 4, outputHeight = 3;
    public CrosshairView(Context context) { super(context); setWillNotDraw(false); setClickable(false); }
    public void setOutputSize(int width, int height) { if (width > 0 && height > 0 && (width != outputWidth || height != outputHeight)) { outputWidth = width; outputHeight = height; invalidate(); } }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); String mode = DarkCatSettings.crosshair(getContext());
        if (DarkCatSettings.CROSSHAIR_OFF.equals(mode)) return;
        CaptureOverlayGeometry.Frame frame = CaptureOverlayGeometry.fitOutputInViewport(getWidth(), getHeight(), outputWidth, outputHeight);
        float cx = frame.left + frame.width / 2f, cy = frame.top + frame.height / 2f;
        float arm = Math.max(8f, Math.min(frame.width, frame.height) * DarkCatSettings.crosshairSize(getContext()) / 1000f);
        float thickness = Math.max(1f, Math.min(frame.width, frame.height) * DarkCatSettings.crosshairThickness(getContext()) / 5000f);
        paint.setColor(DarkCatSettings.crosshairColor(getContext())); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(thickness);
        canvas.drawLine(cx-arm, cy, cx+arm, cy, paint); canvas.drawLine(cx, cy-arm, cx, cy+arm, paint); canvas.drawCircle(cx, cy, Math.max(arm*.25f, thickness), paint);
        postInvalidateDelayed(1000);
    }
}
