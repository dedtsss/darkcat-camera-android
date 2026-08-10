package ru.darkcat.camera.data;

import ru.darkcat.camera.location.LocationFix;

/** Immutable hand-off from camera-success callback to the asynchronous image saver. */
public final class PhotoCaptureTicket {
    public final long ticketId;
    public final int sequenceNumber;
    public final long capturedAt;
    public final LocationFix locationFix;

    PhotoCaptureTicket(long ticketId, int sequenceNumber, long capturedAt) {
        this(ticketId, sequenceNumber, capturedAt, null);
    }

    PhotoCaptureTicket(long ticketId, int sequenceNumber, long capturedAt, LocationFix locationFix) {
        this.ticketId = ticketId;
        this.sequenceNumber = sequenceNumber;
        this.capturedAt = capturedAt;
        this.locationFix = locationFix;
    }
}
