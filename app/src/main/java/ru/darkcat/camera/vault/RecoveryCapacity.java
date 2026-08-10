package ru.darkcat.camera.vault;

import android.content.Context;
import android.os.StatFs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Preallocated private emergency space for one last field JPEG when storage fills unexpectedly. */
public final class RecoveryCapacity {
    public static final long RESERVE_BYTES = 64L * 1024L * 1024L;
    public static final long MIN_WORKING_BYTES = 64L * 1024L * 1024L;
    private static final String NAME = ".capture-emergency-reserve";

    public static synchronized boolean hasCaptureCapacity(Context context) {
        File directory = new VaultRepository(context).recoveryDir();
        long available = new StatFs(directory.getAbsolutePath()).getAvailableBytes();
        File reserve = reserve(context);
        long required = reserve.isFile() && reserve.length() == RESERVE_BYTES
                ? MIN_WORKING_BYTES : MIN_WORKING_BYTES + RESERVE_BYTES;
        return available >= required;
    }

    /** Writes real blocks (not a sparse setLength reservation), then publishes atomically. */
    public static synchronized void ensureReserve(Context context) throws IOException {
        File destination = reserve(context);
        if (destination.isFile() && destination.length() == RESERVE_BYTES) return;
        File parent = destination.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs()))
            throw new IOException("recovery reserve directory unavailable");
        if (new StatFs(parent.getAbsolutePath()).getAvailableBytes()
                < RESERVE_BYTES + MIN_WORKING_BYTES) return;
        File temporary = new File(parent, NAME + ".tmp");
        byte[] block = new byte[1024 * 1024];
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                for (long written = 0; written < RESERVE_BYTES; written += block.length)
                    output.write(block, 0, (int) Math.min(block.length, RESERVE_BYTES - written));
                output.flush();
                output.getFD().sync();
            }
            if (temporary.length() != RESERVE_BYTES)
                throw new IOException("incomplete recovery reserve");
            if (destination.exists() && !destination.delete())
                throw new IOException("unable to replace recovery reserve");
            if (!temporary.renameTo(destination))
                throw new IOException("unable to publish recovery reserve");
        } finally {
            if (temporary.exists()) { //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    /** Frees preallocated blocks for an immediate retry after an app-private write failure. */
    public static synchronized boolean releaseReserve(Context context) {
        File file = reserve(context);
        return file.isFile() && file.delete();
    }

    /** Tiny durable probe used only after a previously persisted storage failure. */
    public static synchronized boolean recoverStorageHealth(Context context) {
        if (!hasCaptureCapacity(context)) return false;
        File probe = new File(new VaultRepository(context).recoveryDir(), ".capture-probe.tmp");
        try (FileOutputStream output = new FileOutputStream(probe)) {
            output.write(new byte[4096]);
            output.flush();
            output.getFD().sync();
            return probe.length() == 4096L;
        } catch (IOException unavailable) {
            return false;
        } finally {
            if (probe.exists()) { //noinspection ResultOfMethodCallIgnored
                probe.delete();
            }
        }
    }

    private static File reserve(Context context) {
        File vault = new File(context.getApplicationContext().getFilesDir(), "darkcat-vault");
        return new File(vault, NAME);
    }

    private RecoveryCapacity() { }
}
