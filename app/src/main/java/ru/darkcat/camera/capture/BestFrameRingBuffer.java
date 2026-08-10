package ru.darkcat.camera.capture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded metadata-only ring; preview image ownership stays with the camera engine. */
public final class BestFrameRingBuffer {
    private final int capacity;
    private final ArrayDeque<FrameCandidate> samples = new ArrayDeque<>();

    public BestFrameRingBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public synchronized void add(FrameCandidate candidate) {
        if (candidate == null) return;
        while (samples.size() >= capacity) samples.removeFirst();
        samples.addLast(candidate);
    }

    public synchronized FrameCandidate best(long shutterTimestampNanos, long windowNanos) {
        List<FrameCandidate> window = new ArrayList<>();
        for (FrameCandidate candidate : samples) {
            if (Math.abs(candidate.timestampNanos - shutterTimestampNanos) <= windowNanos) window.add(candidate);
        }
        return BestFrameScorer.choose(window, shutterTimestampNanos);
    }

    public synchronized int size() { return samples.size(); }
}
