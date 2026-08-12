package ru.darkcat.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.view.View;

import java.io.InputStream;

import ru.darkcat.camera.data.DarkCatSettings;

/** Preview counterpart of ImageStamper's image-space watermark renderer. */
public final class WatermarkView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;
    private String loadedUri;
    private int outputWidth = 4, outputHeight = 3;

    public WatermarkView(Context context) {
        super(context);
        setClickable(false);
        setWillNotDraw(false);
    }
    public void setOutputSize(int width, int height) { if (width > 0 && height > 0 && (width != outputWidth || height != outputHeight)) { outputWidth = width; outputHeight = height; invalidate(); } }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!DarkCatSettings.watermarkEnabled(getContext())) return;
        String uri = DarkCatSettings.watermarkUri(getContext());
        if (uri == null || uri.trim().isEmpty()) return;
        ensureBitmap(uri);
        if (bitmap == null) return;
        WatermarkConfig config = config(uri);
        paint.setAlpha(Math.round(config.opacity * 255f));
        CaptureOverlayGeometry.Frame frame = CaptureOverlayGeometry.fitOutputInViewport(getWidth(), getHeight(), outputWidth, outputHeight);
        for (WatermarkLayout.Box box : WatermarkLayout.boxes(Math.round(frame.width), Math.round(frame.height),
                bitmap.getWidth(), bitmap.getHeight(), config)) {
            canvas.save();
            if (config.angleDegrees != 0f)
                canvas.rotate(config.angleDegrees, frame.left + (box.left + box.right) / 2f, frame.top + (box.top + box.bottom) / 2f);
            canvas.drawBitmap(bitmap, null, new RectF(frame.left + box.left, frame.top + box.top, frame.left + box.right, frame.top + box.bottom), paint);
            canvas.restore();
        }
        postInvalidateDelayed(1_000L);
    }

    private void ensureBitmap(String uri) {
        if (uri.equals(loadedUri)) return;
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
        loadedUri = uri;
        try (InputStream input = getContext().getContentResolver().openInputStream(Uri.parse(uri))) {
            if (input != null) bitmap = BitmapFactory.decodeStream(input);
        } catch (Exception ignored) { }
    }

    private WatermarkConfig config(String uri) {
        String position = DarkCatSettings.watermarkPosition(getContext());
        WatermarkConfig.Position value;
        if ("top_left".equals(position)) value = WatermarkConfig.Position.TOP_LEFT;
        else if ("top_right".equals(position)) value = WatermarkConfig.Position.TOP_RIGHT;
        else if ("bottom_left".equals(position)) value = WatermarkConfig.Position.BOTTOM_LEFT;
        else if ("center".equals(position)) value = WatermarkConfig.Position.CENTER;
        else value = WatermarkConfig.Position.BOTTOM_RIGHT;
        return new WatermarkConfig(true, uri, value, DarkCatSettings.watermarkSize(getContext()),
                DarkCatSettings.watermarkOpacity(getContext()), DarkCatSettings.watermarkTiled(getContext()),
                DarkCatSettings.watermarkTileStep(getContext()), DarkCatSettings.watermarkAngle(getContext()));
    }
}
