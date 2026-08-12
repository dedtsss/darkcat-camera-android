package ru.darkcat.camera.stamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TechnicalStampFormatter {
    public static List<String> lines(Double latitude, Double longitude, Float accuracyMeters,
                                     Integer sequence, List<String> tags, String customText,
                                     boolean includeCoordinates, boolean includeAccuracy,
                                     boolean includeSequence, boolean includeTags, boolean includeCustomText) {
        ArrayList<String> result = new ArrayList<>();
        if (includeCoordinates && latitude != null && longitude != null) {
            String coordinates = String.format(Locale.US, "N %.6f   E %.6f", latitude, longitude);
            if (includeAccuracy && accuracyMeters != null && !Float.isNaN(accuracyMeters)
                    && !Float.isInfinite(accuracyMeters)) coordinates += String.format(Locale.US, "   ±%.1fm", accuracyMeters);
            result.add(coordinates);
        }
        if (includeSequence && sequence != null) result.add(String.format(Locale.US, "№%05d", sequence));
        if (includeTags && tags != null && !tags.isEmpty()) result.add(join(tags));
        if (includeCustomText && customText != null && !customText.trim().isEmpty()) result.add(customText.trim());
        return result;
    }

    private static String join(List<String> values) {
        StringBuilder output = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (output.length() > 0) output.append(" · ");
            output.append(value.trim());
        }
        return output.toString();
    }

    private TechnicalStampFormatter() { }
}
