package ru.darkcat.camera.point;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit user operations for draft/reviewed points. Published points are never silently changed. */
public final class PointOperations {
    public static ShootingPoint mergeAdjacent(ShootingPoint first, ShootingPoint second) {
        requireMutable(first); requireMutable(second);
        ArrayList<PointMedia> media = new ArrayList<>(first.media());
        media.addAll(second.media());
        media.sort((a, b) -> Long.compare(a.timestampMillis, b.timestampMillis));
        return new ShootingPoint(first.pointUuid(), first.displayNumber(), media, PointLifecycle.DRAFT,
                null, null);
    }

    public static List<ShootingPoint> splitHere(ShootingPoint point, int index) {
        requireMutable(point);
        if (index <= 0 || index >= point.media().size()) throw new IllegalArgumentException("split index out of range");
        List<PointMedia> left = new ArrayList<>(point.media().subList(0, index));
        List<PointMedia> right = new ArrayList<>(point.media().subList(index, point.media().size()));
        ArrayList<ShootingPoint> result = new ArrayList<>();
        result.add(new ShootingPoint(point.pointUuid(), point.displayNumber(), left, PointLifecycle.DRAFT, null, null));
        String ids = right.get(0).mediaId + right.get(right.size() - 1).mediaId;
        UUID rightUuid = UUID.nameUUIDFromBytes(ids.getBytes(StandardCharsets.UTF_8));
        result.add(new ShootingPoint(rightUuid, point.displayNumber() + 1, right, PointLifecycle.DRAFT, null, null));
        return result;
    }

    private static void requireMutable(ShootingPoint point) {
        if (point == null || !point.lifecycle().allowsAutomaticReclustering())
            throw new IllegalStateException("published/locked point requires an explicit correction workflow");
    }

    private PointOperations() { }
}
