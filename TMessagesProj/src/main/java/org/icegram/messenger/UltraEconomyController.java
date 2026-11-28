package org.icegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;

public final class UltraEconomyController {
    public enum Option {
        TELEGRAM_POWER(1, R.string.IcegramUltraNativePower, R.string.IcegramUltraNativePowerInfo),
        NO_AUTODOWNLOAD(1 << 1, R.string.IcegramUltraAutodownload, R.string.IcegramUltraAutodownloadInfo),
        REFRESH_RATE(1 << 2, R.string.IcegramUltraRefreshRate, R.string.IcegramUltraRefreshRateInfo);

        public final int flag;
        public final int title;
        public final int description;

        Option(int flag, int title, int description) {
            this.flag = flag;
            this.title = title;
            this.description = description;
        }
    }

    private static volatile int optionMask = -1;

    private UltraEconomyController() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("icegram", Context.MODE_PRIVATE);
    }

    public static boolean isSelected(Option option) {
        if (optionMask == -1) {
            optionMask = preferences().getInt("ultra_economy_options", allOptions());
        }
        return (optionMask & option.flag) != 0;
    }

    public static boolean isEnabled(Option option) {
        return IcegramConfig.isUltraEconomyModeEnabled() && isSelected(option);
    }

    public static synchronized void setEnabled(boolean enabled) {
        if (enabled == IcegramConfig.isUltraEconomyModeEnabled()) {
            return;
        }
        if (enabled) {
            optionMask = allOptions();
            preferences().edit().putInt("ultra_economy_options", optionMask).commit();
        }
        IcegramConfig.storeUltraEconomyModeEnabled(enabled);
        applySettings();
    }

    public static synchronized void setSelected(Option option, boolean selected) {
        isSelected(option);
        optionMask = selected ? optionMask | option.flag : optionMask & ~option.flag;
        preferences().edit().putInt("ultra_economy_options", optionMask).commit();
        applySettings();
    }

    public static synchronized void initialize() {
        SharedPreferences preferences = preferences();
        if (preferences.contains("ultra_economy_previous_view_animations")) {
            MessagesController.getGlobalMainSettings().edit().putBoolean("view_animations",
                    preferences.getBoolean("ultra_economy_previous_view_animations", true)).commit();
            preferences.edit().remove("ultra_economy_previous_view_animations").commit();
        }
        applySettings();
    }

    public static int filterLiteModeFlags(int flags) {
        return isEnabled(Option.TELEGRAM_POWER) ? 0 : flags;
    }

    private static int allOptions() {
        int flags = 0;
        for (Option option : Option.values()) {
            flags |= option.flag;
        }
        return flags;
    }

    private static void applySettings() {
        applyNativePower();
        AndroidUtilities.runOnUIThread(UltraEconomyRefreshRate::refresh);
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                DownloadController.getInstance(account).checkAutodownloadSettings();
            }
        }
    }

    private static void applyNativePower() {
        SharedPreferences main = MessagesController.getGlobalMainSettings();
        UltraEconomySnapshot snapshot = new UltraEconomySnapshot("native_power");
        if (isEnabled(Option.TELEGRAM_POWER)) {
            HashMap<String, Object> defaults = new HashMap<>();
            defaults.put("lite_mode6", LiteMode.getValue(true));
            defaults.put("view_animations", main.getBoolean("view_animations", true));
            if (!snapshot.capture(main, defaults, "lite_mode6", "view_animations")) {
                return;
            }
            main.edit().putBoolean("view_animations", false).commit();
            LiteMode.setAllFlags(filterLiteModeFlags(snapshot.getInt("lite_mode6", 0)));
        } else if (snapshot.isSaved() && snapshot.restore(main, true)) {
            LiteMode.loadPreference();
        }
    }
}
