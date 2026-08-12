package ru.darkcat.camera.vault;

/** Pure retention rule for plaintext files prepared for another app through FileProvider. */
public final class VaultShareCachePolicy {
    /** Long enough for a receiver to open a chooser URI; bounded on the next app start. */
    public static final long TTL_MILLIS = 30L * 60L * 1000L;

    public static boolean shouldDelete(long modifiedAtMillis, long nowMillis) {
        if (modifiedAtMillis <= 0L) return true;
        if (nowMillis < modifiedAtMillis) return false; // Clock rollback must never cause eager deletion.
        return nowMillis - modifiedAtMillis >= TTL_MILLIS;
    }

    private VaultShareCachePolicy() { }
}
