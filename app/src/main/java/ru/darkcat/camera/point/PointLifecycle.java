package ru.darkcat.camera.point;

public enum PointLifecycle {
    DRAFT,
    REVIEWED,
    UPLOADING,
    PUBLISHED,
    LOCKED;

    public boolean allowsAutomaticReclustering() {
        return this == DRAFT || this == REVIEWED;
    }

    public boolean isPublishedOrLocked() {
        return this == PUBLISHED || this == LOCKED;
    }

    public boolean canTransitionTo(PointLifecycle next) {
        if (next == null) return false;
        if (this == LOCKED) return next == LOCKED;
        if (this == PUBLISHED) return next == PUBLISHED || next == LOCKED;
        if (this == UPLOADING) return next == UPLOADING || next == PUBLISHED || next == REVIEWED;
        if (this == REVIEWED) return next == REVIEWED || next == UPLOADING || next == DRAFT;
        return next == DRAFT || next == REVIEWED;
    }
}
