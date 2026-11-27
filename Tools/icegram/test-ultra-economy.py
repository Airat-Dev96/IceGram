"""Verify the ultra-economy mode remains reversible and text-focused."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"


def main():
    config = (JAVA / "org/icegram/messenger/IcegramConfig.java").read_text()
    assert 'getBoolean("ultra_economy_mode", false)' in config
    assert 'putBoolean("ultra_economy_previous_view_animations", globalPreferences.getBoolean("view_animations", true))' in config
    assert 'remove("ultra_economy_previous_view_animations")' in config
    assert 'putBoolean("view_animations", previousAnimations)' in config

    lite_mode = (JAVA / "org/telegram/messenger/LiteMode.java").read_text()
    assert 'if (!ignorePowerSaving && IcegramConfig.isUltraEconomyModeEnabled())' in lite_mode
    assert 'if (IcegramConfig.isUltraEconomyModeEnabled()) {\n            return false;' in lite_mode

    navigation = (JAVA / "com/exteragram/messenger/config/BottomNavigationBar.kt").read_text()
    assert 'fun visible(): Boolean = !IcegramConfig.isUltraEconomyModeEnabled()' in navigation
    assert 'fun floating(): Boolean = !IcegramConfig.isUltraEconomyModeEnabled()' in navigation

    drawer = (JAVA / "com/exteragram/messenger/drawer/DrawerMenuView.java").read_text()
    assert 'MainMenuItem.ARCHIVE.getId(), MainMenuItem.SAVED.getId(), MainMenuItem.SETTINGS.getId()' in drawer
    assert 'IcegramConfig.isUltraEconomyModeEnabled()' in drawer

    settings = (JAVA / "com/exteragram/messenger/preferences/IcegramFeaturesPreferencesActivity.kt").read_text()
    assert 'IcegramConfig.setUltraEconomyModeEnabled(it)' in settings
    assert 'IcegramConfig.isUltraEconomyModeEnabled()' in settings

    chat = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()
    assert 'chatActivityEnterView.setUltraEconomyMode(IcegramConfig.isUltraEconomyModeEnabled())' in chat
    composer = (JAVA / "org/telegram/ui/Components/ChatActivityEnterView.java").read_text()
    assert 'public void setUltraEconomyMode(boolean enabled)' in composer
    assert 'ultraEconomyMode && visibility == VISIBLE ? GONE : visibility' in composer
    assert 'ultraEconomyMode ? View.GONE : View.VISIBLE' in composer
    assert 'ultraEconomyMode && contentType == POPUP_CONTENT_BOT_KEYBOARD' in composer

    for resource in ("values/icegram.xml", "values-ru/icegram.xml"):
        strings = (ROOT / "TMessagesProj/src/main/res" / resource).read_text()
        assert 'name="IcegramUltraEconomy"' in strings
        assert 'name="IcegramUltraEconomyInfo"' in strings
    print("PASS: ultra-economy defaults off, restores animations, and keeps chat/settings access")


if __name__ == "__main__":
    main()
