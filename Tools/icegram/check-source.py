"""Check icegram integration without Android or private keys. Python 3.11 stdlib.

These are structural regression checks, not a Java/Kotlin compiler or runtime test.
Private configuration values are never printed.
"""
import json
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "TMessagesProj/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "org.icegram.messenger"
ERRORS = []
DOCUMENTS = {}
SYMBOLS = set()
CHECKS = 0


def check(condition, message):
    global CHECKS
    CHECKS += 1
    if not condition:
        ERRORS.append(message)


def text(path):
    return path.read_text(encoding="utf-8")


def xml(path):
    if path not in DOCUMENTS:
        try:
            DOCUMENTS[path] = ET.parse(path).getroot()
        except ET.ParseError as error:
            check(False, f"Invalid XML: {path.relative_to(ROOT)} at {error.position}")
            DOCUMENTS[path] = ET.Element("invalid")
    return DOCUMENTS[path]


def resources():
    # Raw files are indexed but not interpreted as Android XML resources.
    for folder in ("res", "res-appicons", "res-solar", "res-remix", "res-tabs"):
        for directory in sorted((MAIN / folder).iterdir()):
            if not directory.is_dir():
                continue
            kind = directory.name.split("-", 1)[0]
            for path in sorted(directory.iterdir()):
                if not path.is_file():
                    continue
                if kind == "values" and path.suffix == ".xml":
                    document = xml(path)
                    for element in document:
                        name = element.get("name")
                        if name:
                            SYMBOLS.add((element.get("type", element.tag), name))
                            value = "".join(element.itertext()).strip()
                            if element.tag == "string" and name in ("AppName", "exteraAppName"):
                                check(value == "icegram", f"Old app name in {path.relative_to(ROOT)}")
                            if element.tag == "string" and name == "Preferences":
                                check("icegram" in value, f"Old settings name in {path.relative_to(ROOT)}")
                else:
                    SYMBOLS.add((kind, path.name.split(".", 1)[0]))
                    if kind != "raw" and path.suffix == ".xml":
                        xml(path)

    en = xml(MAIN / "res/values/icegram.xml")
    ru = xml(MAIN / "res/values-ru/icegram.xml")
    english = {item.get("name"): item for item in en if item.tag == "string"}
    russian = {item.get("name"): item for item in ru if item.tag == "string"}
    required_ru = {name for name, item in english.items() if item.get("translatable") != "false"}
    check(required_ru == set(russian), "English/Russian icegram string keys differ")
    for path, document in ((MAIN / "res/values/icegram.xml", en), (MAIN / "res/values-ru/icegram.xml", ru)):
        names = [item.get("name") for item in document if item.tag == "string"]
        check(len(names) == len(set(names)), f"Duplicate strings in {path.relative_to(ROOT)}")

    # Verify references in the newly added Android sources against real resources.
    additions = list((MAIN / "java/org/icegram/messenger").glob("*.java"))
    additions.extend((MAIN / "java/com/exteragram/messenger/preferences/IcegramFeaturesPreferencesActivity.kt",
                      MAIN / "java/com/exteragram/messenger/preferences/MainPreferencesActivity.kt",
                      MAIN / "java/com/exteragram/messenger/preferences/ExteraGramPreferencesActivity.kt",
                      MAIN / "java/com/exteragram/messenger/preferences/AyugramPreferencesActivity.kt",
                      MAIN / "java/com/exteragram/messenger/preferences/MessageHistoryActivity.kt",
                      MAIN / "java/com/exteragram/messenger/preferences/MessageHistoryExceptionsActivity.kt"))
    for path in additions:
        for kind, name in re.findall(r"\bR\.(string|drawable|mipmap|color)\.(\w+)", text(path)):
            check((kind, name) in SYMBOLS, f"Missing R.{kind}.{name} used by {path.name}")


