package ru.darkcat.camera.catlog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Immutable, OpenTelemetry-shaped event without an SDK or remote exporter. */
public final class CatEvent {
    public static final int SCHEMA_VERSION = 1;
    private final long wallClockMs;
    private final long elapsedMs;
    private final String sessionId;
    private final String eventId;
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String testCase;
    private final String component;
    private final String event;
    private final String action;
    private final String expected;
    private final String actual;
    private final String result;
    private final String evidence;
    private final Map<String, Object> attributes;
    private final String error;

    private CatEvent(Builder b) {
        wallClockMs = b.wallClockMs;
        elapsedMs = b.elapsedMs;
        sessionId = b.sessionId;
        eventId = b.eventId;
        traceId = b.traceId;
        spanId = b.spanId;
        parentSpanId = b.parentSpanId;
        testCase = b.testCase;
        component = CatPrivacy.text(b.component);
        event = CatPrivacy.text(b.event);
        action = CatPrivacy.text(b.action);
        expected = CatPrivacy.text(b.expected);
        actual = CatPrivacy.text(b.actual);
        result = CatPrivacy.text(b.result);
        evidence = CatPrivacy.text(b.evidence);
        attributes = Collections.unmodifiableMap(CatPrivacy.allowAttributes(b.attributes));
        error = CatPrivacy.text(b.error);
    }

    public JSONObject toJson() throws org.json.JSONException {
        JSONObject json = new JSONObject();
        json.put("schema_version", SCHEMA_VERSION);
        json.put("wall_clock_ms", wallClockMs);
        json.put("elapsed_realtime_ms", elapsedMs);
        json.put("session_id", sessionId);
        json.put("event_id", eventId);
        json.put("trace_id", traceId);
        json.put("span_id", spanId);
        if (parentSpanId != null) json.put("parent_span_id", parentSpanId);
        if (testCase != null) json.put("test_case", testCase);
        if (component != null) json.put("component", component);
        if (event != null) json.put("event", event);
        if (action != null) json.put("action", action);
        if (expected != null) json.put("expected", expected);
        if (actual != null) json.put("actual", actual);
        if (result != null) json.put("result", result);
        if (evidence != null) json.put("evidence", evidence);
        if (!attributes.isEmpty()) json.put("attributes", new JSONObject(attributes));
        if (error != null) json.put("error", error);
        return json;
    }

    public String line() throws org.json.JSONException { return toJson().toString() + "\n"; }
    public String event() { return event; }
    public String sessionId() { return sessionId; }

    public static Builder builder(String sessionId, String component, String event) {
        return new Builder(sessionId, component, event);
    }

    public static String traceId() { return hex(16); }
    public static String spanId() { return hex(8); }

    private static String hex(int bytes) {
        String value = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        return value.substring(0, bytes * 2).toLowerCase(java.util.Locale.US);
    }

    public static final class Builder {
        private final String sessionId;
        private final String component;
        private final String event;
        private long wallClockMs = System.currentTimeMillis();
        private long elapsedMs = System.nanoTime() / 1_000_000L;
        private String eventId = hex(16);
        private String traceId = CatEvent.traceId();
        private String spanId = CatEvent.spanId();
        private String parentSpanId;
        private String testCase = CatTestContext.get();
        private String action;
        private String expected;
        private String actual;
        private String result;
        private String evidence;
        private String error;
        private Map<String, ?> attributes = new LinkedHashMap<>();

        private Builder(String sessionId, String component, String event) {
            this.sessionId = CatPrivacy.text(sessionId);
            this.component = component;
            this.event = event;
        }

        public Builder trace(String trace, String parentSpan) { traceId = trace; parentSpanId = parentSpan; return this; }
        public Builder action(String value) { action = value; return this; }
        public Builder expected(String value) { expected = value; return this; }
        public Builder actual(String value) { actual = value; return this; }
        public Builder result(String value) { result = value; return this; }
        public Builder evidence(String value) { evidence = value; return this; }
        public Builder error(Throwable value) { error = CatPrivacy.error(value); return this; }
        public Builder error(String value) { error = value; return this; }
        public Builder attributes(Map<String, ?> value) { attributes = value == null ? Collections.emptyMap() : value; return this; }
        public Builder wallClock(long value) { wallClockMs = value; return this; }
        public Builder elapsed(long value) { elapsedMs = value; return this; }
        public CatEvent build() { return new CatEvent(this); }
    }

    private CatEvent() { throw new AssertionError(); }
}
