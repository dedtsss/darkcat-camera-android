package ru.darkcat.camera.gallery;

/** Android-free ordering contract for the unified Vault/MediaStore timeline. */
public final class GalleryTimelineOrder {
    public static int newestFirst(long leftCreatedAt, String leftId, long rightCreatedAt, String rightId) {
        int date = Long.compare(rightCreatedAt, leftCreatedAt);
        if (date != 0) return date;
        String left = leftId == null ? "" : leftId;
        String right = rightId == null ? "" : rightId;
        return left.compareTo(right);
    }

    private GalleryTimelineOrder() { }
}