def branding():
    properties = text(ROOT / "gradle.properties")
    check(re.search(r"^APP_PACKAGE=org\.icegram\.messenger\s*$", properties, re.M), "Wrong application id")
    spec = json.loads(text(ROOT / "TMessagesProj/appicons/icons.json"))
    icons = spec.get("icons", [])
    check(len(icons) == 1, "Only the icegram launcher icon should be offered")
    if icons:
        icon = icons[0]
        check(icon.get("id") == "default" and icon.get("component") == "DefaultIcon", "Wrong default icon alias")
        check(icon.get("resource") == "ic_icegram_launcher" and icon.get("legacy") is False, "Wrong vector icon configuration")
        for field in ("background", "foreground", "monochrome"):
            reference = icon.get(field, "")
            match = re.fullmatch(r"@(color|drawable|mipmap)/(\w+)", reference)
            check(bool(match) and tuple(match.groups()) in SYMBOLS, f"Missing icon {field} resource")

    alias_doc = xml(MAIN / "AndroidManifest-appicons.xml")
    aliases = alias_doc.findall("./application/activity-alias")
    check(len(aliases) == 1, "Stale launcher aliases")
    if aliases:
        check(aliases[0].get(ANDROID + "name") == PACKAGE + ".DefaultIcon", "Wrong launcher alias package")
        check(aliases[0].get(ANDROID + "enabled") == "true", "Default launcher alias is disabled")
        check(aliases[0].get(ANDROID + "targetActivity") == "org.telegram.ui.LaunchActivity", "Wrong launcher target")
    registry = text(MAIN / "java/com/exteragram/messenger/appicons/GeneratedAppIcons.java")
    check(registry.count("AppIcon.of(") == 1 and PACKAGE + ".DefaultIcon" in registry, "Stale generated icon registry")

    manifests = list(MAIN.glob("AndroidManifest*.xml"))
    manifests += list((ROOT / "TMessagesProj/config").glob("*/AndroidManifest.xml"))
    for path in manifests:
        document = xml(path)
        for element in document.iter():
            for value in element.attrib.values():
                check(not value.startswith("@mipmap/ic_launcher"), f"Removed icon referenced in {path.relative_to(ROOT)}")
                check(not any(part in value for part in ("com.google.android.maps", "com.google.android.geo.API_KEY", "MAPS_RECEIVE")),
                      f"Maps configuration remains in {path.relative_to(ROOT)}")
        application = document.find("application")
        if application is not None and path.name != "AndroidManifest-appicons.xml":
            check(application.get(ANDROID + "icon") == "@mipmap/ic_icegram_launcher", f"Wrong app icon in {path.relative_to(ROOT)}")

    for path, document in DOCUMENTS.items():
        if path.name == "styles.xml":
            for item in document.iter("item"):
                if item.get("name") == "android:windowSplashScreenAnimatedIcon":
                    check(item.text == "@drawable/ic_icegram_foreground", f"Old splash icon in {path.relative_to(ROOT)}")


