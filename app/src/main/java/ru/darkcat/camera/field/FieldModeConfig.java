package ru.darkcat.camera.field;

import ru.darkcat.camera.location.GpsPolicy;

/** User-approved Field Mode configuration; no secret or media metadata is stored here. */
public final class FieldModeConfig {
    private final boolean keepCameraWarm;
    private final boolean gpsLockerEnabled;
    private final boolean volumeUpShutterEnabled;
    private final GpsPolicy gpsPolicy;

    public FieldModeConfig(
            boolean keepCameraWarm,
            boolean gpsLockerEnabled,
            boolean volumeUpShutterEnabled,
            GpsPolicy gpsPolicy) {
        this.keepCameraWarm = keepCameraWarm;
        this.gpsLockerEnabled = gpsLockerEnabled;
        this.volumeUpShutterEnabled = volumeUpShutterEnabled;
        if (gpsPolicy == null) {
            throw new NullPointerException("gpsPolicy");
        }
        this.gpsPolicy = gpsPolicy;
    }

    public static FieldModeConfig defaults() {
        return new FieldModeConfig(true, true, true, GpsPolicy.strictDefault());
    }

    public boolean shouldKeepCameraWarm() {
        return keepCameraWarm;
    }

    public boolean isGpsLockerEnabled() {
        return gpsLockerEnabled;
    }

    public boolean isVolumeUpShutterEnabled() {
        return volumeUpShutterEnabled;
    }

    public GpsPolicy getGpsPolicy() {
        return gpsPolicy;
    }
}
