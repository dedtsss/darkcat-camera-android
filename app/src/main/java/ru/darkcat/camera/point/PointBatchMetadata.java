package ru.darkcat.camera.point;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** CRM-ready batch contract; individual media URLs remain available for providers without sessions. */
public final class PointBatchMetadata {
    public final ShootingPoint point;
    public final List<String> tags;

    public PointBatchMetadata(ShootingPoint point, List<String> tags) {
        if (point == null) throw new NullPointerException("point");
        this.point = point;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("pointUuid", point.pointUuid().toString());
            object.put("displayPointNumber", point.displayNumber());
            object.put("lifecycle", point.lifecycle().name());
            object.put("centerLatitude", point.centerLatitude());
            object.put("centerLongitude", point.centerLongitude());
            object.put("firstTimestamp", point.firstTimestampMillis());
            object.put("lastTimestamp", point.lastTimestampMillis());
            object.put("mediaIds", mediaIds());
            object.put("pointShareUrl", point.pointShareUrl() == null ? JSONObject.NULL : point.pointShareUrl());
            object.put("mediaShareUrls", new JSONArray(point.mediaShareUrls()));
            object.put("tags", new JSONArray(tags));
        } catch (JSONException ignored) { }
        return object;
    }

    private JSONArray mediaIds() {
        JSONArray values = new JSONArray();
        for (PointMedia media : point.media()) values.put(media.mediaId);
        return values;
    }
}
