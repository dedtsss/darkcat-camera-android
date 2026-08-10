package ru.darkcat.camera.location;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Pure GPS state machine; safe to run in local JVM tests. */
public final class GpsStateEvaluator {
    public GpsState evaluate(GpsSnapshot snapshot, long nowElapsedRealtimeNanos, GpsPolicy policy) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(policy, "policy");
        if (nowElapsedRealtimeNanos < 0L) {
            throw new IllegalArgumentException("nowElapsedRealtimeNanos must be non-negative");
        }

        GpsIssue sourceIssue = sourceIssue(snapshot.getSourceStatus());
        if (sourceIssue != null) {
            return unavailable(sourceIssue);
        }

        LocationFix fix = snapshot.getFix();
        if (fix == null) {
            return unavailable(GpsIndicator.YELLOW, GpsIssue.SEARCHING);
        }

        if (fix.getElapsedRealtimeNanos() > nowElapsedRealtimeNanos) {
            return new GpsState(
                    GpsIndicator.RED,
                    GpsIssue.MONOTONIC_CLOCK_MISMATCH,
                    LocationAge.STALE,
                    Long.MAX_VALUE,
                    fix.getAccuracyMeters(),
                    fix);
        }

        long ageMillis = TimeUnit.NANOSECONDS.toMillis(
                nowElapsedRealtimeNanos - fix.getElapsedRealtimeNanos());
        LocationAge age = classifyAge(ageMillis, policy);
        if (age == LocationAge.STALE) {
            return state(GpsIndicator.RED, GpsIssue.STALE_FIX, age, ageMillis, fix);
        }
        if (!fix.hasAccuracy()) {
            return state(GpsIndicator.YELLOW, GpsIssue.ACCURACY_UNAVAILABLE, age, ageMillis, fix);
        }
        if (fix.getAccuracyMeters() > policy.getMaxAccuracyMeters()) {
            return state(GpsIndicator.YELLOW, GpsIssue.POOR_ACCURACY, age, ageMillis, fix);
        }
        if (age == LocationAge.AGING) {
            return state(GpsIndicator.YELLOW, GpsIssue.AGING_FIX, age, ageMillis, fix);
        }
        return state(GpsIndicator.GREEN, GpsIssue.NONE, age, ageMillis, fix);
    }

    public LocationAge classifyAge(long ageMillis, GpsPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (ageMillis < 0L || ageMillis > policy.getStaleAgeMillis()) {
            return LocationAge.STALE;
        }
        if (ageMillis > policy.getFreshAgeMillis()) {
            return LocationAge.AGING;
        }
        return LocationAge.FRESH;
    }

    private static GpsIssue sourceIssue(GpsSourceStatus sourceStatus) {
        switch (sourceStatus) {
            case STOPPED:
                return GpsIssue.STOPPED;
            case PERMISSION_DENIED:
                return GpsIssue.PERMISSION_DENIED;
            case LOCATION_DISABLED:
                return GpsIssue.LOCATION_DISABLED;
            case PROVIDER_UNAVAILABLE:
                return GpsIssue.PROVIDER_UNAVAILABLE;
            case ERROR:
                return GpsIssue.SOURCE_ERROR;
            case RUNNING:
                return null;
            default:
                throw new AssertionError("Unhandled source state: " + sourceStatus);
        }
    }

    private static GpsState unavailable(GpsIssue issue) {
        return unavailable(GpsIndicator.RED, issue);
    }

    private static GpsState unavailable(GpsIndicator indicator, GpsIssue issue) {
        return new GpsState(indicator, issue, null, Long.MAX_VALUE, Float.NaN, null);
    }

    private static GpsState state(
            GpsIndicator indicator,
            GpsIssue issue,
            LocationAge age,
            long ageMillis,
            LocationFix fix) {
        return new GpsState(indicator, issue, age, ageMillis, fix.getAccuracyMeters(), fix);
    }
}
