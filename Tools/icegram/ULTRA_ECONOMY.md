# Ultra economy implementation audit

The expansion reuses existing settings instead of adding parallel power-saving systems:

1. Apply Ice Mode and Animations and power saving already use `LiteMode`. All its flags, plus `view_animations`, are saved and restored. `DownloadController` already owns media preload presets.
2. `DownloadController` has separate mobile, Wi-Fi and roaming presets, as well as low, medium and high templates. Automatic download decisions have story, sponsored-media and voice-message exceptions that also need guards.
3. `AndroidUtilities.setPreferredMaxRefreshRate` owns window preferences. The adaptive `RefreshRateController` is not instantiated in this fork; no new frame-polling loop is needed.
4. `Monet Black` is the existing black theme, available on Android 12 and newer. Older devices must not be promised a nonexistent AMOLED theme.
5. `SharedConfig` already stores raise-to-listen and raise-to-record. `MediaController` owns their sensors, separately from VoIP sensors.
6. `ExteraConfig.hideStories` already hides the feed and stops some loads. Additional story network entry points and preloading need guards.
7. `LiteMode` already controls blur, glass, animated backgrounds and emoji effects. Extra wallpaper parallax, profile backgrounds and effect overlays need guards, not a second animation system.
8. `AutoProxyScheduler` owns one unique WorkManager task. The fork APK updater is already disabled in `UpdaterUtils`; keep it disabled. Full-flavor plugin SDK/analyzer checks and phonebook synchronization are separate from Telegram transport and push.
9. Notification avatars, large icons and `MessagingStyle.Message.setData` are decoration paths, separate from notification delivery and reply actions.
10. `ExteraConfig.inAppVibration`, `VibratorUtils` and per-account notification-controller chat sounds already exist. Do not change notification channels or call alerts.

## Settings icon diagnosis

`SettingsActivity` passes `ic_icegram_monochrome` to `SettingCell`. That resource is a valid 108dp launcher vector, not a missing resource. The cell places it in a 24dp `ImageView` using `CENTER_CROP`, cropping its filled center into a white block. Its normal white `SRC_IN` tint is appropriate for the shared colored tile. Use a dedicated 24dp snowflake for this row, leaving launcher assets and other rows unchanged.

## Verification boundaries

Source/policy tests and CI compilation/signature verification do not prove runtime behavior or battery savings. On a device, check sending with the keyboard and button, incoming messages and calls in background, notification text/replies, ten independent switches, restoration after restart and across accounts, story requests, sensor registration, 60Hz behavior, theme/wallpaper restoration, and the icon in light/dark themes. Measure battery use on the target device.
