package android.content;

import java.util.Map;
import java.util.Set;

public interface SharedPreferences {
    Map<String, ?> getAll();
    boolean getBoolean(String key, boolean defaultValue);
    int getInt(String key, int defaultValue);
    String getString(String key, String defaultValue);
    Set<String> getStringSet(String key, Set<String> defaultValue);
    Editor edit();

    interface Editor {
        Editor clear();
        Editor remove(String key);
        Editor putBoolean(String key, boolean value);
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor putFloat(String key, float value);
        Editor putString(String key, String value);
        Editor putStringSet(String key, Set<String> value);
        boolean commit();
    }
}
