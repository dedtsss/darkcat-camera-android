package ru.darkcat.camera.point;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Stable point block with an explicit lifecycle and immutable capture facts. */
public final class ShootingPoint {
    private final UUID pointUuid;
    private final int displayNumber;
    private final List<PointMedia> media;
    private final PointLifecycle lifecycle;
    private final String pointShareUrl;
    private final List<String> mediaShareUrls;

    public ShootingPoint(UUID pointUuid, int displayNumber, List<PointMedia> media,
                         PointLifecycle lifecycle, String pointShareUrl, List<String> mediaShareUrls) {
        if (displayNumber <= 0) throw new IllegalArgumentException("displayNumber must be positive");
        this.pointUuid = pointUuid == null ? UUID.randomUUID() : pointUuid;
        this.displayNumber = displayNumber;
        this.media = Collections.unmodifiableList(new ArrayList<>(media == null
                ? Collections.emptyList() : media));
        if (this.media.isEmpty()) throw new IllegalArgumentException("point must contain media");
        this.lifecycle = lifecycle == null ? PointLifecycle.DRAFT : lifecycle;
        this.pointShareUrl = emptyToNull(pointShareUrl);
        this.mediaShareUrls = Collections.unmodifiableList(new ArrayList<>(mediaShareUrls == null
                ? Collections.emptyList() : mediaShareUrls));
    }

    public static ShootingPoint draft(int displayNumber, List<PointMedia> media) {
        StringBuilder ids = new StringBuilder();
        for (PointMedia item : media) ids.append(item.mediaId).append('\n');
        UUID stable = UUID.nameUUIDFromBytes(ids.toString().getBytes(StandardCharsets.UTF_8));
        return new ShootingPoint(stable, displayNumber, media, PointLifecycle.DRAFT, null, null);
    }

    public UUID pointUuid() { return pointUuid; }
    public int displayNumber() { return displayNumber; }
    public List<PointMedia> media() { return media; }
    public PointLifecycle lifecycle() { return lifecycle; }
    public String pointShareUrl() { return pointShareUrl; }
    public List<String> mediaShareUrls() { return mediaShareUrls; }

    public double centerLatitude() {
        double total = 0; int count = 0;
        for (PointMedia item : media) if (item.latitude != null) { total += item.latitude; count++; }
        return count == 0 ? Double.NaN : total / count;
    }

    public double centerLongitude() {
        double total = 0; int count = 0;
        for (PointMedia item : media) if (item.longitude != null) { total += item.longitude; count++; }
        return count == 0 ? Double.NaN : total / count;
    }

    public long firstTimestampMillis() { return media.get(0).timestampMillis; }
    public long lastTimestampMillis() { return media.get(media.size() - 1).timestampMillis; }

    public ShootingPoint withLifecycle(PointLifecycle next) {
        if (next == null) throw new NullPointerException("next");
        if (!lifecycle.canTransitionTo(next))
            throw new IllegalStateException("invalid point lifecycle transition: " + lifecycle + " -> " + next);
        return new ShootingPoint(pointUuid, displayNumber, media, next, pointShareUrl, mediaShareUrls);
    }

    public ShootingPoint withShareUrls(String commonUrl, List<String> itemUrls) {
        return new ShootingPoint(pointUuid, displayNumber, media, lifecycle, commonUrl, itemUrls);
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
