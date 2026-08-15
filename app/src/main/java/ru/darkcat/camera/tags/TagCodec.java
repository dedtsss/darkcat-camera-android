package ru.darkcat.camera.tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Length-prefixed codec preserves spaces, symbols and emoji without delimiter ambiguity. */
public final class TagCodec {
    public static String encode(List<String> tags) {
        StringBuilder out = new StringBuilder();
        if (tags != null) for (String raw : tags) {
            String tag = normalize(raw);
            if (tag == null) continue;
            out.append(tag.length()).append(':').append(tag);
        }
        return out.toString();
    }

    public static List<String> decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Collections.emptyList();
        ArrayList<String> result = new ArrayList<>();
        int offset = 0;
        while (offset < encoded.length()) {
            int colon = encoded.indexOf(':', offset);
            if (colon < 0) break;
            int length;
            try { length = Integer.parseInt(encoded.substring(offset, colon)); }
            catch (NumberFormatException invalid) { break; }
            int start = colon + 1;
            int end = start + length;
            if (length < 0 || end > encoded.length()) break;
            String tag = normalize(encoded.substring(start, end));
            if (tag != null && !result.contains(tag)) result.add(tag);
            offset = end;
        }
        return result;
    }

    public static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().replace('\n', ' ').replace('\r', ' ');
        if (normalized.isEmpty()) return null;
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private TagCodec() { }
}
