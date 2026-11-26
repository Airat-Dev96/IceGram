/* icegram additions. Licensed under GPL-3.0-or-later. */
package org.icegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** No services, timers, network access or per-frame preference reads. */
public final class IcegramConfig {
    public static final String UPSTREAM_URL = "https://github.com/exteraSquad/exteraGram";
    public static final int DEFAULT_AUTO_PROXY_INTERVAL_HOURS = 3;
    public static final int DEFAULT_AUTO_PROXY_COUNT = 25;
    public static final int DEFAULT_AUTO_PROXY_MAX_PING = 800;
    private static volatile Boolean ghostModeEnabled;
    private static volatile Boolean hideOnline;
    private static volatile Boolean hideTyping;
    private static volatile Boolean hideReadReceipts;
    private static volatile Boolean hideStoryViews;
    private static volatile Boolean silentSend;
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

    public static boolean isOnlineHidden() {
        Boolean enabled = hideOnline;
        if (enabled == null) {
            synchronized (IcegramConfig.class) {
                enabled = hideOnline;
                if (enabled == null) {
                    enabled = preferences().getBoolean("icegram_hide_online", isGhostModeEnabled());
                    hideOnline = enabled;
                }
            }
        }
        return enabled;
    }

    public static boolean isTypingHidden() {
        Boolean enabled = hideTyping;
        if (enabled == null) {
            synchronized (IcegramConfig.class) {
                enabled = hideTyping;
                if (enabled == null) {
                    enabled = preferences().getBoolean("icegram_hide_typing", isGhostModeEnabled());
                    hideTyping = enabled;
                }
            }
        }
        return enabled;
    }

    public static boolean areReadReceiptsHidden() {
        Boolean enabled = hideReadReceipts;
        if (enabled == null) {
            synchronized (IcegramConfig.class) {
                enabled = hideReadReceipts;
                if (enabled == null) {
                    enabled = preferences().getBoolean("icegram_hide_read_receipts", isGhostModeEnabled());
                    hideReadReceipts = enabled;
                }
            }
        }
        return enabled;
    }

    public static void setOnlineHidden(boolean enabled) {
        preferences().edit().putBoolean("icegram_hide_online", enabled).apply();
        hideOnline = enabled;
    }

    public static void setTypingHidden(boolean enabled) {
        preferences().edit().putBoolean("icegram_hide_typing", enabled).apply();
        hideTyping = enabled;
    }

    public static void setReadReceiptsHidden(boolean enabled) {
        preferences().edit().putBoolean("icegram_hide_read_receipts", enabled).apply();
        hideReadReceipts = enabled;
    }

    public static boolean areStoryViewsHidden() {
        Boolean enabled = hideStoryViews;
        if (enabled == null) {
            synchronized (IcegramConfig.class) {
                enabled = hideStoryViews;
                if (enabled == null) {
                    hideStoryViews = enabled = preferences().getBoolean("icegram_hide_story_views", false);
                }
            }
        }
        return enabled;
    }

    public static void setStoryViewsHidden(boolean enabled) {
        preferences().edit().putBoolean("icegram_hide_story_views", enabled).apply();
        hideStoryViews = enabled;
    }

    public static boolean isSilentSendEnabled() {
        Boolean enabled = silentSend;
        if (enabled == null) {
            synchronized (IcegramConfig.class) {
                enabled = silentSend;
                if (enabled == null) {
                    silentSend = enabled = preferences().getBoolean("icegram_silent_send", false);
                }
            }
        }
        return enabled;
    }

    public static void setSilentSendEnabled(boolean enabled) {
        preferences().edit().putBoolean("icegram_silent_send", enabled).apply();
        silentSend = enabled;
    }

