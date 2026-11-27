package org.icegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class UltraEconomySnapshot {
    private final SharedPreferences backup;

    public UltraEconomySnapshot(String group) {
        backup = ApplicationLoader.applicationContext.getSharedPreferences("icegram_ultra_backup_" + group, Context.MODE_PRIVATE);
    }

    public boolean isSaved() {
        return backup.getBoolean("saved", false);
    }

    public boolean capture(SharedPreferences source, String... keys) {
        return capture(source, Collections.emptyMap(), keys);
    }

    public boolean capture(SharedPreferences source, Map<String, ?> defaults, String... keys) {
        if (isSaved()) {
            return true;
        }
        Map<String, ?> values = source.getAll();
        SharedPreferences.Editor editor = backup.edit().clear();
        Set<String> savedKeys = new HashSet<>();
        for (String key : keys) {
            savedKeys.add(key);
            editor.putBoolean("present:" + key, values.containsKey(key));
            put(editor, "value:" + key, values.containsKey(key) ? values.get(key) : defaults.get(key));
        }
        return editor.putStringSet("keys", savedKeys).putBoolean("saved", true).commit();
    }

    public boolean restore(SharedPreferences target, boolean release) {
        if (!isSaved()) {
            return true;
        }
        Map<String, ?> values = backup.getAll();
        SharedPreferences.Editor editor = target.edit();
        for (String key : backup.getStringSet("keys", new HashSet<>())) {
            put(editor, key, backup.getBoolean("present:" + key, false) ? values.get("value:" + key) : null);
        }
        if (!editor.commit()) {
            return false;
        }
        return !release || backup.edit().clear().commit();
    }

    public int getInt(String key, int defaultValue) {
        return backup.getInt("value:" + key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return backup.getBoolean("value:" + key, defaultValue);
    }

    public String getString(String key, String defaultValue) {
        return backup.getString("value:" + key, defaultValue);
    }

    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Set) {
            Set<String> strings = new HashSet<>();
            for (Object entry : (Set<?>) value) {
                strings.add((String) entry);
            }
            editor.putStringSet(key, strings);
        } else {
            editor.remove(key);
        }
    }
}
