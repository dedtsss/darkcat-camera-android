package ru.darkcat.camera.tags;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public final class TagRepository {
    private static final String KEY_ALL = "darkcat_tags_all_v1";
    private static final String KEY_ACTIVE = "darkcat_tags_active_v1";
    private final SharedPreferences preferences;

    public TagRepository(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public synchronized List<String> all() { return new ArrayList<>(TagCodec.decode(preferences.getString(KEY_ALL, ""))); }
    public synchronized List<String> active() { return new ArrayList<>(TagCodec.decode(preferences.getString(KEY_ACTIVE, ""))); }

    public synchronized boolean add(String raw) {
        String tag = TagCodec.normalize(raw);
        if (tag == null) return false;
        List<String> tags = all();
        if (tags.contains(tag)) return false;
        tags.add(tag);
        preferences.edit().putString(KEY_ALL, TagCodec.encode(tags)).apply();
        return true;
    }

    public synchronized void remove(String raw) {
        String tag = TagCodec.normalize(raw);
        List<String> tags = all();
        List<String> active = active();
        tags.remove(tag);
        active.remove(tag);
        preferences.edit().putString(KEY_ALL, TagCodec.encode(tags)).putString(KEY_ACTIVE, TagCodec.encode(active)).apply();
    }

    public synchronized boolean toggle(String raw) {
        String tag = TagCodec.normalize(raw);
        if (tag == null || !all().contains(tag)) return false;
        List<String> active = active();
        boolean enabled;
        if (active.remove(tag)) enabled = false;
        else { active.add(tag); enabled = true; }
        preferences.edit().putString(KEY_ACTIVE, TagCodec.encode(active)).apply();
        return enabled;
    }

    public synchronized boolean isActive(String tag) { return active().contains(TagCodec.normalize(tag)); }
    public synchronized void clearActive() { preferences.edit().putString(KEY_ACTIVE, "").apply(); }
}
