"""Verify the ultra-economy mode remains reversible and text-focused."""
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"


def main():
    config = (JAVA / "org/icegram/messenger/IcegramConfig.java").read_text()
    assert 'getBoolean("ultra_economy_mode", false)' in config
    assert 'UltraEconomyController.setEnabled(enabled)' in config
    controller = (JAVA / "org/icegram/messenger/UltraEconomyController.java").read_text()
    assert 'optionMask = allOptions()' in controller
    assert 'snapshot.capture(main, defaults, "lite_mode6", "view_animations")' in controller
    assert 'snapshot.restore(main, true)' in controller
    assert 'ultra_economy_previous_view_animations' in controller

    lite_mode = (JAVA / "org/telegram/messenger/LiteMode.java").read_text()
    assert 'UltraEconomyController.isEnabled(UltraEconomyController.Option.TELEGRAM_POWER)' in lite_mode
    assert 'UltraEconomyController.filterLiteModeFlags' in lite_mode

    downloads = (JAVA / "org/telegram/messenger/DownloadController.java").read_text()
    for signature in ('public boolean canDownloadMedia(MessageObject messageObject)',
                      'public boolean canDownloadMedia(int type, long size)',
                      'public int canDownloadMediaType(MessageObject messageObject)',
                      'public int canDownloadMediaType(MessageObject messageObject, long overrideSize)',
                      'public int canDownloadMedia(TLRPC.Message message)',
                      'public int canDownloadMedia(TLRPC.Message message, TLRPC.MessageMedia media)',
                      'public int getAutodownloadMask()', 'protected int getAutodownloadMaskAll()',
                      'public int getCurrentDownloadMask()', 'protected boolean canDownloadNextTrack()',
                      'public boolean canPreloadStories()'):
        body = downloads.split(signature, 1)[1]
        assert body.lstrip().startswith('{\n        if (UltraEconomyController.isEnabled(UltraEconomyController.Option.NO_AUTODOWNLOAD))'), signature
    assert 'if (!power && !noDownloads)' in downloads
    assert 'java.util.Arrays.fill(preset.mask, 0)' in downloads
    refresh = (JAVA / "org/icegram/messenger/UltraEconomyRefreshRate.java").read_text()
    assert 'Math.min(60f, preference.rate)' in refresh
    assert 'limited ? 0 : preference.mode' in refresh
    assert 'WeakHashMap<View, Preference>' in refresh
    assert 'postDelayed' not in refresh
    theme = (JAVA / "org/telegram/ui/ActionBar/Theme.java").read_text()
    black_theme = theme.split('public static void applyUltraEconomyTheme', 1)[1].split('private static int applyThemeGeneration', 1)[0]
    assert 'applyTheme(black, false, false, false)' in black_theme
    assert 'snapshot.getString("ultraActive"' in black_theme
    assert 'snapshot.getInt("selectedAutoNightType"' in black_theme
    assert 'removeOverride' not in black_theme
    media = (JAVA / "org/telegram/messenger/MediaController.java").read_text()
    sensors = media.split('public void disableUltraEconomyRaiseSensors()', 1)[1].split('public void stopRaiseToEarSensors', 1)[0]
    assert 'stopRaiseToEarSensors(raiseChat, false, false)' in sensors
    assert 'ignoreOnPause = false' in sensors
    assert 'Option.NO_RAISE_SENSORS' in media.split('public void startRaiseToEarSensors', 1)[1].split('public void clearRaiseChat', 1)[0]
    stories = (JAVA / "org/icegram/messenger/UltraEconomyStories.java").read_text()
    assert 'TL_stories_getStoriesByID' in stories
    assert 'TL_stories_getPeerMaxIDs' in stories
    assert 'TL_messages_' not in stories
    assert 'phoneCall' not in stories
    connection = (JAVA / 'org/telegram/tgnet/ConnectionsManager.java').read_text()
    assert 'UltraEconomyStories.suppress(object)' in connection
    assert 'onComplete.run(null, economyError)' in connection

    navigation = (JAVA / "com/exteragram/messenger/config/BottomNavigationBar.kt").read_text()
    assert 'fun visible(): Boolean = !IcegramConfig.isUltraEconomyModeEnabled()' in navigation
    assert 'fun floating(): Boolean = !IcegramConfig.isUltraEconomyModeEnabled()' in navigation

    drawer = (JAVA / "com/exteragram/messenger/drawer/DrawerMenuView.java").read_text()
    assert 'MainMenuItem.ARCHIVE.getId(), MainMenuItem.SAVED.getId(), MainMenuItem.SETTINGS.getId()' in drawer
    assert 'IcegramConfig.isUltraEconomyModeEnabled()' in drawer

    settings = (JAVA / "com/exteragram/messenger/preferences/IcegramFeaturesPreferencesActivity.kt").read_text()
    assert 'presentFragment(UltraEconomyPreferencesActivity())' in settings
    options = (JAVA / "com/exteragram/messenger/preferences/UltraEconomyPreferencesActivity.kt").read_text()
    assert 'UltraEconomyController.Option.values()' in options
    assert 'UltraEconomyController.setSelected' in options

    chat = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()
    assert 'chatActivityEnterView.setUltraEconomyMode(IcegramConfig.isUltraEconomyModeEnabled())' in chat
    composer = (JAVA / "org/telegram/ui/Components/ChatActivityEnterView.java").read_text()
    assert 'public void setUltraEconomyMode(boolean enabled)' in composer
    assert 'ultraEconomyMode && visibility == VISIBLE ? GONE : visibility' in composer
    assert 'ultraEconomyMode ? View.GONE : View.VISIBLE' in composer
    assert 'ultraEconomyMode && contentType == POPUP_CONTENT_BOT_KEYBOARD' in composer
    send_container = composer.split('sendButtonContainer = new FrameLayout(context)', 1)[1].split('sendButton =', 1)[0]
    assert 'ultraEconomyMode && visibility' not in send_container
    mode_setter = composer.split('public void setUltraEconomyMode(boolean enabled)', 1)[1].split('private boolean isLiveComment', 1)[0]
    assert 'sendButtonContainer.setVisibility' not in mode_setter
    assert 'sendButton.setVisibility' not in mode_setter

    localized = []
    for resource in ("values/strings.xml", "values-ru/strings.xml"):
        strings = ET.parse(ROOT / "TMessagesProj/src/main/res" / resource).getroot()
        localized.append({item.get('name') for item in strings if (item.get('name') or '').startswith('IcegramUltra')})
    assert localized[0] == localized[1]
    for name in re.findall(r'R.string.(IcegramUltra\w+)', controller):
        assert name in localized[0], name
    fixtures = list((ROOT / "Tools/icegram/ultra-fixtures").rglob('*.java'))
    with tempfile.TemporaryDirectory() as output:
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', output, *map(str, fixtures),
                        str(JAVA / 'org/icegram/messenger/UltraEconomySnapshot.java'),
                        str(ROOT / 'Tools/icegram/UltraEconomySnapshotTest.java')], check=True)
        subprocess.run(['java', '-cp', output, 'UltraEconomySnapshotTest'], check=True)
    print("PASS: independent ultra-economy options, reversible settings, localization and send-button safety")


if __name__ == "__main__":
    main()