def integration():
    gradle = text(ROOT / "TMessagesProj/build.gradle")
    workflow = text(ROOT / ".github/workflows/icegram-build.yml")
    check("FullArm64Release" in workflow and "FullArmv7Release" in workflow, "Release workflow must build full with plugins")
    check("LiteArm" not in workflow and "icegram-full-" in workflow, "Stale lite release workflow")
    check("python-version: '3.11'" in workflow and "BUILD_PYTHON=" in workflow, "Full build needs explicit Python 3.11")
    check(":TMessagesProj:generateAppIcons" in workflow, "Build must regenerate launcher assets")
    check("bash gradlew" in workflow, "Upstream Gradle wrapper requires Bash, not POSIX sh")
    check(":buildSrc:testWalletCallbacks" in workflow,
          "Release must test the Wallet callback bytecode transform")
    check("org.icegram.gradle.WalletCallbacksVisitorFactory" in gradle and
          "InstrumentationScope.ALL" in gradle,
          "Maps-free Wallet transform must include the external dependency")
    factory = text(ROOT / "buildSrc/src/main/java/org/icegram/gradle/WalletCallbacksVisitorFactory.java")
    check("WalletLegacyCallbacks.CLASS_NAME.equals(classData.getClassName())" in factory,
          "Wallet transform must match only the exact callback class")
    callbacks = text(ROOT / "buildSrc/src/main/java/org/icegram/gradle/WalletLegacyCallbacks.java")
    check("EXPECTED_HASH.equals(hash)" in callbacks and "new int[] {1, 2}" in callbacks,
          "Wallet transform must fail closed and remove only legacy transactions")
    report = text(ROOT / "Tools/icegram/r8-report.init.gradle")
    check("if (app == null)" in report, "R8 diagnostic script must skip buildSrc/included builds")
    check("config.incoming.artifactView" in report and
          "componentFilter { id -> id instanceof ModuleComponentIdentifier }" in report and
          "resolvedConfiguration" not in report,
          "R8 diagnostics must filter external components before artifact selection")
    check('buildConfigField "boolean", "EXTERA_HOOK_EMBEDDED", "true"' in gradle
          and 'fullImplementation("com.exteragram.hook:hook-core:' in gradle, "Full plugin/hook support removed")
    check("java.exclude 'org/telegram/messenger/GoogleMapsProvider.java'" in gradle, "GoogleMapsProvider must not compile")
    check("exclude group: 'com.google.android.gms', module: 'play-services-maps'" in gradle, "Maps SDK must be excluded, including transitively")
    check(not re.search(r"(?:implementation|api)\s*[( ]\s*['\"]com\.google\.android\.gms:play-services-maps:", gradle), "Direct Maps dependency remains")
    proguard = text(ROOT / "TMessagesProj/proguard-rules.pro")
    check("-dontwarn com.google.android.gms.maps.model.LatLng" in proguard.splitlines(),
          "Missing narrow rule for Wallet's optional Maps model")
    check("-checkdiscard class com.google.android.gms.wallet.zzs" in proguard.splitlines(),
          "Wallet's Maps-dependent parcel creator must be discarded")
    check(not re.search(r"^\s*(?:-ignorewarnings\b|-dontwarn\s+com\.google\.android\.gms\.maps\.\*)", proguard, re.M),
          "Do not hide missing classes with broad Maps warning suppression")
    for path in (ROOT / "API_KEYS.example", ROOT / "Tools/icegram/prepare-ci.py", ROOT / ".github/workflows/icegram-build.yml"):
        check("MAPS_V2_API" not in text(path), f"Unneeded Maps key in {path.relative_to(ROOT)}")

    java = MAIN / "java"
    loader = text(java / "org/telegram/messenger/ApplicationLoader.java")
    check("new org.icegram.messenger.NoMapsProvider()" in loader, "Map renderer not disabled")
    check("if (!org.icegram.messenger.IcegramConfig.isLazyWidgetsEnabled())" in loader, "Widget prewarming is unconditional")
    config = text(java / "org/icegram/messenger/IcegramConfig.java")
    check(re.search(r"areMapsEnabled\(\)\s*\{\s*return false;", config), "Embedded maps must stay disabled")
    check('getBoolean("compact_image_cache", true)' in config and 'getBoolean("lazy_widgets", true)' in config, "Wrong resource-saving defaults")
    image_loader = text(java / "org/telegram/messenger/ImageLoader.java")
    for method in ("bitmapCacheBytes", "wallpaperCacheBytes", "animationCacheBytes"):
        check("ImageCachePolicy." + method in image_loader, f"ImageLoader does not use {method}")
    location = text(java / "org/telegram/messenger/ImageLocation.java")
    check("NoMapsProvider.PLACEHOLDER_URI.equals(path)" in location
          and "webFile.location instanceof TLRPC.TL_inputWebFileGeoPointLocation" in location, "Map thumbnails are not suppressed")
    for relative in ("org/telegram/ui/LocationActivity.java", "org/telegram/ui/Components/ChatAttachAlert.java"):
        check("IcegramConfig.areMapsEnabled()" in text(java / relative), f"Map entry guard missing: {relative}")
    settings = text(java / "com/exteragram/messenger/preferences/IcegramFeaturesPreferencesActivity.kt")
    check("PluginsController.isPluginEngineSupported()" in settings and "PluginsActivity()" in settings, "Plugin shortcut removed")
    root_settings = text(java / "org/telegram/ui/SettingsActivity.java")
    direct_settings = (
        ("-1", "ExteraGramSettings", "ExteraGramPreferencesActivity"),
        ("-2", "AyugramSettings", "AyugramPreferencesActivity"),
        ("-3", "IcegramSettings", "IcegramFeaturesPreferencesActivity"),
    )
    check(all(f"Factory.of({item_id}," in root_settings and
              f"getString(R.string.{title})" in root_settings and
              f"new {route}()" in root_settings
              for item_id, title, route in direct_settings) and
          "MainPreferencesActivity" not in root_settings,
          "The main settings screen must open ExteraGram, AyuGram and IceGram directly")
    extera_settings = text(java / "com/exteragram/messenger/preferences/ExteraGramPreferencesActivity.kt")
    check("PreferenceItem.GENERAL_CATEGORY" in extera_settings and
          "PreferenceItem.APPEARANCE_CATEGORY" in extera_settings and
          "PreferenceItem.CHATS_CATEGORY" in extera_settings and
          "PreferenceItem.AYUGRAM_CATEGORY" not in extera_settings and
          "PreferenceItem.ICEGRAM_CATEGORY" not in extera_settings,
          "ExteraGram categories must remain on their own screen")
    check("IcegramConfig.setMessageHistoryEnabled(it)" in settings and
          "MessageHistoryActivity()" in settings,
          "Message history must remain available in the IceGram settings page")
    ayugram_settings = text(java / "com/exteragram/messenger/preferences/AyugramPreferencesActivity.kt")
    check("GhostModeController.setOnlineHidden(it)" in ayugram_settings and
          "GhostModeController.setTypingHidden(it)" in ayugram_settings and
          "GhostModeController.setReadReceiptsHidden(it)" in ayugram_settings and
          "MessageHistoryActivity" not in ayugram_settings,
          "AyuGram settings must own the separate Ghost Mode controls")
    settings = text(java / "com/exteragram/messenger/preferences/IcegramFeaturesPreferencesActivity.kt")
    check("IcegramClassicInterface" in settings and "FLAG_LIQUID_GLASS" in settings,
          "Classic interface action must disable Liquid Glass")
    ghost = text(java / "org/icegram/messenger/GhostModeController.java")
    for request in ("TL_messages_readHistory", "TL_channels_readHistory",
                    "TL_messages_readMessageContents", "TL_channels_readMessageContents",
                    "TL_messages_setTyping", "TL_messages_setEncryptedTyping"):
        check(request in ghost, f"Ghost Mode does not cover {request}")
    check("TL_sendMessageCancelAction" in ghost and "TL_messages_readEncryptedHistory" not in ghost,
          "Ghost Mode must keep typing cancellations and secret-chat read behavior intact")
    check("GhostModeController.shouldSuppressRequest(object)" in text(java / "org/telegram/tgnet/ConnectionsManager.java"),
          "Ghost Mode request filter is not connected to the network send path")
    check("!IcegramConfig.isOnlineHidden() && !ignoreSetOnline" in text(java / "org/telegram/messenger/MessagesController.java"),
          "The independent online toggle must prevent online status updates")
    check("onGhostModeChanged()" in text(java / "org/telegram/messenger/MessagesController.java"),
          "Ghost Mode must reset the cached presence state when toggled")
    history_config = text(java / "org/icegram/messenger/IcegramConfig.java")
    history_storage = text(java / "org/telegram/messenger/MessagesStorage.java")
    history_controller = text(java / "org/telegram/messenger/MessagesController.java")
    history_ui = text(java / "com/exteragram/messenger/preferences/MessageHistoryActivity.kt")
    migration = text(java / "org/telegram/messenger/DatabaseMigrationHelper.java")
    check('getBoolean("ayugram_message_history", false)' in history_config,
          "Message history must be disabled by default")
    check("icegram_message_history" in history_storage and
          "ICEGRAM_MESSAGE_HISTORY_MAX_ENTRIES = 1000" in history_storage and
          "ICEGRAM_MESSAGE_HISTORY_MAX_TEXT_LENGTH = 8192" in history_storage,
          "Message history storage must be bounded")
    check("message.ttl == 0" in history_storage and
          "!MessageObject.isEphemeral(message)" in history_storage and
          "!DialogObject.isEncryptedDialog(dialogId)" in history_storage,
          "Secret and self-destructing messages must not enter the history")
    check("CREATE TABLE IF NOT EXISTS icegram_message_history" in migration and
          'PRAGMA user_version = 182' in migration,
          "Message history table migration is missing")
    delete_internal = history_storage.split(
        "private ArrayList<Long> markMessagesAsDeletedInternal(long dialogId", 1
    )[1].split("public ArrayList<Long> markMessagesAsDeleted(", 1)[0]
    check("saveMessageHistoryInternal" not in delete_internal and
          "saveDeletedMessagesToHistory(key, arrayList)" in history_controller and
          "saveDeletedMessagesToHistory(dialogId, ids)" in history_controller,
          "Only server-originated message deletions should be journaled")
    check("loadMessageHistoryPage" in history_ui and "showClearConfirmation" in history_ui,
          "Message history page/details or confirmed clear action is missing")
    lite = text(java / "org/telegram/messenger/LiteMode.java")
    check("int defaultValue = 0," in lite and 'getBoolean("lite_mode_battery_follow_system", true)' in lite, "Wrong Ice Mode defaults")
    check("oldValue ^ newValue" in lite and "onFlagsUpdate(previous, flags)" in lite, "Effects must refresh when Ice Mode changes")
    updater = text(java / "com/exteragram/messenger/updater/UpdaterUtils.java")
    update_body = updater.split("public static void getAppUpdate(", 1)[1].split("private static", 1)[0]
    check("ICEGRAM_UPDATER_DISABLED" in update_body and "sendRequest" not in update_body
          and "RemoteUtils." not in update_body, "Upstream APK updater is active")
    app = xml(MAIN / "AndroidManifest.xml").find("application")
    metadata = {item.get(ANDROID + "name"): item.get(ANDROID + "value") for item in app.findall("meta-data")}
    for key in ("firebase_analytics_collection_enabled", "firebase_crashlytics_collection_enabled"):
        check(metadata.get(key) == "false", f"Collection enabled before app startup: {key}")

    # Removed generated mipmaps must not survive as Java/Kotlin references.
    for path in java.rglob("*"):
        if path.suffix not in (".java", ".kt"):
            continue
        source = text(path)
        check(not re.search(r"\bR\.mipmap\.ic_launcher\w*", source), f"Removed generated mipmap used by {path.relative_to(ROOT)}")
        if path.name != "GoogleMapsProvider.java":
            check("com.google.android.gms.maps" not in source and "new GoogleMapsProvider(" not in source,
                  f"Google Maps SDK still referenced by {path.relative_to(ROOT)}")
        for name in set(re.findall(r"\bR\.string\.(Icegram\w+)", source)):
            check(("string", name) in SYMBOLS, f"Missing icegram string: {name}")


