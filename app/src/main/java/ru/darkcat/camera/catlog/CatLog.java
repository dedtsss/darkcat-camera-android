package ru.darkcat.camera.catlog;

import android.content.Context;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Passive, local-only CAT Log facade used from existing camera/application paths. */
public final class CatLog {
    private static final Object LOCK = new Object();
    private static volatile CatLog instance;
    private final Context context;
    private final CatSessionManager session;
    private final BoundedCatWriter writer;
    private final CatCrashRecorder crashRecorder;
    private final AtomicBoolean recording = new AtomicBoolean(true);
    private final String traceId = CatEvent.traceId();
    private volatile CatSnapshotProvider snapshotProvider;
    private volatile JSONObject previousExit = new JSONObject();
    private volatile String motionState = "UNKNOWN";
    private volatile boolean motionMoving;
    private final AtomicLong motionElapsedMs = new AtomicLong();

    private CatLog(Context context) {
        this.context = context.getApplicationContext();
        session = new CatSessionManager(this.context);
        writer = new BoundedCatWriter(session.directory(), session.id());
        recording.set(session.isActive());
        crashRecorder = new CatCrashRecorder(this.context);
        crashRecorder.install();
        if (session.recovered()) {
            event("session", "session.interrupted_recovered", null, "previous session was not cleanly stopped", "recovered session evidence", null,
                    map("night_restore", "recovered_session"));
        }
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("app_version", appVersion());
        app.put("build_number", appBuild());
        app.put("orientation_relevant", true);
        event("session", "session.start", "start", "passive CAT logging active", session.recovered() ? "recovered" : "new", null, app);
        event("app", "app.foreground", "foreground", "camera process available", "foreground", null, null);
        previousExit = crashRecorder.collectPreviousExit();
        pruneOldSessions();
    }

    public static void initialize(Context context) {
        if (context == null || instance != null) return;
        synchronized (LOCK) { if (instance == null) instance = new CatLog(context); }
    }

    private static CatLog get() { return instance; }
    static CatLog current() { return instance; }

    public static void setSnapshotProvider(CatSnapshotProvider provider) { if (get() != null) get().snapshotProvider = provider; }
    public static void setTestCase(String testCase) { CatTestContext.set(testCase); }
    public static void clearTestCase() { CatTestContext.clear(); }

    public static void event(String component, String name, String action, String expected, String actual,
                             String error, Map<String, ?> attributes) {
        CatLog current = get();
        if (current != null) current.record(component, name, action, expected, actual, null, null, error, attributes);
    }

    public static void result(String component, String name, String action, String expected, String actual,
                              String result, Map<String, ?> attributes) {
        CatLog current = get();
        if (current != null) current.record(component, name, action, expected, actual, result, null, null, attributes);
    }

    public static void note(String note) {
        event("ui", "user.note", "add_note", "note retained locally", CatPrivacy.text(note), null,
                Collections.singletonMap("screen", "diagnostics"));
    }

    public static void markProblem() {
        CatLog current = get();
        if (current == null) return;
        Map<String, ?> snapshot = current.snapshotProvider == null ? Collections.emptyMap() : current.snapshotProvider.snapshot();
        current.record("ui", "user.problem_marked", "mark_problem", "problem marker recorded", "recorded", "PASS", null, null, snapshot);
    }

    public static void startSession() {
        CatLog current = get();
        if (current == null) return;
        current.session.markStarted();
        current.recording.set(true);
        current.record("session", "session.start", "start", "new diagnostic session", "started", "PASS", null, null, null);
    }

    public static void stopSession() {
        CatLog current = get();
        if (current == null) return;
        current.flush(500L);
        long eventCountIncludingStop = current.writer.writtenCount()
                + (current.writer.pendingDroppedCount() > 0L ? 1L : 0L) + 1L;
        current.record("session", "session.stop", "stop", "clean stop requested", "stopped", "PASS", null, null,
                map("event_count", eventCountIncludingStop, "dropped_count", current.writer.droppedCount()));
        current.flush(500L);
        current.session.markStopped();
        current.recording.set(false);
    }

    public static void foreground(boolean foreground) {
        if (get() != null) event("app", foreground ? "app.foreground" : "app.background", foreground ? "foreground" : "background", null, null, null, null);
    }

