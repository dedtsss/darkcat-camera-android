package com.darkcat.camera;

import android.location.Location;
import java.util.Locale;

/** Stable, side-effect-free metadata contract used by capture and diagnostics. */
public final class CaptureMetadata {
    public final String fileName; public final long capturedAtMs; public final Location location;
    public CaptureMetadata(String fileName, long capturedAtMs, Location location) { this.fileName=fileName; this.capturedAtMs=capturedAtMs; this.location=location; }
    public String technicalStamp() { return location == null ? "GPS unavailable" : String.format(Locale.US,"%.6f, %.6f",location.getLatitude(),location.getLongitude()); }
}
