package ru.darkcat.camera.gallery;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import ru.darkcat.camera.data.CaptureContext;

/**
 * Android-free serialization for a recovery sidecar. It deliberately does not use JSONObject:
 * the journal is written before publication and must be independently testable on the JVM.
 */
final class CaptureContextJournalCodec {
    private static final String VERSION = "dcj1";

    static String encode(CaptureContext context) {
        CaptureContext value = context == null ? CaptureContext.empty() : context;
        StringBuilder tags = new StringBuilder();
        for (String tag : value.customTags) {
            if (tags.length() > 0) tags.append(',');
            tags.append(text(tag));
        }
        return VERSION + "|" + text(value.crmObjectId) + "|" + text(value.inspectionId) + "|"
                + text(value.taskId) + "|" + text(value.userId) + "|"
                + number(value.captureLatitude) + "|" + number(value.captureLongitude) + "|"
                + number(value.captureAccuracyMeters) + "|" + value.captureLocationElapsedRealtimeNanos + "|"
                + text(value.captureLocationProvider) + "|" + tags;
    }

    static CaptureContext decode(String serialized) {
        if (serialized == null) return CaptureContext.empty();
        try {
            String[] values = serialized.split("\\|", -1);
            if (values.length != 11 || !VERSION.equals(values[0])) return CaptureContext.empty();
            List<String> tags = new ArrayList<>();
            if (!values[10].isEmpty()) for (String encoded : values[10].split(",", -1)) {
                String tag = textValue(encoded);
                if (tag != null) tags.add(tag);
            }
            return new CaptureContext(textValue(values[1]), textValue(values[2]), textValue(values[3]),
                    textValue(values[4]), tags, decimal(values[5]), decimal(values[6]),
                    floatValue(values[7]), elapsed(values[8]), textValue(values[9]));
        } catch (RuntimeException invalid) {
            return CaptureContext.empty();
        }
    }

    static boolean isEncoded(String value) {
        return value != null && value.startsWith(VERSION + "|");
    }

    private static String text(String value) {
        if (value == null) return "N";
        try { return "V" + URLEncoder.encode(value, StandardCharsets.UTF_8.name()); }
        catch (Exception impossible) { throw new IllegalStateException("UTF-8 unavailable", impossible); }
    }

    private static String textValue(String value) {
        if (value == null || "N".equals(value)) return null;
        if (!value.startsWith("V")) throw new IllegalArgumentException("invalid text value");
        try { return URLDecoder.decode(value.substring(1), StandardCharsets.UTF_8.name()); }
        catch (Exception invalid) { throw new IllegalArgumentException("invalid text encoding", invalid); }
    }

    private static String number(Number value) {
        if (value == null) return "";
        double numeric = value.doubleValue();
        return Double.isNaN(numeric) || Double.isInfinite(numeric) ? "" : String.valueOf(value);
    }

    private static Double decimal(String value) {
        if (value == null || value.isEmpty()) return null;
        double numeric = Double.parseDouble(value);
        return Double.isNaN(numeric) || Double.isInfinite(numeric) ? null : numeric;
    }

    private static Float floatValue(String value) {
        if (value == null || value.isEmpty()) return null;
        float numeric = Float.parseFloat(value);
        return Float.isNaN(numeric) || Float.isInfinite(numeric) ? null : numeric;
    }

    private static long elapsed(String value) {
        if (value == null || value.isEmpty()) return 0L;
        long numeric = Long.parseLong(value);
        if (numeric < 0L) throw new IllegalArgumentException("negative elapsed time");
        return numeric;
    }

    private CaptureContextJournalCodec() { }
}