def private_files():
    ignore = text(ROOT / ".gitignore")
    for entry in ("API_KEYS", "!API_KEYS.example", "*.jks", "/google-services.json", "TMessagesProj/google-services.json"):
        check(entry in ignore.splitlines(), f"Missing private-file ignore rule: {entry}")
    firebase = ROOT / "TMessagesProj/google-services.json"
    if firebase.exists():
        try:
            clients = json.loads(text(firebase)).get("client", [])
            packages = {client.get("client_info", {}).get("android_client_info", {}).get("package_name") for client in clients}
            check(PACKAGE in packages, "Firebase config must include the icegram package")
        except (ValueError, TypeError, AttributeError):
            check(False, "Invalid private Firebase config (values hidden)")
    if (ROOT / ".git").exists() and shutil.which("git"):
        tracked = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, check=True, capture_output=True).stdout.decode().split("\0")
        check(not any(path in ("API_KEYS", "google-services.json") or path.endswith((".jks", "/google-services.json")) for path in tracked),
              "Private build configuration is tracked by Git")
    else:
        print("NOTE: no Git metadata/tool; tracked-secret check skipped.")
    for name in ("LICENSE", "ADDITIONAL_TERMS.md", "ICEGRAM.md"):
        check((ROOT / name).is_file() and (ROOT / name).stat().st_size > 0, f"Missing attribution/license: {name}")
    for path in (ROOT / "Tools/icegram").glob("*.py"):
        compile(text(path), str(path), "exec")


def main():
    try:
        resources()
        branding()
        integration()
        private_files()
    except (OSError, ValueError, KeyError, IndexError, AttributeError, SyntaxError, subprocess.CalledProcessError) as error:
        check(False, f"Checker could not finish ({type(error).__name__}); inspect source/config structure.")
    if ERRORS:
        for error in ERRORS:
            print("FAIL: " + error, file=sys.stderr)
        raise SystemExit(1)
    print(f"PASS: {CHECKS} structural checks; {len(DOCUMENTS)} XML files parsed.")
    print("Android compilation, R8, native/Python packaging and device tests are still required.")


if __name__ == "__main__":
    main()
