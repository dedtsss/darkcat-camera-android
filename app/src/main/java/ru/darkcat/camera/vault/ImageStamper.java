package ru.darkcat.camera.vault;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.location.Location;
import android.location.LocationManager;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import ru.darkcat.camera.data.CaptureContext;
import ru.darkcat.camera.data.DarkCatSettings;
import ru.darkcat.camera.stamp.TechnicalStampFormatter;
import ru.darkcat.camera.ui.WatermarkConfig;
import ru.darkcat.camera.ui.WatermarkLayout;

/** Flattens DarkCat's simple black technical block and optional centered crosshair. */
public final class ImageStamper {
    /* Bitmap re-encoding drops the OEM's capture metadata unless it is copied explicitly. Keep
       the camera fields that are useful for diagnostics and downstream EXIF consumers. The
       orientation is intentionally excluded because orient() has already flattened it. */
    private static final String[] PRESERVED_EXIF_TAGS = new String[]{
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_EXPOSURE_MODE, ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SPECIFICATION, ExifInterface.TAG_MAKER_NOTE,
            ExifInterface.TAG_USER_COMMENT, ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_TIMESTAMP
    };

    public static void stamp(File file, Context context, int sequence,
                             CaptureContext captureContext, long capturedAt) throws Exception {
        Location location = captureLocation(captureContext);
        List<String> tags = captureContext == null
                ? Collections.emptyList() : captureContext.customTags;
        List<String> lines = TechnicalStampFormatter.lines(
                location == null ? null : location.getLatitude(),
                location == null ? null : location.getLongitude(),
                location == null || !location.hasAccuracy() ? null : location.getAccuracy(),
                sequence > 0 ? sequence : null,
                tags,
                DarkCatSettings.customStampText(context),
                DarkCatSettings.stampCoordinates(context),
                DarkCatSettings.stampAccuracy(context),
                DarkCatSettings.stampSequence(context),
                DarkCatSettings.stampTags(context),
                DarkCatSettings.stampCustomText(context));
        boolean drawCrosshair = DarkCatSettings.CROSSHAIR_STAMP.equals(DarkCatSettings.crosshair(context));
        if (lines.isEmpty() && !drawCrosshair && !DarkCatSettings.watermarkEnabled(context)) {
            writeCaptureLocationExif(file, captureContext, capturedAt);
            return;
        }

        ExifInterface originalExif = new ExifInterface(file.getAbsolutePath());
        Map<String, String> preservedExif = readPreservedExif(originalExif);
        int orientation = originalExif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inMutable = true;
        Bitmap source = BitmapFactory.decodeFile(file.getAbsolutePath(), decode);
        if (source == null) throw new java.io.IOException("unable to decode recovery photo");
        Bitmap oriented = orient(source, orientation);
        if (oriented != source) source.recycle();
        Bitmap result = oriented;
        if (!oriented.isMutable()) {
            result = oriented.copy(Bitmap.Config.ARGB_8888, true);
            oriented.recycle();
            if (result == null) throw new java.io.IOException("unable to allocate stamp bitmap");
        }
        Canvas canvas = new Canvas(result);

        drawWatermark(canvas, result, context);
        if (drawCrosshair) drawCrosshair(canvas, result, context);
        if (!lines.isEmpty()) drawTechnicalBlock(canvas, result, lines);

        replaceAtomically(file, result, preservedExif, captureContext, capturedAt);
    }

