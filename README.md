# icegram

An independent Android Telegram client based on
[exteraGram 13.2.2](https://github.com/exteraSquad/exteraGram).
This is not an official Telegram or exteraGram release.

**Prototype: no APK has been built or tested yet.** Lower memory use, a smaller
APK or better battery life have not been measured and are not guaranteed.

## What changes

- A separate app name, icon and application ID: `org.icegram.messenger`.
- The **full** edition retains Python plugins, their SDK and method hooking.
- Embedded maps, map selection and map thumbnails are disabled; the Google Maps
  SDK is excluded. Google Location remains available. No Maps API key is needed.
- Smaller optional image caches, on-demand HTML widget startup and calmer
  initial animation settings, with a dedicated icegram settings screen.
- Upstream APK updates and default Analytics/Crashlytics collection are disabled.
  Messaging, calls and push notifications are not intentionally disabled.

Full builds include Python and native plugin libraries. They are larger than
lite builds, and plugins may themselves increase resource use.

## Build on GitHub Actions

Corresponding modified source:
**<https://github.com/Airat-Dev96/IceGram>**.

1. Select the branch containing the icegram changes and enable Actions in the fork.
2. Add your own Telegram, Firebase and signing configuration in
   **Settings → Secrets and variables → Actions**.
3. Open **Actions → Build icegram APK → Run workflow**, select that branch
   and `arm64` (or `armv7` for an older 32-bit phone).
4. After a successful run, download the `icegram-full-*` artifact and extract
   its APK. A successful build still requires the device checks below.

See [ICEGRAM.md](ICEGRAM.md) for the required secret names, toolchain,
local build commands and Russian documentation. Never commit `API_KEYS`,
`google-services.json`, signing keys or passwords.

The **icegram checks** workflow validates source integration and cache-budget
policy without private keys. It does not compile the complete Android client.
The release workflow builds one ARM architecture, including the plugin engine.

## Development

Use JDK 17, Android SDK 37, Build Tools 36.0.0, NDK 29.0.14206865,
CMake 4.x and **Python 3.11**. Initialize the pinned submodules first.
See [ICEGRAM.md](ICEGRAM.md) rather than the older upstream Dockerfile.

Before distributing an APK, complete
[the testing checklist](Tools/icegram/TESTING.md). Include a link to the exact
source revision used to build it; the workflow adds this link to its artifact.
Plugin SDK documentation is in [plugins-pysdk](plugins-pysdk/README.md).

## Credits and license

Based on [exteraGram](https://github.com/exteraSquad/exteraGram) and
[Telegram for Android](https://github.com/DrKLO/Telegram).
Upstream authorship and copyright notices are retained.

Upstream also credits [Catogram](https://github.com/Catogram/Catogram),
[Nekogram](https://gitlab.com/Nekogram/Nekogram),
[OwlGram](https://github.com/OwlGramDev/OwlGram) and
[Crowdin translators](https://crowdin.com/project/exteralocales).

[GNU General Public License v3.0 or later](LICENSE), with
[additional terms](ADDITIONAL_TERMS.md) concerning attribution and the use of
the exteraGram name and logo. icegram uses its own launcher branding.
