package ru.darkcat.camera.data;

import java.util.ArrayDeque;
import java.util.Iterator;

import ru.darkcat.camera.location.LocationFix;

/** Thread-safe FIFO bridging camera callbacks and ImageSaver worker threads. */
public final class PhotoCaptureTicketQueue {
    private final ArrayDeque<PhotoCaptureTicket> tickets = new ArrayDeque<>();
    private long nextTicketId = 1;

    public synchronized PhotoCaptureTicket enqueue(int sequenceNumber, long capturedAt) {
        return enqueue(sequenceNumber, capturedAt, null);
    }

    public synchronized PhotoCaptureTicket enqueue(int sequenceNumber, long capturedAt,
                                                   LocationFix locationFix) {
        if (sequenceNumber < 0) throw new IllegalArgumentException("sequence must be positive or zero when disabled");
        PhotoCaptureTicket ticket = new PhotoCaptureTicket(nextTicketId++, sequenceNumber,
                capturedAt > 0 ? capturedAt : System.currentTimeMillis(), locationFix);
        tickets.addLast(ticket);
        return ticket;
    }

    public synchronized PhotoCaptureTicket claim() { return tickets.pollFirst(); }

    /** Claims only the expected callback ticket, without corrupting FIFO alignment on mismatch. */
    public synchronized PhotoCaptureTicket claimExpected(long ticketId) {
        Iterator<PhotoCaptureTicket> iterator = tickets.iterator();
        while (iterator.hasNext()) {
            PhotoCaptureTicket ticket = iterator.next();
            if (ticket.ticketId == ticketId) {
                iterator.remove();
                return ticket;
            }
        }
        return null;
    }

    /** Call when the corresponding ImageSaver request is abandoned before interception. */
    public synchronized PhotoCaptureTicket discardOldest() { return tickets.pollFirst(); }

    public synchronized int size() { return tickets.size(); }

    public synchronized void clear() { tickets.clear(); }
}
