package ru.darkcat.camera.vault;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import ru.darkcat.camera.data.DarkCatSettings;

import java.io.File;
import java.io.FileOutputStream;

/** Burns only the DarkCat crosshair; regular Open Camera stamps remain in the upstream pipeline. */
public final class ImageStamper {
    public static void stampCrosshair(File file, android.content.Context context) throws Exception {
        if (!DarkCatSettings.CROSSHAIR_STAMP.equals(DarkCatSettings.crosshair(context))) return;
        Bitmap source = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (source == null) throw new java.io.IOException("unable to decode photo for crosshair stamp");
        Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true); source.recycle();
        Canvas canvas = new Canvas(result);
        float cx = result.getWidth() / 2f, cy = result.getHeight() / 2f;
        float arm = Math.max(8f, Math.min(result.getWidth(), result.getHeight()) * DarkCatSettings.crosshairSize(context) / 1000f);
        float thickness = Math.max(1f, Math.min(result.getWidth(), result.getHeight()) * DarkCatSettings.crosshairThickness(context) / 5000f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(DarkCatSettings.crosshairColor(context)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(thickness);
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paint); canvas.drawLine(cx, cy - arm, cx, cy + arm, paint);
        canvas.drawCircle(cx, cy, Math.max(arm * .25f, thickness), paint);
        try (FileOutputStream output = new FileOutputStream(file)) { if (!result.compress(Bitmap.CompressFormat.JPEG, 94, output)) throw new java.io.IOException("crosshair stamp failed"); }
        result.recycle();
    }
    private ImageStamper() { }
}