    public static boolean isRecording() { return get() != null && get().recording.get(); }
    public static String sessionId() { return get() == null ? null : get().session.id(); }
    public static long eventCount() { return get() == null ? 0 : get().writer.writtenCount(); }
    public static long droppedCount() { return get() == null ? 0 : get().writer.droppedCount(); }
    public static Map<String, Object> motionEvidence() {
        CatLog current = get();
        if (current == null) return Collections.emptyMap();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("motion_state", current.motionState);
        values.put("motion_moving", current.motionMoving);
        long elapsed = current.motionElapsedMs.get();
        values.put("motion_age_ms", elapsed == 0L ? 0L
                : Math.max(0L, android.os.SystemClock.elapsedRealtime() - elapsed));
        return values;
    }
    public static void updateMotionEvidence(String state, boolean moving, long elapsedMs) {
        CatLog current = get();
        if (current == null) return;
        current.motionState = state == null ? "UNKNOWN" : state;
        current.motionMoving = moving;
        current.motionElapsedMs.set(elapsedMs);
    }
    public static File sessionDirectory() { return get() == null ? null : get().session.directory(); }
    public static File rootDirectory(Context context) { return new File(context.getApplicationContext().getFilesDir(), "cat-log"); }
    public static void flush(long timeoutMs) { if (get() != null) get().writer.flush(timeoutMs); }

    public static void clear() {
        CatLog current = get();
        if (current == null) return;
        current.flush(500L);
        current.writer.clear();
        File root = rootDirectory(current.context);
        File[] sessions = new File(root, "sessions").listFiles(File::isDirectory);
        if (sessions != null) for (File directory : sessions) delete(directory);
        current.session.directory().mkdirs();
        current.session.markStarted();
        current.recording.set(true);
        current.record("session", "session.start", "clear", "fresh session after CAT data clear", "started", "PASS", null, null, null);
    }

    public static JSONObject status() {
        CatLog current = get();
        JSONObject json = new JSONObject();
        if (current == null) return json;
        try {
            json.put("recording", current.recording.get()).put("session_id", current.session.id())
                    .put("event_count", current.writer.writtenCount()).put("dropped_count", current.writer.droppedCount())
                    .put("test_case", CatTestContext.get() == null ? JSONObject.NULL : CatTestContext.get())
                    .put("max_events", BoundedCatWriter.MAX_EVENTS).put("max_bytes", BoundedCatWriter.MAX_BYTES);
        } catch (Exception ignored) { }
        return json;
    }

    static JSONObject sessionInfoJson() throws org.json.JSONException {
        CatLog current = get();
        JSONObject json = status();
        json.put("schema_version", CatEvent.SCHEMA_VERSION)
                .put("retention_sessions", 5)
                .put("max_events", BoundedCatWriter.MAX_EVENTS)
                .put("max_bytes", BoundedCatWriter.MAX_BYTES)
                .put("logcat_available", false);
        if (current != null) json.put("interrupted_recovered", current.session.recovered());
        return json;
    }

    static JSONObject exitInfoJson() { return get() == null ? new JSONObject() : get().previousExit; }
    static JSONObject appInfoJson() throws org.json.JSONException { return get() == null ? new JSONObject() : get().session.appInfoJson(); }
    static JSONObject deviceInfoJson() throws org.json.JSONException { return get() == null ? new JSONObject() : get().session.deviceInfoJson(); }

    static void resetForTests() {
        synchronized (LOCK) {
            if (instance != null) instance.writer.close();
            instance = null;
            CatTestContext.clear();
        }
    }

    private void record(String component, String name, String action, String expected, String actual, String result,
                        String evidence, String error, Map<String, ?> attributes) {
        if (!recording.get() && !"session.stop".equals(name) && !"session.start".equals(name)) return;
        CatEvent event = CatEvent.builder(session.id(), component, name).trace(traceId, null)
                .action(action).expected(expected).actual(actual).result(result).evidence(evidence).error(error)
                .attributes(attributes).build();
        writer.offer(event);
    }

    private String appVersion() {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "unknown"; }
    }
    private long appBuild() {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) { return 0L; }
    }

    private void pruneOldSessions() {
        File root = new File(context.getFilesDir(), "cat-log/sessions");
        File[] sessions = root.listFiles(File::isDirectory);
        if (sessions == null || sessions.length <= 5) return;
        java.util.Arrays.sort(sessions, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        for (int index = 5; index < sessions.length; index++) delete(sessions[index]);
    }
    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }

    private CatLog() { throw new AssertionError(); }
}
