package ru.darkcat.camera.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small image-space object editor. Shapes and text remain independent until {@link #render()}.
 * Coordinates are stored in source-bitmap pixels, so saving never depends on the preview size.
 */
public final class ObjectEditorView extends View {
    public enum Tool { SELECT, CROP, FREEHAND, LINE, RECTANGLE, OVAL, ARROW }

    public interface Listener {
        void onStateChanged(String description, boolean hasSelection, boolean canUndo, boolean canRedo);
    }

    private static final int[] COLORS = {
            Color.WHITE, 0xffffd600, 0xffff3b30, 0xff2ecc71, Color.BLACK
    };
    private static final int MAX_HISTORY = 24;
    private static final float MIN_OBJECT_SIZE = 3f;
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint objectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<EditorObject> objects = new ArrayList<>();
    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private Bitmap source;
    private Tool tool = Tool.SELECT;
    private Listener listener;
    private long selectedId = -1L;
    private int currentColor = Color.WHITE;
    private float currentStroke = 8f;
    private boolean gestureChanged;
    private State gestureBefore;
    private PointF gestureStart;
    private PointF lastPoint;
    private EditorObject gestureObject;
    private TransformGesture transformGesture;
    private RectF cropRect;
    private RectF initialCropRect;
    private CropHandle cropHandle = CropHandle.NONE;

    public ObjectEditorView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setColor(0xff00c8ff);
        cropPaint.setStyle(Paint.Style.STROKE);
        cropPaint.setColor(Color.WHITE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        notifyState();
    }

    public void setSource(Bitmap bitmap) {
        if (bitmap == null) throw new IllegalArgumentException("bitmap is required");
        source = bitmap;
        objects.clear();
        undo.clear();
        redo.clear();
        selectedId = -1L;
        currentStroke = Math.max(3f, bitmap.getWidth() / 270f);
        tool = Tool.SELECT;
        cropRect = null;
        invalidate();
        notifyState();
    }

    public Bitmap getSource() { return source; }

    public void setTool(Tool newTool) {
        if (newTool == null) return;
        tool = newTool;
        transformGesture = null;
        gestureObject = null;
        if (newTool == Tool.CROP && source != null) {
            float insetX = source.getWidth() * 0.08f;
            float insetY = source.getHeight() * 0.08f;
            cropRect = new RectF(insetX, insetY,
                    source.getWidth() - insetX, source.getHeight() - insetY);
            selectedId = -1L;
        } else {
            cropRect = null;
        }
        invalidate();
        notifyState();
    }

    public Tool getTool() { return tool; }

    public boolean hasSelection() { return findSelected() != null; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public void addText(String value) {
        if (source == null || value == null || value.trim().isEmpty()) return;
        remember();
        TextObject text = new TextObject(value.trim(), source.getWidth() / 2f,
                source.getHeight() / 2f, currentColor,
                Math.max(30f, source.getWidth() * 0.045f), currentStroke);
        objects.add(text);
        selectedId = text.id;
        tool = Tool.SELECT;
        invalidate();
        notifyState();
    }

    public void deleteSelection() {
        EditorObject selected = findSelected();
        if (selected == null) return;
        remember();
        objects.remove(selected);
        selectedId = -1L;
        invalidate();
        notifyState();
    }

    public void cycleSelectionColor() {
        int base = currentColor;
        EditorObject selected = findSelected();
        if (selected != null) base = selected.color;
        int next = COLORS[0];
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == base) { next = COLORS[(i + 1) % COLORS.length]; break; }
        }
        if (selected != null) {
            remember();
            selected.color = next;
        }
        currentColor = next;
        invalidate();
        notifyState();
    }

    public void cycleSelectionStroke() {
        float base = currentStroke;
        EditorObject selected = findSelected();
        if (selected != null) base = selected.strokeWidth;
        float unit = source == null ? 4f : Math.max(2f, source.getWidth() / 540f);
        float[] widths = {unit, unit * 2f, unit * 4f, unit * 7f};
        int index = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < widths.length; i++) {
            float candidate = Math.abs(widths[i] - base);
            if (candidate < distance) { index = i; distance = candidate; }
        }
        float next = widths[(index + 1) % widths.length];
        if (selected != null) {
            remember();
            selected.strokeWidth = next;
        }
        currentStroke = next;
        invalidate();
        notifyState();
    }

    public boolean applyCrop() {
        if (source == null || cropRect == null) return false;
        RectF bounded = boundedCrop(cropRect);
        int left = Math.max(0, Math.round(bounded.left));
        int top = Math.max(0, Math.round(bounded.top));
        int right = Math.min(source.getWidth(), Math.round(bounded.right));
        int bottom = Math.min(source.getHeight(), Math.round(bounded.bottom));
        if (right - left < 2 || bottom - top < 2) return false;
        if (left == 0 && top == 0 && right == source.getWidth() && bottom == source.getHeight()) {
            setTool(Tool.SELECT);
            return true;
        }
        remember();
        source = Bitmap.createBitmap(source, left, top, right - left, bottom - top);
        for (EditorObject object : objects) object.translate(-left, -top);
        selectedId = -1L;
        cropRect = null;
        tool = Tool.SELECT;
        invalidate();
        notifyState();
        return true;
    }

    public void cancelCrop() { setTool(Tool.SELECT); }

    public void undo() {
        if (undo.isEmpty()) return;
        redo.push(snapshot());
        restore(undo.pop());
    }

    public void redo() {
        if (redo.isEmpty()) return;
        undo.push(snapshot());
        restore(redo.pop());
    }

    /** Flattens source and overlays at source resolution; crop/selection UI is never rendered. */
    public Bitmap render() {
        if (source == null) return null;
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(source, 0f, 0f, bitmapPaint);
        canvas.save();
        canvas.clipRect(0f, 0f, source.getWidth(), source.getHeight());
        for (EditorObject object : objects) object.draw(canvas, objectPaint);
        canvas.restore();
        return output;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (source == null) return;
        ImageTransform image = imageTransform();
        canvas.save();
        canvas.translate(image.left, image.top);
        canvas.scale(image.scale, image.scale);
        canvas.clipRect(0f, 0f, source.getWidth(), source.getHeight());
        canvas.drawBitmap(source, 0f, 0f, bitmapPaint);
        for (EditorObject object : objects) object.draw(canvas, objectPaint);
        EditorObject selected = findSelected();
        if (selected != null && tool != Tool.CROP) drawSelection(canvas, selected, image.scale);
        if (tool == Tool.CROP && cropRect != null) drawCrop(canvas, image.scale);
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (source == null) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            PointF point = toImage(event.getX(), event.getY());
            gestureStart = point;
            lastPoint = point;
            gestureChanged = false;
            transformGesture = null;
            if (tool == Tool.CROP) {
                initialCropRect = cropRect == null ? null : new RectF(cropRect);
                cropHandle = findCropHandle(point);
                return true;
            }
            if (tool == Tool.SELECT) {
                EditorObject hit = hitObject(point);
                selectedId = hit == null ? -1L : hit.id;
                gestureObject = hit;
                gestureBefore = hit == null ? null : snapshot();
                invalidate();
                notifyState();
                return true;
            }
            remember();
            gestureObject = createDrawObject(point);
            if (gestureObject != null) {
                objects.add(gestureObject);
                selectedId = gestureObject.id;
            }
            invalidate();
            return true;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                && tool == Tool.SELECT && gestureObject != null && event.getPointerCount() >= 2) {
            transformGesture = new TransformGesture(gestureObject, event, this);
            return true;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP
                && tool == Tool.SELECT && gestureObject != null && event.getPointerCount() >= 2) {
            int remaining = event.getActionIndex() == 0 ? 1 : 0;
            lastPoint = toImage(event.getX(remaining), event.getY(remaining));
            transformGesture = null;
            return true;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (tool == Tool.CROP) {
                updateCrop(toImage(event.getX(), event.getY()));
                invalidate();
                return true;
            }
            if (tool == Tool.SELECT && gestureObject != null) {
                if (!gestureChanged && gestureBefore != null) remember(gestureBefore);
                if (event.getPointerCount() >= 2 && transformGesture != null) {
                    transformGesture.apply(gestureObject, event, this);
                } else if (event.getPointerCount() == 1 && lastPoint != null) {
                    PointF point = toImage(event.getX(), event.getY());
                    gestureObject.translate(point.x - lastPoint.x, point.y - lastPoint.y);
                    lastPoint = point;
                }
                gestureChanged = true;
                invalidate();
                return true;
            }
            PointF point = toImage(event.getX(), event.getY());
            updateDrawObject(gestureObject, gestureStart, point);
            gestureChanged = true;
            invalidate();
            return true;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !gestureChanged) performClick();
            if (tool == Tool.CROP) {
                cropHandle = CropHandle.NONE;
            } else if (tool != Tool.SELECT && tool != Tool.FREEHAND
                    && gestureObject instanceof ShapeObject
                    && ((ShapeObject) gestureObject).isTooSmall()) {
                objects.remove(gestureObject);
                selectedId = -1L;
                if (!undo.isEmpty()) undo.pop();
            }
            gestureObject = null;
            gestureBefore = null;
            transformGesture = null;
            getParent().requestDisallowInterceptTouchEvent(false);
            invalidate();
            notifyState();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private EditorObject createDrawObject(PointF point) {
        switch (tool) {
            case FREEHAND: return new StrokeObject(point.x, point.y, currentColor, currentStroke);
            case LINE: return new ShapeObject(ShapeKind.LINE, point.x, point.y, currentColor, currentStroke);
            case RECTANGLE: return new ShapeObject(ShapeKind.RECTANGLE, point.x, point.y, currentColor, currentStroke);
            case OVAL: return new ShapeObject(ShapeKind.OVAL, point.x, point.y, currentColor, currentStroke);
            case ARROW: return new ShapeObject(ShapeKind.ARROW, point.x, point.y, currentColor, currentStroke);
            default: return null;
        }
    }

    private static void updateDrawObject(EditorObject object, PointF start, PointF point) {
        if (object instanceof ShapeObject) ((ShapeObject) object).update(start, point);
        else if (object instanceof StrokeObject) ((StrokeObject) object).add(point);
    }

    private void remember() {
        remember(snapshot());
    }

    private void remember(State state) {
        undo.push(state);
        while (undo.size() > MAX_HISTORY) undo.removeLast();
        redo.clear();
    }

    private State snapshot() {
        ArrayList<EditorObject> copies = new ArrayList<>(objects.size());
        for (EditorObject object : objects) copies.add(object.copy());
        return new State(source, copies, selectedId, currentColor, currentStroke);
    }

    private void restore(State state) {
        source = state.source;
        objects.clear();
        for (EditorObject object : state.objects) objects.add(object.copy());
        selectedId = state.selectedId;
        currentColor = state.color;
        currentStroke = state.stroke;
        tool = Tool.SELECT;
        cropRect = null;
        gestureObject = null;
        gestureBefore = null;
        transformGesture = null;
        invalidate();
        notifyState();
    }

    private EditorObject findSelected() {
        for (EditorObject object : objects) if (object.id == selectedId) return object;
        return null;
    }

    private EditorObject hitObject(PointF point) {
        float tolerance = dp(22f) / Math.max(0.0001f, imageTransform().scale);
        for (int i = objects.size() - 1; i >= 0; i--) {
            EditorObject object = objects.get(i);
            if (object.hit(point.x, point.y, tolerance, objectPaint)) return object;
        }
        return null;
    }

    private void drawSelection(Canvas canvas, EditorObject selected, float imageScale) {
        canvas.save();
        selected.applyTransform(canvas);
        RectF bounds = selected.localBounds(objectPaint);
        float padding = dp(8f) / imageScale / Math.max(0.2f, selected.scale);
        bounds.inset(-padding, -padding);
        selectionPaint.setStrokeWidth(dp(1.5f) / imageScale / Math.max(0.2f, selected.scale));
        canvas.drawRect(bounds, selectionPaint);
        float radius = dp(5f) / imageScale / Math.max(0.2f, selected.scale);
        selectionPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(bounds.right, bounds.bottom, radius, selectionPaint);
        selectionPaint.setStyle(Paint.Style.STROKE);
        canvas.restore();
    }

    private void drawCrop(Canvas canvas, float imageScale) {
        RectF bounded = boundedCrop(cropRect);
        Paint shade = cropPaint;
        shade.setStyle(Paint.Style.FILL);
        shade.setColor(0x99000000);
        canvas.drawRect(0f, 0f, source.getWidth(), bounded.top, shade);
        canvas.drawRect(0f, bounded.bottom, source.getWidth(), source.getHeight(), shade);
        canvas.drawRect(0f, bounded.top, bounded.left, bounded.bottom, shade);
        canvas.drawRect(bounded.right, bounded.top, source.getWidth(), bounded.bottom, shade);
        shade.setStyle(Paint.Style.STROKE);
        shade.setColor(Color.WHITE);
        shade.setStrokeWidth(dp(2f) / imageScale);
        canvas.drawRect(bounded, shade);
        float thirdX = bounded.width() / 3f;
        float thirdY = bounded.height() / 3f;
        shade.setStrokeWidth(dp(1f) / imageScale);
        shade.setColor(0x99ffffff);
        canvas.drawLine(bounded.left + thirdX, bounded.top, bounded.left + thirdX, bounded.bottom, shade);
        canvas.drawLine(bounded.left + 2f * thirdX, bounded.top, bounded.left + 2f * thirdX, bounded.bottom, shade);
        canvas.drawLine(bounded.left, bounded.top + thirdY, bounded.right, bounded.top + thirdY, shade);
        canvas.drawLine(bounded.left, bounded.top + 2f * thirdY, bounded.right, bounded.top + 2f * thirdY, shade);
    }

    private CropHandle findCropHandle(PointF point) {
        if (cropRect == null) return CropHandle.NONE;
        float tolerance = dp(28f) / Math.max(0.0001f, imageTransform().scale);
        boolean left = Math.abs(point.x - cropRect.left) <= tolerance;
        boolean right = Math.abs(point.x - cropRect.right) <= tolerance;
        boolean top = Math.abs(point.y - cropRect.top) <= tolerance;
        boolean bottom = Math.abs(point.y - cropRect.bottom) <= tolerance;
        if (left && top) return CropHandle.TOP_LEFT;
        if (right && top) return CropHandle.TOP_RIGHT;
        if (left && bottom) return CropHandle.BOTTOM_LEFT;
        if (right && bottom) return CropHandle.BOTTOM_RIGHT;
        if (left) return CropHandle.LEFT;
        if (right) return CropHandle.RIGHT;
        if (top) return CropHandle.TOP;
        if (bottom) return CropHandle.BOTTOM;
        return cropRect.contains(point.x, point.y) ? CropHandle.MOVE : CropHandle.NONE;
    }

    private void updateCrop(PointF point) {
        if (cropRect == null || initialCropRect == null || gestureStart == null) return;
        float dx = point.x - gestureStart.x;
        float dy = point.y - gestureStart.y;
        RectF next = new RectF(initialCropRect);
        switch (cropHandle) {
            case TOP_LEFT: next.left += dx; next.top += dy; break;
            case TOP_RIGHT: next.right += dx; next.top += dy; break;
            case BOTTOM_LEFT: next.left += dx; next.bottom += dy; break;
            case BOTTOM_RIGHT: next.right += dx; next.bottom += dy; break;
            case LEFT: next.left += dx; break;
            case RIGHT: next.right += dx; break;
            case TOP: next.top += dy; break;
            case BOTTOM: next.bottom += dy; break;
            case MOVE: next.offset(dx, dy); break;
            default: return;
        }
        cropRect = boundedCrop(next);
    }

    private RectF boundedCrop(RectF value) {
        float min = Math.max(24f, Math.min(source.getWidth(), source.getHeight()) * 0.04f);
        RectF result = new RectF(value);
        if (result.width() < min) {
            if (cropHandle == CropHandle.LEFT || cropHandle == CropHandle.TOP_LEFT || cropHandle == CropHandle.BOTTOM_LEFT)
                result.left = result.right - min;
            else result.right = result.left + min;
        }
        if (result.height() < min) {
            if (cropHandle == CropHandle.TOP || cropHandle == CropHandle.TOP_LEFT || cropHandle == CropHandle.TOP_RIGHT)
                result.top = result.bottom - min;
            else result.bottom = result.top + min;
        }
        if (result.left < 0f) result.offset(-result.left, 0f);
        if (result.top < 0f) result.offset(0f, -result.top);
        if (result.right > source.getWidth()) result.offset(source.getWidth() - result.right, 0f);
        if (result.bottom > source.getHeight()) result.offset(0f, source.getHeight() - result.bottom);
        result.left = clamp(result.left, 0f, source.getWidth() - min);
        result.top = clamp(result.top, 0f, source.getHeight() - min);
        result.right = clamp(result.right, result.left + min, source.getWidth());
        result.bottom = clamp(result.bottom, result.top + min, source.getHeight());
        return result;
    }

    private PointF toImage(float x, float y) {
        ImageTransform transform = imageTransform();
        return new PointF(
                clamp((x - transform.left) / transform.scale, 0f, source.getWidth()),
                clamp((y - transform.top) / transform.scale, 0f, source.getHeight()));
    }

    private ImageTransform imageTransform() {
        if (source == null || getWidth() <= 0 || getHeight() <= 0) return new ImageTransform(0f, 0f, 1f);
        float scale = Math.min(getWidth() / (float) source.getWidth(), getHeight() / (float) source.getHeight());
        float left = (getWidth() - source.getWidth() * scale) / 2f;
        float top = (getHeight() - source.getHeight() * scale) / 2f;
        return new ImageTransform(left, top, scale);
    }

    private void notifyState() {
        if (listener == null) return;
        EditorObject selected = findSelected();
        String description;
        if (tool == Tool.CROP) description = "Перетащите рамку и нажмите «Применить»";
        else if (tool == Tool.SELECT && selected != null) description = "Объект выбран: перемещение одним пальцем, масштаб и поворот двумя";
        else if (tool == Tool.SELECT) description = "Коснитесь объекта, чтобы снова его выбрать";
        else description = "Проведите по снимку, чтобы добавить: " + toolName(tool);
        listener.onStateChanged(description, selected != null, canUndo(), canRedo());
    }

    private static String toolName(Tool tool) {
        switch (tool) {
            case FREEHAND: return "рисование";
            case LINE: return "линию";
            case RECTANGLE: return "прямоугольник";
            case OVAL: return "овал";
            case ARROW: return "стрелку";
            default: return "объект";
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private enum CropHandle { NONE, MOVE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private enum ShapeKind { LINE, RECTANGLE, OVAL, ARROW }

    private static final class ImageTransform {
        final float left, top, scale;
        ImageTransform(float left, float top, float scale) { this.left = left; this.top = top; this.scale = scale; }
    }

    private static final class State {
        final Bitmap source;
        final ArrayList<EditorObject> objects;
        final long selectedId;
        final int color;
        final float stroke;
        State(Bitmap source, ArrayList<EditorObject> objects, long selectedId, int color, float stroke) {
            this.source = source; this.objects = objects; this.selectedId = selectedId;
            this.color = color; this.stroke = stroke;
        }
    }

    private abstract static class EditorObject {
        final long id;
        float centerX, centerY, scale = 1f, rotation;
        int color;
        float strokeWidth;

        EditorObject(float centerX, float centerY, int color, float strokeWidth) {
            this(NEXT_ID.getAndIncrement(), centerX, centerY, color, strokeWidth);
        }

        EditorObject(long id, float centerX, float centerY, int color, float strokeWidth) {
            this.id = id; this.centerX = centerX; this.centerY = centerY;
            this.color = color; this.strokeWidth = strokeWidth;
        }

        final void draw(Canvas canvas, Paint paint) {
            canvas.save();
            applyTransform(canvas);
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            drawLocal(canvas, paint);
            canvas.restore();
        }

        final void applyTransform(Canvas canvas) {
            canvas.translate(centerX, centerY);
            canvas.rotate(rotation);
            canvas.scale(scale, scale);
        }

        final boolean hit(float x, float y, float tolerance, Paint paint) {
            EditorMath.Point local = EditorMath.inverseTransform(x, y, centerX, centerY, rotation, scale);
            return hitLocal(local.x, local.y, tolerance / Math.max(0.2f, scale), paint);
        }

        void translate(float dx, float dy) { centerX += dx; centerY += dy; }
        abstract void drawLocal(Canvas canvas, Paint paint);
        abstract boolean hitLocal(float x, float y, float tolerance, Paint paint);
        abstract RectF localBounds(Paint paint);
        abstract EditorObject copy();

        void copyTransformTo(EditorObject copy) {
            copy.scale = scale;
            copy.rotation = rotation;
        }
    }

    private static final class ShapeObject extends EditorObject {
        final ShapeKind kind;
        float vectorX, vectorY;

        ShapeObject(ShapeKind kind, float x, float y, int color, float stroke) {
            super(x, y, color, stroke); this.kind = kind;
        }

        ShapeObject(long id, ShapeKind kind, float x, float y, int color, float stroke, float vx, float vy) {
            super(id, x, y, color, stroke); this.kind = kind; vectorX = vx; vectorY = vy;
        }

        void update(PointF start, PointF end) {
            centerX = (start.x + end.x) / 2f;
            centerY = (start.y + end.y) / 2f;
            vectorX = end.x - start.x;
            vectorY = end.y - start.y;
        }

        boolean isTooSmall() { return Math.hypot(vectorX, vectorY) < MIN_OBJECT_SIZE; }

        @Override void drawLocal(Canvas canvas, Paint paint) {
            float halfX = vectorX / 2f, halfY = vectorY / 2f;
            if (kind == ShapeKind.LINE) canvas.drawLine(-halfX, -halfY, halfX, halfY, paint);
            else if (kind == ShapeKind.RECTANGLE) canvas.drawRect(bounds(), paint);
            else if (kind == ShapeKind.OVAL) canvas.drawOval(bounds(), paint);
            else drawArrow(canvas, paint, -halfX, -halfY, halfX, halfY);
        }

        @Override boolean hitLocal(float x, float y, float tolerance, Paint paint) {
            if (kind == ShapeKind.LINE || kind == ShapeKind.ARROW) {
                return EditorMath.distanceToSegment(x, y, -vectorX / 2f, -vectorY / 2f,
                        vectorX / 2f, vectorY / 2f) <= tolerance + strokeWidth;
            }
            RectF bounds = bounds();
            if (kind == ShapeKind.RECTANGLE) {
                bounds.inset(-tolerance, -tolerance);
                return bounds.contains(x, y);
            }
            float rx = Math.max(1f, bounds.width() / 2f), ry = Math.max(1f, bounds.height() / 2f);
            float normalized = (x * x) / (rx * rx) + (y * y) / (ry * ry);
            float band = tolerance / Math.max(1f, Math.min(rx, ry));
            return normalized <= (1f + band) * (1f + band);
        }

        private RectF bounds() {
            return new RectF(-Math.abs(vectorX) / 2f, -Math.abs(vectorY) / 2f,
                    Math.abs(vectorX) / 2f, Math.abs(vectorY) / 2f);
        }

        @Override RectF localBounds(Paint paint) { return bounds(); }

        @Override EditorObject copy() {
            ShapeObject copy = new ShapeObject(id, kind, centerX, centerY, color, strokeWidth, vectorX, vectorY);
            copyTransformTo(copy); return copy;
        }

        private static void drawArrow(Canvas canvas, Paint paint, float x1, float y1, float x2, float y2) {
            canvas.drawLine(x1, y1, x2, y2, paint);
            float length = (float) Math.hypot(x2 - x1, y2 - y1);
            if (length < 1f) return;
            float head = Math.min(length * 0.35f, Math.max(paint.getStrokeWidth() * 5f, length * 0.14f));
            double angle = Math.atan2(y2 - y1, x2 - x1);
            double spread = Math.toRadians(28d);
            canvas.drawLine(x2, y2, x2 - head * (float) Math.cos(angle - spread),
                    y2 - head * (float) Math.sin(angle - spread), paint);
            canvas.drawLine(x2, y2, x2 - head * (float) Math.cos(angle + spread),
                    y2 - head * (float) Math.sin(angle + spread), paint);
        }
    }

    private static final class TextObject extends EditorObject {
        final String text;
        final float textSize;

        TextObject(String text, float x, float y, int color, float textSize, float stroke) {
            super(x, y, color, stroke); this.text = text; this.textSize = textSize;
        }

        TextObject(long id, String text, float x, float y, int color, float textSize, float stroke) {
            super(id, x, y, color, stroke); this.text = text; this.textSize = textSize;
        }

        private void configure(Paint paint) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(textSize);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        }

        @Override void drawLocal(Canvas canvas, Paint paint) {
            configure(paint);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            String[] lines = lines();
            float lineHeight = metrics.descent - metrics.ascent;
            float spacing = lineHeight * 0.12f;
            float totalHeight = lineHeight * lines.length + spacing * (lines.length - 1);
            float baseline = -totalHeight / 2f - metrics.ascent;
            if (strokeWidth > 0f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(color == Color.BLACK ? Color.WHITE : Color.BLACK);
                for (int i = 0; i < lines.length; i++)
                    canvas.drawText(lines[i], 0f, baseline + i * (lineHeight + spacing), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            for (int i = 0; i < lines.length; i++)
                canvas.drawText(lines[i], 0f, baseline + i * (lineHeight + spacing), paint);
        }

        @Override boolean hitLocal(float x, float y, float tolerance, Paint paint) {
            RectF bounds = localBounds(paint); bounds.inset(-tolerance, -tolerance); return bounds.contains(x, y);
        }

        @Override RectF localBounds(Paint paint) {
            configure(paint);
            String[] lines = lines();
            float width = 0f;
            for (String line : lines) width = Math.max(width, paint.measureText(line));
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float lineHeight = metrics.descent - metrics.ascent;
            float totalHeight = lineHeight * lines.length + lineHeight * 0.12f * (lines.length - 1);
            return new RectF(-width / 2f, -totalHeight / 2f, width / 2f, totalHeight / 2f);
        }

        private String[] lines() { return text.split("\\n", -1); }

        @Override EditorObject copy() {
            TextObject copy = new TextObject(id, text, centerX, centerY, color, textSize, strokeWidth);
            copyTransformTo(copy); return copy;
        }
    }

    private static final class StrokeObject extends EditorObject {
        final ArrayList<PointF> points = new ArrayList<>();

        StrokeObject(float x, float y, int color, float stroke) {
            super(x, y, color, stroke); points.add(new PointF(0f, 0f));
        }

        StrokeObject(long id, float x, float y, int color, float stroke, List<PointF> sourcePoints) {
            super(id, x, y, color, stroke);
            for (PointF point : sourcePoints) points.add(new PointF(point.x, point.y));
        }

        void add(PointF point) {
            PointF local = new PointF(point.x - centerX, point.y - centerY);
            PointF last = points.get(points.size() - 1);
            if (Math.hypot(local.x - last.x, local.y - last.y) >= Math.max(1f, strokeWidth * 0.35f)) points.add(local);
        }

        @Override void drawLocal(Canvas canvas, Paint paint) {
            if (points.size() == 1) { canvas.drawPoint(points.get(0).x, points.get(0).y, paint); return; }
            Path path = new Path();
            path.moveTo(points.get(0).x, points.get(0).y);
            for (int i = 1; i < points.size(); i++) path.lineTo(points.get(i).x, points.get(i).y);
            canvas.drawPath(path, paint);
        }

        @Override boolean hitLocal(float x, float y, float tolerance, Paint paint) {
            if (points.size() == 1) return Math.hypot(x - points.get(0).x, y - points.get(0).y) <= tolerance;
            for (int i = 1; i < points.size(); i++) {
                PointF a = points.get(i - 1), b = points.get(i);
                if (EditorMath.distanceToSegment(x, y, a.x, a.y, b.x, b.y) <= tolerance + strokeWidth) return true;
            }
            return false;
        }

        @Override RectF localBounds(Paint paint) {
            float left = 0f, top = 0f, right = 0f, bottom = 0f;
            for (PointF point : points) {
                left = Math.min(left, point.x); top = Math.min(top, point.y);
                right = Math.max(right, point.x); bottom = Math.max(bottom, point.y);
            }
            return new RectF(left, top, right, bottom);
        }

        @Override EditorObject copy() {
            StrokeObject copy = new StrokeObject(id, centerX, centerY, color, strokeWidth, points);
            copyTransformTo(copy); return copy;
        }
    }

    private static final class TransformGesture {
        final float initialSpan;
        final float initialAngle;
        final float initialScale;
        final float initialRotation;
        final float initialCenterX;
        final float initialCenterY;
        final PointF initialMidpoint;

        TransformGesture(EditorObject object, MotionEvent event, ObjectEditorView view) {
            PointF first = view.toImage(event.getX(0), event.getY(0));
            PointF second = view.toImage(event.getX(1), event.getY(1));
            initialSpan = Math.max(1f, (float) Math.hypot(second.x - first.x, second.y - first.y));
            initialAngle = (float) Math.atan2(second.y - first.y, second.x - first.x);
            initialScale = object.scale;
            initialRotation = object.rotation;
            initialCenterX = object.centerX;
            initialCenterY = object.centerY;
            initialMidpoint = new PointF((first.x + second.x) / 2f, (first.y + second.y) / 2f);
        }

        void apply(EditorObject object, MotionEvent event, ObjectEditorView view) {
            PointF first = view.toImage(event.getX(0), event.getY(0));
            PointF second = view.toImage(event.getX(1), event.getY(1));
            float span = Math.max(1f, (float) Math.hypot(second.x - first.x, second.y - first.y));
            float angle = (float) Math.atan2(second.y - first.y, second.x - first.x);
            PointF midpoint = new PointF((first.x + second.x) / 2f, (first.y + second.y) / 2f);
            object.scale = clamp(initialScale * span / initialSpan, 0.2f, 8f);
            object.rotation = initialRotation + EditorMath.angleDeltaDegrees(angle, initialAngle);
            object.centerX = initialCenterX + midpoint.x - initialMidpoint.x;
            object.centerY = initialCenterY + midpoint.y - initialMidpoint.y;
        }
    }
}
