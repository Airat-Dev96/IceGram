/* icegram additions. Licensed under GPL-3.0-or-later. */
package org.icegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/** No services, timers, network access or per-frame preference reads. */
public final class IcegramConfig {
    public static final String UPSTREAM_URL = "https://github.com/exteraSquad/exteraGram";

    private IcegramConfig() {
    }

    public static boolean areMapsEnabled() {
        return false;
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("icegram", Context.MODE_PRIVATE);
    }

    public static boolean isCompactCacheEnabled() {
        return preferences().getBoolean("compact_image_cache", true);
    }

    public static void setCompactCacheEnabled(boolean enabled) {
        preferences().edit().putBoolean("compact_image_cache", enabled).apply();
    }

    public static boolean isLazyWidgetsEnabled() {
        return preferences().getBoolean("lazy_widgets", true);
    }

    public static void setLazyWidgetsEnabled(boolean enabled) {
        preferences().edit().putBoolean("lazy_widgets", enabled).apply();
    }
}
