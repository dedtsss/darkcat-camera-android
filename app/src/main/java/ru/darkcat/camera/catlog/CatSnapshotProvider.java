package ru.darkcat.camera.catlog;

import java.util.Map;

/** Supplies an allowlisted current app state for a problem marker. */
public interface CatSnapshotProvider {
    Map<String, ?> snapshot();
}
