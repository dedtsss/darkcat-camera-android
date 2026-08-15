package ru.darkcat.camera.location;

import java.util.Objects;

/** Pure strict-GPS capture gate. Aging-but-not-stale accurate fixes remain usable. */
public final class GpsCapturePolicy {
    private final GpsStateEvaluator stateEvaluator;

    public GpsCapturePolicy() {
        this(new GpsStateEvaluator());
    }

    public GpsCapturePolicy(GpsStateEvaluator stateEvaluator) {
        this.stateEvaluator = Objects.requireNonNull(stateEvaluator, "stateEvaluator");
    }

    public CaptureDecision evaluate(
            GpsSnapshot snapshot,
            long nowElapsedRealtimeNanos,
            GpsPolicy policy) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(policy, "policy");
        GpsState state = stateEvaluator.evaluate(snapshot, nowElapsedRealtimeNanos, policy);
        if (!policy.isStrictCapture()) {
            return CaptureDecision.allowed(state);
        }

        CaptureBlockReason sourceBlock = sourceBlock(snapshot.getSourceStatus());
        if (sourceBlock != null) {
            return CaptureDecision.blocked(sourceBlock, state);
        }
        LocationFix fix = snapshot.getFix();
        if (fix == null) {
            return CaptureDecision.blocked(CaptureBlockReason.NO_FIX, state);
        }
        if (state.getLocationAge() == LocationAge.STALE) {
            return CaptureDecision.blocked(CaptureBlockReason.STALE_FIX, state);
        }
        if (!fix.hasAccuracy()) {
            return CaptureDecision.blocked(CaptureBlockReason.ACCURACY_UNAVAILABLE, state);
        }
        if (fix.getAccuracyMeters() > policy.getMaxAccuracyMeters()) {
            return CaptureDecision.blocked(CaptureBlockReason.ACCURACY_TOO_LOW, state);
        }
        return CaptureDecision.allowed(state);
    }

    private static CaptureBlockReason sourceBlock(GpsSourceStatus status) {
        switch (status) {
            case STOPPED:
                return CaptureBlockReason.GPS_STOPPED;
            case PERMISSION_DENIED:
                return CaptureBlockReason.GPS_PERMISSION_DENIED;
            case LOCATION_DISABLED:
                return CaptureBlockReason.LOCATION_DISABLED;
            case PROVIDER_UNAVAILABLE:
                return CaptureBlockReason.PROVIDER_UNAVAILABLE;
            case ERROR:
                return CaptureBlockReason.GPS_SOURCE_ERROR;
            case RUNNING:
                return null;
            default:
                throw new AssertionError("Unhandled source state: " + status);
        }
    }
}
