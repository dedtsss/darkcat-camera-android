package ru.darkcat.camera.point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Spatial + temporal clustering kept outside the capture critical path. */
public final class PointClusterer {
    public static final double DEFAULT_SPATIAL_RADIUS_METERS = 5.0d;
    public static final long DEFAULT_MAX_TEMPORAL_GAP_MILLIS = 120_000L;
    private static final long MIN_ADAPTIVE_GAP_MILLIS = 5_000L;

    private final double radiusMeters;
    private final long maxTemporalGapMillis;

    public PointClusterer() {
        this(DEFAULT_SPATIAL_RADIUS_METERS, DEFAULT_MAX_TEMPORAL_GAP_MILLIS);
    }

    public PointClusterer(double radiusMeters, long maxTemporalGapMillis) {
        if (!(radiusMeters > 0.0d) || Double.isInfinite(radiusMeters) || Double.isNaN(radiusMeters))
            throw new IllegalArgumentException("radiusMeters must be positive");
        if (maxTemporalGapMillis <= 0L) throw new IllegalArgumentException("maxTemporalGapMillis must be positive");
        this.radiusMeters = radiusMeters;
        this.maxTemporalGapMillis = maxTemporalGapMillis;
    }

    public List<ShootingPoint> cluster(List<PointMedia> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        ArrayList<PointMedia> sorted = new ArrayList<>(input);
        Collections.sort(sorted, new Comparator<PointMedia>() {
            @Override public int compare(PointMedia left, PointMedia right) {
                return Long.compare(left.timestampMillis, right.timestampMillis);
            }
        });
        ArrayList<List<PointMedia>> groups = new ArrayList<>();
        ArrayList<PointMedia> current = new ArrayList<>();
        ArrayList<Long> gaps = new ArrayList<>();
        PointMedia previous = null;
        for (PointMedia item : sorted) {
            if (current.isEmpty()) {
                current.add(item);
            } else {
                long gap = Math.max(0L, item.timestampMillis - previous.timestampMillis);
                long typical = median(gaps);
                long adaptiveLimit = typical <= 0L ? maxTemporalGapMillis
                        : Math.min(maxTemporalGapMillis, Math.max(MIN_ADAPTIVE_GAP_MILLIS, typical * 6L));
                boolean temporalBreak = gap > maxTemporalGapMillis || gap > adaptiveLimit;
                boolean spatialBreak = !withinRadius(item, current);
                if (temporalBreak || spatialBreak) {
                    groups.add(current);
                    current = new ArrayList<>();
                    gaps.clear();
                } else {
                    gaps.add(gap);
                }
                current.add(item);
            }
            previous = item;
        }
        if (!current.isEmpty()) groups.add(current);
        ArrayList<ShootingPoint> points = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) points.add(ShootingPoint.draft(i + 1, groups.get(i)));
        return points;
    }

    public static boolean canAutoRecluster(ShootingPoint point) {
        return point != null && point.lifecycle().allowsAutomaticReclustering();
    }

    private boolean withinRadius(PointMedia item, List<PointMedia> group) {
        if (!item.hasCoordinates()) return false;
        double lat = 0.0d, lon = 0.0d; int count = 0;
        for (PointMedia member : group) {
            if (member.latitude == null || member.longitude == null) continue;
            lat += member.latitude; lon += member.longitude; count++;
        }
        return count > 0 && distanceMeters(item.latitude, item.longitude, lat / count, lon / count) <= radiusMeters;
    }

    public static double distanceMeters(double latitude1, double longitude1,
                                        double latitude2, double longitude2) {
        double dLat = Math.toRadians(latitude2 - latitude1);
        double dLon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000.0d * 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0d, 1.0d - a)));
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) return 0L;
        ArrayList<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
