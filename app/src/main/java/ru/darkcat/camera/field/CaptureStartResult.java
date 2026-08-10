package ru.darkcat.camera.field;

import java.util.Objects;

import ru.darkcat.camera.location.CaptureDecision;

public final class CaptureStartResult {
    private final CaptureStartStatus status;
    private final CaptureDecision gpsDecision;

    CaptureStartResult(CaptureStartStatus status, CaptureDecision gpsDecision) {
        this.status = Objects.requireNonNull(status, "status");
        this.gpsDecision = Objects.requireNonNull(gpsDecision, "gpsDecision");
    }

    public CaptureStartStatus getStatus() {
        return status;
    }

    public CaptureDecision getGpsDecision() {
        return gpsDecision;
    }
}
