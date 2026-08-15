package ru.darkcat.camera.data;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ru.darkcat.camera.location.LocationFix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PhotoCaptureTicketQueueTest {
    @Test public void claimsInFifoOrderAndAllowsDisabledSequence() {
        PhotoCaptureTicketQueue queue = new PhotoCaptureTicketQueue();
        PhotoCaptureTicket first = queue.enqueue(0, 100);
        PhotoCaptureTicket second = queue.enqueue(42, 200);

        assertEquals(first.ticketId, queue.claim().ticketId);
        assertEquals(0, first.sequenceNumber);
        assertEquals(second.ticketId, queue.claim().ticketId);
        assertEquals(42, second.sequenceNumber);
        assertNull(queue.claim());
    }

    @Test public void concurrentProducersDoNotLoseOrDuplicateTickets() throws Exception {
        PhotoCaptureTicketQueue queue = new PhotoCaptureTicketQueue();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 1; i <= 500; i++) {
            final int sequence = i;
            executor.execute(() -> queue.enqueue(sequence, sequence));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(500, queue.size());

        Set<Integer> sequences = new HashSet<>();
        PhotoCaptureTicket ticket;
        while ((ticket = queue.claim()) != null) sequences.add(ticket.sequenceNumber);
        assertEquals(500, sequences.size());
        for (int i = 1; i <= 500; i++) assertTrue(sequences.contains(i));
    }

    @Test public void explicitDiscardKeepsFollowingTicketAligned() {
        PhotoCaptureTicketQueue queue = new PhotoCaptureTicketQueue();
        queue.enqueue(10, 100);
        queue.enqueue(11, 200);

        assertNotNull(queue.discardOldest());
        assertEquals(11, queue.claim().sequenceNumber);
    }

    @Test public void preservesCaptureTimeLocationWithTicket() {
        PhotoCaptureTicketQueue queue = new PhotoCaptureTicketQueue();
        LocationFix fix = new LocationFix(64.588210, 30.599140, 4.2f,
                123_000_000L, 1_786_310_400_000L, "gps");

        queue.enqueue(427, 1_786_310_400_100L, fix);

        PhotoCaptureTicket claimed = queue.claim();
        assertNotNull(claimed);
        assertEquals(427, claimed.sequenceNumber);
        assertEquals(64.588210, claimed.locationFix.getLatitude(), 0.000001);
        assertEquals(30.599140, claimed.locationFix.getLongitude(), 0.000001);
        assertEquals(4.2f, claimed.locationFix.getAccuracyMeters(), 0.01f);
        assertEquals("gps", claimed.locationFix.getProvider());
    }

    @Test public void expectedClaimRemovesOnlyMatchingCapture() {
        PhotoCaptureTicketQueue queue = new PhotoCaptureTicketQueue();
        PhotoCaptureTicket first = queue.enqueue(1, 100);
        PhotoCaptureTicket second = queue.enqueue(2, 200);

        assertEquals(second.ticketId, queue.claimExpected(second.ticketId).ticketId);
        assertEquals(1, queue.size());
        assertEquals(first.ticketId, queue.claimExpected(first.ticketId).ticketId);
        assertNull(queue.claim());
    }
}
