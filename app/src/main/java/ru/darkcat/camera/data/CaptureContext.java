package ru.darkcat.camera.data;

import android.content.Intent;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Optional context supplied by DarkCat CRM. The camera remains fully usable without it. */
public final class CaptureContext {
    public static final String ACTION_DARKCAT_CAPTURE = "ru.darkcat.camera.action.CAPTURE";
    public static final String EXTRA_CONTEXT_JSON = "ru.darkcat.camera.extra.CAPTURE_CONTEXT";
    public static final String EXTRA_CRM_OBJECT_ID = "ru.darkcat.camera.extra.CRM_OBJECT_ID";
    public static final String EXTRA_INSPECTION_ID = "ru.darkcat.camera.extra.INSPECTION_ID";
    public static final String EXTRA_TASK_ID = "ru.darkcat.camera.extra.TASK_ID";
    public static final String EXTRA_USER_ID = "ru.darkcat.camera.extra.USER_ID";
    public static final String EXTRA_CUSTOM_TAGS = "ru.darkcat.camera.extra.CUSTOM_TAGS";

    public final String crmObjectId;
    public final String inspectionId;
    public final String taskId;
    public final String userId;
    public final List<String> customTags;

    public CaptureContext(String crmObjectId, String inspectionId, String taskId,
                          String userId, List<String> customTags) {
        this.crmObjectId = emptyToNull(crmObjectId);
        this.inspectionId = emptyToNull(inspectionId);
        this.taskId = emptyToNull(taskId);
        this.userId = emptyToNull(userId);
        this.customTags = customTags == null ? new ArrayList<>() : new ArrayList<>(customTags);
    }

    public boolean isEmpty() {
        return crmObjectId == null && inspectionId == null && taskId == null && userId == null && customTags.isEmpty();
    }

    public static CaptureContext fromIntent(Intent intent) {
        if (intent == null) return empty();
        Bundle extras = intent.getExtras();
        if (extras == null) return empty();
        String json = extras.getString(EXTRA_CONTEXT_JSON);
        if (json != null) {
            try {
                JSONObject object = new JSONObject(json);
                return fromJson(object);
            } catch (JSONException ignored) {
                // Fall back to individual extras so a malformed optional payload never blocks capture.
            }
        }
        ArrayList<String> tags = extras.getStringArrayList(EXTRA_CUSTOM_TAGS);
        return new CaptureContext(extras.getString(EXTRA_CRM_OBJECT_ID), extras.getString(EXTRA_INSPECTION_ID),
                extras.getString(EXTRA_TASK_ID), extras.getString(EXTRA_USER_ID), tags);
    }

    public static CaptureContext fromJson(JSONObject object) {
        ArrayList<String> tags = new ArrayList<>();
        JSONArray array = object.optJSONArray("customTags");
        if (array != null) for (int i = 0; i < array.length(); i++) tags.add(array.optString(i));
        return new CaptureContext(object.optString("crmObjectId", null), object.optString("inspectionId", null),
                object.optString("taskId", null), object.optString("userId", null), tags);
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            if (crmObjectId != null) object.put("crmObjectId", crmObjectId);
            if (inspectionId != null) object.put("inspectionId", inspectionId);
            if (taskId != null) object.put("taskId", taskId);
            if (userId != null) object.put("userId", userId);
            object.put("customTags", new JSONArray(customTags));
        } catch (JSONException ignored) { }
        return object;
    }

    public static CaptureContext empty() { return new CaptureContext(null, null, null, null, null); }

    private static String emptyToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
