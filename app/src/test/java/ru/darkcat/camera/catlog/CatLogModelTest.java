package ru.darkcat.camera.catlog;

import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CatLogModelTest {
    @Test public void eventIsOneValidNdjsonObjectWithTraceAndSpanIds() throws Exception {
        CatEvent event = CatEvent.builder("session-1", "camera", "camera.capture_requested")
                .trace("0123456789abcdef0123456789abcdef", null).wallClock(123L).elapsed(45L)
                .attributes(java.util.Collections.singletonMap("storage_mode", "VAULT")).build();
        String line = event.line();
        assertTrue(line.endsWith("\n"));
        JSONObject json = new JSONObject(line);
        assertEquals(CatEvent.SCHEMA_VERSION, json.getInt("schema_version"));
        assertEquals(32, json.getString("trace_id").length());
        assertEquals(16, json.getString("span_id").length());
        assertEquals("VAULT", json.getJSONObject("attributes").getString("storage_mode"));
    }

    @Test public void allowlistDropsRawCoordinatesAndSanitizesSensitiveText() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("gps_accuracy_m", 4.2f);
        attributes.put("latitude", "55.7558");
        attributes.put("note", "Bearer abcdef");
        Map<String, Object> safe = CatPrivacy.allowAttributes(attributes);
        assertEquals(4.2f, (Float) safe.get("gps_accuracy_m"), .001f);
        assertFalse(safe.containsKey("latitude"));
        assertFalse(safe.containsKey("note"));
        assertEquals("[redacted]", CatPrivacy.text("Bearer abcdef"));
    }

    @Test public void droppedEventsKeepLifetimeCountAfterEvidenceDrain() {
        DroppedEventCounter counter = new DroppedEventCounter();
        counter.record(); counter.record();
        assertEquals(2L, counter.drainPending());
        assertEquals(2L, counter.total());
        assertEquals(0L, counter.drainPending());
        counter.clear();
        assertEquals(0L, counter.total());
    }
}
