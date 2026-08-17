package ru.darkcat.camera.catlog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/** Strict CAT Log allowlist and bounded text policy. Unknown attributes are dropped. */
public final class CatPrivacy {
    private static final int MAX_TEXT = 512;
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(bearer\\s+|password|passwd|token|api[_-]?key|secret|cookie|authorization|private[_-]?key|ssh)\\s*[:=]?\\s*[^\\s,;]+"
    );
    private static final Set<String> ALLOWED_ATTRIBUTES;

    static {
        Set<String> values = new HashSet<>();
        Collections.addAll(values,
                "screen", "camera_state", "camera_api", "camera_id", "lens", "zoom", "orientation",
                "storage_mode", "sequence", "gps_state", "gps_accuracy_m", "gps_age_ms", "field_state",
                "locker_owner", "foreground", "test_case", "system_top_inset", "display_cutout_top",
                "dashboard_top", "window_width", "window_height", "night_enabled", "night_session_type",
                "night_transition_ms", "night_restore", "capture_outcome", "storage_operation", "storage_result",
                "permission_state", "provider_enabled", "event_count", "dropped_count", "exit_reason",
                "exit_description", "app_version", "build_number", "orientation_relevant",
                "owner", "moving", "stationary_grace_ms", "destination", "bytes", "camera_ready",
                "thermal_status", "power_save");
        ALLOWED_ATTRIBUTES = Collections.unmodifiableSet(values);
    }

    public static Map<String, Object> allowAttributes(Map<String, ?> attributes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (attributes == null) return result;
        for (Map.Entry<String, ?> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || !ALLOWED_ATTRIBUTES.contains(key)) continue;
            Object value = safeValue(entry.getValue());
            if (value != null) result.put(key, value);
        }
        return result;
    }

    public static String text(String value) {
        if (value == null) return null;
        String clean = CONTROL.matcher(value).replaceAll("").trim();
        clean = SECRET.matcher(clean).replaceAll("[redacted]");
        if (clean.length() > MAX_TEXT) clean = clean.substring(0, MAX_TEXT);
        return clean.isEmpty() ? null : clean;
    }

    public static String error(Throwable error) {
        if (error == null) return null;
        String type = error.getClass().getSimpleName();
        String message = text(error.getMessage());
        return message == null ? type : type + ": " + message;
    }

    private static Object safeValue(Object value) {
        if (value == null || value == org.json.JSONObject.NULL) return null;
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double) return value;
        if (value instanceof String) return text((String) value);
        return text(String.valueOf(value));
    }

    private CatPrivacy() { }
}
