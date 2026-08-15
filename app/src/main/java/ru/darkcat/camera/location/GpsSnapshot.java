package ru.darkcat.camera.location;

import java.util.Objects;

/** Last known runtime source state and optional fix. */
public final class GpsSnapshot {
    private final GpsSourceStatus sourceStatus;
    private final LocationFix fix;

    private GpsSnapshot(GpsSourceStatus sourceStatus, LocationFix fix) {
        this.sourceStatus = Objects.requireNonNull(sourceStatus, "sourceStatus");
        this.fix = fix;
    }

    public static GpsSnapshot stopped() {
        return new GpsSnapshot(GpsSourceStatus.STOPPED, null);
    }

    public static GpsSnapshot running(LocationFix fix) {
        return new GpsSnapshot(GpsSourceStatus.RUNNING, fix);
    }

    public static GpsSnapshot unavailable(GpsSourceStatus status) {
        if (status == GpsSourceStatus.RUNNING) {
            throw new IllegalArgumentException("use running() for RUNNING status");
        }
        return new GpsSnapshot(status, null);
    }

    public GpsSourceStatus getSourceStatus() {
        return sourceStatus;
    }

    public LocationFix getFix() {
        return fix;
    }

    public boolean hasFix() {
        return fix != null;
    }
}
