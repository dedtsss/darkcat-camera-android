package ru.darkcat.camera.catlog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** One non-blocking producer queue and one sequential file writer for the active session. */
final class BoundedCatWriter {
    static final int MAX_QUEUE = 256;
    static final int MAX_EVENTS = 10_000;
    static final long MAX_BYTES = 2L * 1024L * 1024L;
    private final ArrayBlockingQueue<CatEvent> queue = new ArrayBlockingQueue<>(MAX_QUEUE);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "darkcat-cat-log-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final DroppedEventCounter dropped = new DroppedEventCounter();
    private final java.util.concurrent.atomic.AtomicLong written = new java.util.concurrent.atomic.AtomicLong();
    private final File output;
    private final String sessionId;

    BoundedCatWriter(File sessionDirectory, String sessionId) {
        output = new File(sessionDirectory, "cat-events.ndjson");
        this.sessionId = sessionId;
        restoreExistingFile();
        executor.execute(this::run);
    }

    boolean offer(CatEvent event) {
        if (event == null || !queue.offer(event)) {
            noteDropped();
            return false;
        }
        return true;
    }

    long writtenCount() { return written.get(); }
    long droppedCount() { return dropped.total(); }
    long pendingDroppedCount() { return dropped.pending(); }
    File output() { return output; }

    void flush(long timeoutMs) {
        try {
            Future<?> barrier = executor.submit(() -> { });
            barrier.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) { }
    }

    void clear() {
        // The barrier is required before deleting the file: the writer may already have
        // taken an event from the queue while the UI requested Clear.
        flush(1_500L);
        queue.clear();
        dropped.clear();
        written.set(0L);
        if (output.exists()) output.delete();
    }

    void close() {
        flush(1_500L);
        executor.shutdownNow();
    }

    private void restoreExistingFile() {
        if (!output.isFile()) return;
        File cleaned = new File(output.getParentFile(), output.getName() + ".recovered");
        try (BufferedReader reader = new BufferedReader(new FileReader(output));
             BufferedWriter writer = new BufferedWriter(new FileWriter(cleaned, false))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || "null".equals(trimmed)) {
                    dropped.record();
                    continue;
                }
                try {
                    new org.json.JSONObject(trimmed);
                    writer.write(trimmed);
                    writer.newLine();
                    written.incrementAndGet();
                } catch (Exception malformed) {
                    dropped.record();
                }
            }
        } catch (Exception ignored) {
            cleaned.delete();
            return;
        }
        if (!cleaned.renameTo(output)) {
            cleaned.delete();
        }
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                CatEvent event = queue.take();
                writeDroppedIfNeeded();
                append(event);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                noteDropped();
            }
        }
    }

    private void writeDroppedIfNeeded() throws Exception {
        long count = dropped.drainPending();
        if (count <= 0L) return;
        CatEvent event = CatEvent.builder(sessionId, "cat-log", "logger.events_dropped")
                .result("PARTIAL")
                .attributes(java.util.Collections.singletonMap("dropped_count", count))
                .build();
        append(event);
    }

    private void append(CatEvent event) throws Exception {
        if (written.get() >= MAX_EVENTS || output.length() >= MAX_BYTES) {
            noteDropped();
            return;
        }
        File parent = output.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(output, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
            String line = event.line();
            if (output.length() + line.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                noteDropped();
                return;
            }
            writer.write(line);
        }
        written.incrementAndGet();
    }

    private void noteDropped() {
        dropped.record();
    }
}