    public static void setGhostModeEnabled(boolean enabled) {
        preferences().edit().putBoolean("ayugram_ghost_mode", enabled).apply();
        ghostModeEnabled = enabled;
        setOnlineHidden(enabled);
        setTypingHidden(enabled);
        setReadReceiptsHidden(enabled);
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

    private static String historyExceptionsKey(int account) {
        return "history_excluded_dialogs_" + UserConfig.getInstance(account).getClientUserId();
    }

    public static Set<Long> getMessageHistoryExcludedDialogs(int account) {
        Set<Long> result = new HashSet<>();
        for (String value : preferences().getStringSet(historyExceptionsKey(account), Collections.emptySet())) {
            try {
                long dialogId = Long.parseLong(value);
                if (dialogId != 0) {
                    result.add(dialogId);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static boolean isMessageHistoryExcluded(int account, long dialogId) {
        return preferences().getStringSet(historyExceptionsKey(account), Collections.emptySet())
                .contains(Long.toString(dialogId));
    }

    public static synchronized void setMessageHistoryExcluded(int account, long dialogId, boolean excluded) {
        if (dialogId == 0) {
            return;
        }
        String key = historyExceptionsKey(account);
        Set<String> values = new HashSet<>(preferences().getStringSet(key, Collections.emptySet()));
        if (excluded) {
            values.add(Long.toString(dialogId));
        } else {
            values.remove(Long.toString(dialogId));
        }
        preferences().edit().putStringSet(key, values).apply();
    }

    public static boolean isAutoProxyEnabled() {
        return preferences().getBoolean("auto_proxy_enabled", false);
    }

    public static void setAutoProxyEnabled(boolean enabled) {
        preferences().edit().putBoolean("auto_proxy_enabled", enabled).apply();
    }

    public static int getAutoProxyIntervalHours() {
        int value = preferences().getInt("auto_proxy_interval_hours", DEFAULT_AUTO_PROXY_INTERVAL_HOURS);
        return value == 1 || value == 3 || value == 5 || value == 12 || value == 24
                ? value : DEFAULT_AUTO_PROXY_INTERVAL_HOURS;
    }

    public static void setAutoProxyIntervalHours(int hours) {
        if (hours == 1 || hours == 3 || hours == 5 || hours == 12 || hours == 24) {
            preferences().edit().putInt("auto_proxy_interval_hours", hours).apply();
        }
    }

    public static int getAutoProxyCount() {
        int value = preferences().getInt("auto_proxy_count", DEFAULT_AUTO_PROXY_COUNT);
        return value == 10 || value == 25 || value == 50 || value == 100
                ? value : DEFAULT_AUTO_PROXY_COUNT;
    }

    public static void setAutoProxyCount(int count) {
        if (count == 10 || count == 25 || count == 50 || count == 100) {
            preferences().edit().putInt("auto_proxy_count", count).apply();
        }
    }

    public static boolean isAutoProxyRemoveDeadEnabled() {
        return preferences().getBoolean("auto_proxy_remove_dead", true);
    }

    public static void setAutoProxyRemoveDeadEnabled(boolean enabled) {
        preferences().edit().putBoolean("auto_proxy_remove_dead", enabled).apply();
    }

    public static boolean isAutoProxyAutoSwitchEnabled() {
        return preferences().getBoolean("auto_proxy_auto_switch", false);
    }

    public static void setAutoProxyAutoSwitchEnabled(boolean enabled) {
        preferences().edit().putBoolean("auto_proxy_auto_switch", enabled).apply();
    }

    public static int getAutoProxyMaxPing() {
        int value = preferences().getInt("auto_proxy_max_ping", DEFAULT_AUTO_PROXY_MAX_PING);
        return value == 300 || value == 500 || value == 800 || value == 1200 || value == 2000
                ? value : DEFAULT_AUTO_PROXY_MAX_PING;
    }

    public static void setAutoProxyMaxPing(int ping) {
        if (ping == 300 || ping == 500 || ping == 800 || ping == 1200 || ping == 2000) {
            preferences().edit().putInt("auto_proxy_max_ping", ping).apply();
        }
    }

    public static boolean isHideProxySponsorEnabled() {
        return preferences().getBoolean("hide_proxy_sponsor", true);
    }

    public static void setHideProxySponsorEnabled(boolean enabled) {
        preferences().edit().putBoolean("hide_proxy_sponsor", enabled).apply();
    }
}
