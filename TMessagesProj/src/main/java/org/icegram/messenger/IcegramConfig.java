/* icegram additions. Licensed under GPL-3.0-or-later. */
package org.icegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/** No services, timers, network access or per-frame preference reads. */
public final class IcegramConfig {
    public static final String UPSTREAM_URL = "https://github.com/exteraSquad/exteraGram";
    private static volatile Boolean ghostModeEnabled;
    private static volatile Boolean messageHistoryEnabled;

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

    public static boolean isGhostModeEnabled() {
        Boolean enabled = ghostModeEnabled;
        if (enabled == null) {
            synchronized (IcegramConfig.class) {
                enabled = ghostModeEnabled;
                if (enabled == null) {
                    enabled = preferences().getBoolean("ayugram_ghost_mode", false);
                    ghostModeEnabled = enabled;
                }
            }
        }
        return enabled;
    }

    public static void setGhostModeEnabled(boolean enabled) {
        preferences().edit().putBoolean("ayugram_ghost_mode", enabled).apply();
        ghostModeEnabled = enabled;
    }

    public static boolean isMessageHistoryEnabled() {
        Boolean enabled = messageHistoryEnabled;
        if (enabled == null) {
            synchronized (IcegramConfig.class) {
                enabled = messageHistoryEnabled;
                if (enabled == null) {
                    enabled = preferences().getBoolean("ayugram_message_history", false);
                    messageHistoryEnabled = enabled;
                }
            }
        }
        return enabled;
    }

    public static void setMessageHistoryEnabled(boolean enabled) {
        preferences().edit().putBoolean("ayugram_message_history", enabled).apply();
        messageHistoryEnabled = enabled;
    }
}
