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
            String coordinates = coordinate(latitude, 'N', 'S') + " " + coordinate(longitude, 'E', 'W');
            if (includeAccuracy && accuracyMeters != null && !Float.isNaN(accuracyMeters)
                    && !Float.isInfinite(accuracyMeters)) coordinates += " " + accuracy(accuracyMeters);
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

    private static String coordinate(double value, char positive, char negative) {
        char hemisphere = value < 0d ? negative : positive;
        return decimal(Math.abs(value), 6) + hemisphere;
    }

    private static String accuracy(float value) {
        return "±" + decimal(value, 1) + "м";
    }

    /** Product stamps deliberately use a stable Russian decimal comma independent of device locale. */
    private static String decimal(double value, int digits) {
        return String.format(Locale.US, "% ." + digits + "f", value).trim().replace('.', ',');
    }

    private TechnicalStampFormatter() { }
}
