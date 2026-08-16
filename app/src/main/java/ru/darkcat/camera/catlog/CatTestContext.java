package ru.darkcat.camera.catlog;

/** Process-local CAT/Maestro correlation context; no production deep link or endpoint is needed. */
public final class CatTestContext {
    private static volatile String current;

    public static void set(String testCase) {
        if (testCase == null || testCase.trim().isEmpty()) { current = null; return; }
        String value = testCase.trim();
        current = value.matches("CAT-[0-9]{2}") ? value : null;
    }

    public static String get() { return current; }
    public static void clear() { current = null; }
    private CatTestContext() { }
}
