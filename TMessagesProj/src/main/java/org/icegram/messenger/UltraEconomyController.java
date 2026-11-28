package org.icegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.WallpaperParallaxEffect;
import org.telegram.ui.Components.Reactions.ReactionsEffectOverlay;

import java.util.HashMap;

public final class UltraEconomyController {
    public enum Option {
        TELEGRAM_POWER(1, R.string.IcegramUltraNativePower, R.string.IcegramUltraNativePowerInfo),
        NO_AUTODOWNLOAD(1 << 1, R.string.IcegramUltraAutodownload, R.string.IcegramUltraAutodownloadInfo),
        REFRESH_RATE(1 << 2, R.string.IcegramUltraRefreshRate, R.string.IcegramUltraRefreshRateInfo),
        BLACK_THEME(1 << 3, R.string.IcegramUltraBlackTheme, R.string.IcegramUltraBlackThemeInfo),
        NO_RAISE_SENSORS(1 << 4, R.string.IcegramUltraSensors, R.string.IcegramUltraSensorsInfo),
        NO_STORIES(1 << 5, R.string.IcegramUltraStories, R.string.IcegramUltraStoriesInfo),
        NO_EFFECTS(1 << 6, R.string.IcegramUltraEffects, R.string.IcegramUltraEffectsInfo);

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
        if (isEnabled(Option.TELEGRAM_POWER)) {
            return 0;
        }
        if (isEnabled(Option.NO_EFFECTS)) {
            return flags & ~(LiteMode.FLAG_CHAT_BACKGROUND | LiteMode.FLAG_CHAT_BLUR | LiteMode.FLAG_CHAT_SCALE
                    | LiteMode.FLAG_CHAT_THANOS | LiteMode.FLAG_LIQUID_GLASS | LiteMode.FLAG_PARTICLES
                    | LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS);
        }
        return flags;
    }

    public static boolean areEffectsDisabled() {
        return isEnabled(Option.TELEGRAM_POWER) || isEnabled(Option.NO_EFFECTS);
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
        applyRaiseSensors();
        applyStories();
        WallpaperParallaxEffect.refreshUltraEconomy();
        if (areEffectsDisabled()) {
            ReactionsEffectOverlay.removeCurrent(true);
        }
        AndroidUtilities.runOnUIThread(UltraEconomyRefreshRate::refresh);
        AndroidUtilities.runOnUIThread(() -> Theme.applyUltraEconomyTheme(isEnabled(Option.BLACK_THEME)));
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                DownloadController.getInstance(account).checkAutodownloadSettings();
            }
        }
    }

    private static void applyNativePower() {
        SharedPreferences main = MessagesController.getGlobalMainSettings();
        UltraEconomySnapshot snapshot = new UltraEconomySnapshot("native_power");
        if (areEffectsDisabled()) {
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

    private static void applyRaiseSensors() {
        SharedPreferences main = MessagesController.getGlobalMainSettings();
        UltraEconomySnapshot snapshot = new UltraEconomySnapshot("raise_sensors");
        if (isEnabled(Option.NO_RAISE_SENSORS)) {
            if (!snapshot.capture(main, "raise_to_listen", "raise_to_speak")) {
                return;
            }
            main.edit().putBoolean("raise_to_listen", false).putBoolean("raise_to_speak", false).commit();
            SharedConfig.raiseToListen = false;
            SharedConfig.raiseToSpeak = false;
            MediaController.getInstance().disableUltraEconomyRaiseSensors();
        } else if (snapshot.isSaved() && snapshot.restore(main, true)) {
            SharedConfig.raiseToListen = main.getBoolean("raise_to_listen", true);
            SharedConfig.raiseToSpeak = main.getBoolean("raise_to_speak", false);
        }
    }

    private static void applyStories() {
        UltraEconomySnapshot snapshot = new UltraEconomySnapshot("stories");
        boolean enabled = isEnabled(Option.NO_STORIES);
        if (enabled) {
            if (!snapshot.capture(ExteraConfig.getPreferences(), "hideStories")) {
                return;
            }
            ExteraConfig.setHideStories(true);
        } else if (snapshot.isSaved()) {
            ExteraConfig.setHideStories(snapshot.getBoolean("hideStories", false));
            snapshot.restore(ExteraConfig.getPreferences(), true);
        } else {
            return;
        }
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                MessagesController controller = MessagesController.getInstance(account);
                controller.getStoriesController().stopAllPollers();
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.storiesUpdated);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.storiesEnabledUpdate);
                if (!enabled && !ExteraConfig.getHideStories()) {
                    controller.getStoriesController().loadStories();
                }
            }
        }
    }
}
