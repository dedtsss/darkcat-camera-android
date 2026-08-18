package ru.darkcat.camera.catlog;

import java.util.concurrent.atomic.AtomicLong;

/** Keeps the lifetime count for status/export and a drainable count for one evidence event. */
final class DroppedEventCounter {
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong pending = new AtomicLong();

    void record() { total.incrementAndGet(); pending.incrementAndGet(); }
    long total() { return total.get(); }
    long pending() { return pending.get(); }
    long drainPending() { return pending.getAndSet(0L); }
    void clear() { total.set(0L); pending.set(0L); }
}
