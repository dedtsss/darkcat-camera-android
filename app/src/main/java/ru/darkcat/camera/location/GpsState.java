package ru.darkcat.camera.location;

import java.util.Objects;

/** Evaluated UI state. It is intentionally free of localized prose. */
public final class GpsState {
    private final GpsIndicator indicator;
    private final GpsIssue issue;
    private final LocationAge locationAge;
    private final long ageMillis;
    private final float accuracyMeters;
    private final LocationFix fix;

    GpsState(
            GpsIndicator indicator,
            GpsIssue issue,
            LocationAge locationAge,
            long ageMillis,
            float accuracyMeters,
            LocationFix fix) {
        this.indicator = Objects.requireNonNull(indicator, "indicator");
        this.issue = Objects.requireNonNull(issue, "issue");
        this.locationAge = locationAge;
        this.ageMillis = ageMillis;
        this.accuracyMeters = accuracyMeters;
        this.fix = fix;
    }

    public GpsIndicator getIndicator() {
        return indicator;
    }

    public GpsIssue getIssue() {
        return issue;
    }

    public LocationAge getLocationAge() {
        return locationAge;
    }

    public long getAgeMillis() {
        return ageMillis;
    }

    public float getAccuracyMeters() {
        return accuracyMeters;
    }

    public boolean hasAccuracy() {
        return !Float.isNaN(accuracyMeters) && !Float.isInfinite(accuracyMeters);
    }

    public LocationFix getFix() {
        return fix;
    }

    /** Compact Russian label suitable for the preview and conservative notification. */
    public String getAccuracyLabel() {
        return LiveAccuracyFormatter.format(accuracyMeters);
    }
}