    private static Bitmap orient(Bitmap source, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1f, 1f); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180f); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1f, -1f); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f); matrix.postScale(-1f, 1f); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90f); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f); matrix.postScale(-1f, 1f); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90f); break;
            default: return source;
        }
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    /** Compatibility entry retained for existing callers/tests. */
    public static void stampCrosshair(File file, Context context) throws Exception {
        stamp(file, context, 0, CaptureContext.empty(), System.currentTimeMillis());
    }

    private static void drawTechnicalBlock(Canvas canvas, Bitmap bitmap, List<String> lines) {
        float min = Math.min(bitmap.getWidth(), bitmap.getHeight());
        float textSize = Math.max(22f, min * 0.026f);
        float padding = Math.max(12f, textSize * 0.55f);
        float lineHeight = textSize * 1.28f;
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(textSize);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        float width = 0f;
        for (String line : lines) width = Math.max(width, text.measureText(line));
        float right = bitmap.getWidth() - padding;
        float bottom = bitmap.getHeight() - padding;
        float left = Math.max(padding, right - width - 2f * padding);
        float top = Math.max(padding, bottom - lines.size() * lineHeight - 2f * padding);
        Paint background = new Paint();
        background.setColor(0xee000000);
        canvas.drawRect(left, top, bitmap.getWidth(), bitmap.getHeight(), background);
        float x = left + padding;
        float y = top + padding - text.ascent();
        for (String line : lines) {
            canvas.drawText(line, x, y, text);
            y += lineHeight;
        }
    }

    private static void drawCrosshair(Canvas canvas, Bitmap result, Context context) {
        float cx = result.getWidth() / 2f;
        float cy = result.getHeight() / 2f;
        float min = Math.min(result.getWidth(), result.getHeight());
        float arm = Math.max(8f, min * DarkCatSettings.crosshairSize(context) / 1000f);
        float thickness = Math.max(1f, min * DarkCatSettings.crosshairThickness(context) / 5000f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(DarkCatSettings.crosshairColor(context));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paint);
        canvas.drawLine(cx, cy - arm, cx, cy + arm, paint);
        canvas.drawCircle(cx, cy, Math.max(arm * .25f, thickness), paint);
    }

    private static void drawWatermark(Canvas canvas, Bitmap result, Context context) {
        if (!DarkCatSettings.watermarkEnabled(context)) return;
        String uriText = DarkCatSettings.watermarkUri(context);
        if (uriText == null || uriText.trim().isEmpty()) return;
        Bitmap watermark = null;
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uriText))) {
            if (input == null) return;
            watermark = BitmapFactory.decodeStream(input);
            if (watermark == null) return;
            WatermarkConfig config = watermarkConfig(context, uriText);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setAlpha(Math.round(config.opacity * 255f));
            for (WatermarkLayout.Box box : WatermarkLayout.boxes(result.getWidth(), result.getHeight(),
                    watermark.getWidth(), watermark.getHeight(), config)) {
                canvas.save();
                if (config.angleDegrees != 0f)
                    canvas.rotate(config.angleDegrees, (box.left + box.right) / 2f, (box.top + box.bottom) / 2f);
                canvas.drawBitmap(watermark, null, new RectF(box.left, box.top, box.right, box.bottom), paint);
                canvas.restore();
            }
        } catch (Exception ignored) {
            // A missing optional watermark must never invalidate a captured JPEG.
        } finally {
            if (watermark != null && !watermark.isRecycled()) watermark.recycle();
        }
    }

    private static WatermarkConfig watermarkConfig(Context context, String uri) {
        String position = DarkCatSettings.watermarkPosition(context);
        WatermarkConfig.Position value;
        if ("top_left".equals(position)) value = WatermarkConfig.Position.TOP_LEFT;
        else if ("top_right".equals(position)) value = WatermarkConfig.Position.TOP_RIGHT;
        else if ("bottom_left".equals(position)) value = WatermarkConfig.Position.BOTTOM_LEFT;
        else if ("center".equals(position)) value = WatermarkConfig.Position.CENTER;
        else value = WatermarkConfig.Position.BOTTOM_RIGHT;
        return new WatermarkConfig(true, uri, value, DarkCatSettings.watermarkSize(context),
                DarkCatSettings.watermarkOpacity(context), DarkCatSettings.watermarkTiled(context),
                DarkCatSettings.watermarkTileStep(context), DarkCatSettings.watermarkAngle(context));
    }

    private static Location captureLocation(CaptureContext captureContext) {
        if (captureContext == null || captureContext.captureLatitude == null
                || captureContext.captureLongitude == null) return null;
        Location location = new Location(captureContext.captureLocationProvider == null
                ? LocationManager.GPS_PROVIDER : captureContext.captureLocationProvider);
        location.setLatitude(captureContext.captureLatitude);
        location.setLongitude(captureContext.captureLongitude);
        if (captureContext.captureAccuracyMeters != null)
            location.setAccuracy(captureContext.captureAccuracyMeters);
        if (captureContext.captureLocationElapsedRealtimeNanos > 0)
            location.setElapsedRealtimeNanos(captureContext.captureLocationElapsedRealtimeNanos);
        return location;
    }

    private static Map<String, String> readPreservedExif(ExifInterface source) {
        Map<String, String> attributes = new HashMap<>();
        for (String tag : PRESERVED_EXIF_TAGS) {
            String value = source.getAttribute(tag);
            if (value != null) attributes.put(tag, value);
        }
        return attributes;
    }

    /** Adds the shutter-time location after any bitmap rewrite, without consulting live state. */
    public static void writeCaptureLocationExif(File file, CaptureContext captureContext,
                                                long capturedAt) {
        if (captureContext == null || captureContext.captureLatitude == null
                || captureContext.captureLongitude == null) return;
        try {
            Location location = captureLocation(captureContext);
            if (location == null) return;
            location.setTime(capturedAt);
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            exif.setGpsInfo(location);
            exif.saveAttributes();
        } catch (Exception ignored) {
            // The durable capture context/database remains authoritative if an OEM JPEG rejects
            // EXIF rewriting.
        }
    }

    private static void replaceAtomically(File file, Bitmap result, Map<String, String> preservedExif,
                                          CaptureContext captureContext, long capturedAt) throws Exception {
        File temporary = new File(file.getParentFile(), "." + file.getName() + ".stamp.tmp");
        File backup = new File(file.getParentFile(), "." + file.getName() + ".stamp.bak");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!result.compress(Bitmap.CompressFormat.JPEG, 100, output))
                    throw new java.io.IOException("technical stamp encode failed");
                output.flush();
                output.getFD().sync();
            }
            ExifInterface stampedExif = new ExifInterface(temporary.getAbsolutePath());
            for (Map.Entry<String, String> entry : preservedExif.entrySet())
                stampedExif.setAttribute(entry.getKey(), entry.getValue());
            stampedExif.setAttribute(ExifInterface.TAG_ORIENTATION,
                    String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            stampedExif.saveAttributes();
            writeCaptureLocationExif(temporary, captureContext, capturedAt);
            if (backup.exists() && !backup.delete())
                throw new java.io.IOException("unable to prepare stamp backup");
            if (!file.renameTo(backup)) throw new java.io.IOException("unable to preserve recovery image");
            if (!temporary.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                backup.renameTo(file);
                throw new java.io.IOException("unable to commit stamped image");
            }
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
        } finally {
            result.recycle();
            if (temporary.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    private ImageStamper() { }
}
