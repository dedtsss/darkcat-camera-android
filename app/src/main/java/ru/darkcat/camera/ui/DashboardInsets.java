package ru.darkcat.camera.ui;

/** Converts a window-safe top coordinate to the existing overlay parent's coordinate space. */
public final class DashboardInsets {
    public static int topMargin(int safeTopInWindow, int parentTopInWindow) {
        return Math.max(0, safeTopInWindow - parentTopInWindow);
    }

    private DashboardInsets() { }
}
