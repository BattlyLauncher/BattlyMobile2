# Battly Mobile

Battly Mobile is the Android version of Battly Launcher. It runs Minecraft: Java Edition on Android with the Battly account system, Battly skins, mobile controls, modded versions, BattlyWorlds and Battly+ features.

This project is based on the Android Java launcher stack from PojavLauncher/Boardwalk and contains Battly-specific product, account, API, cloud and UI integrations.

See [CHANGELOG.md](CHANGELOG.md) for release notes. Google Play publication additionally requires every bundled native library to pass the 16 KB page-size audit described in [docs/android-production-release.md](docs/android-production-release.md).

## Main Features

- Minecraft: Java Edition on Android, including legacy and modern versions.
- Runtime management for Java 8, Java 17, Java 21 and newer runtimes used by recent Minecraft builds.
- Support for vanilla, Forge, Fabric, Quilt, NeoForge and LegacyFabric installation flows.
- Battly account login with Battly avatars, skins and authlib injector support.
- BattlyWorlds private LAN rooms for inviting friends without router port forwarding.
- Battly+ modules: BattlyWorlds Plus, Cloud Sync, Google Drive backups, shared installations, mod updates, Battly Boost, premium download queue, custom backgrounds and app icons.
- Marketplace and local management for mods, resource packs, shader packs, datapacks and control layouts.
- Firebase push notifications and in-app messages for Battly updates and friend invites.
- Android scoped storage: game data is stored in the app-owned external files directory.

## Repository Layout

- `app_pojavlauncher/`: Android app module.
- `jre_lwjgl3glfw/`, `lwjgl2_methods_injector/`, `arc_dns_injector/`, `forge_installer/`: launcher support modules.
- `Terracotta/`: BattlyWorlds/Terracotta integration.
- `docs/`: production, release and operational notes.
- `scripts/`: helper scripts used by the Android project.

## Local Build

Requirements:

- Android Studio or Android SDK command-line tools.
- JDK compatible with Android Gradle Plugin 8.11.x.
- Android SDK 36 and Android NDK `28.2.13676358`.
- `local.properties` pointing to your Android SDK.

Debug build:

```powershell
.\gradlew :app_pojavlauncher:assembleDebug --console=plain
```

Install debug build:

```powershell
adb install .\app_pojavlauncher\build\outputs\apk\debug\app_pojavlauncher-debug.apk
```

Signed release artifacts for GitHub and Google Play:

```powershell
.\gradlew :app_pojavlauncher:assembleGplay :app_pojavlauncher:bundleGplay --console=plain
```

Generated files:

```powershell
app_pojavlauncher/build/outputs/apk/gplay/app_pojavlauncher-gplay.apk
app_pojavlauncher/build/outputs/bundle/gplay/app_pojavlauncher-gplay.aab
```

The Google Play build requires release signing configuration, described below.

## Secrets And Local Configuration

Do not commit signing keys, Firebase service accounts or API keys. The repository `.gitignore` excludes the expected local secret files.

Supported local inputs:

- `CURSEFORGE_API_KEY` environment variable, or local `curseforge_key.txt`.
- `keystore.properties`, or `GPLAY_KEYSTORE_FILE`, `GPLAY_KEYSTORE_PASSWORD`, `GPLAY_KEY_ALIAS`, `GPLAY_KEY_PASSWORD`.
- Variant-specific Firebase configs under local-only source sets when needed.
- `BATTLY_GOOGLE_DEBUG_CLIENT_ID` and `BATTLY_GOOGLE_RELEASE_CLIENT_ID` when overriding the public OAuth client IDs for either environment.
- Service account JSON files outside the repo or in ignored local secret folders.

Example `keystore.properties`:

```properties
storeFile=release-secrets/battly-upload.jks
storePassword=change-me
keyAlias=upload
keyPassword=change-me
```

## Firebase Variants

Debug and release must use separate Firebase Android apps:

- Debug application id: `com.tecnobros.battlylauncher.debug`
- Release application id: `com.tecnobros.battlylauncher`

Keep production and test Firebase configuration separated. Do not commit private Firebase Admin service account files.

### Legacy Git History

Public releases are created from a clean root commit and must never merge the imported launcher history. The old local `v3_openjdk` ancestry contains the legacy `app_pojavlauncher/upload.jks`; treat that historical upload key as compromised and rotate it in Google Play Console. The clean release branch removes that file and its history from the published ref, while preserving the old branch locally for reference.

## Notifications

Battly Mobile uses Firebase Cloud Messaging for:

- BattlyWorlds friend invites.
- Important Battly announcements.
- Background install/download status.
- In-app messages shown while the launcher is open.

The notification small icon is generated from the real Battly logo asset and kept as an Android-safe alpha icon for notification rendering.

## BattlyWorlds Status

BattlyWorlds is disabled by default while Google Play VPNService access is pending. The integration remains in the source tree and is controlled by one build property: `-PbattlyWorldsEnabled=true`. That property enables the runtime feature and adds its VPN service and `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission to the merged manifest.

## Storage

Battly Mobile does not require `MANAGE_EXTERNAL_STORAGE`. Runtime data, versions, assets, libraries, logs, worlds, mods, resource packs, shaders and launcher configuration are stored under the app-owned external files directory returned by `getExternalFilesDir(null)`.

External imports should use Android document pickers (`ACTION_OPEN_DOCUMENT` or `ACTION_OPEN_DOCUMENT_TREE`) and then copy selected files into Battly storage.

## Release Checklist

1. Verify that Battly-owned UI text is localized and no translatable text bypasses Android resources:

   ```powershell
   python .\tools\i18n\audit_android_i18n.py
   ```

2. Confirm there are no committed secrets:

   ```powershell
   git status --short
   rg --hidden --glob '!**/.git/**' --glob '!**/build/**' --glob '!MobileGlues/**' --glob '!app_pojavlauncher/src/main/jni/SDL/**' -n "BEGIN PRIVATE KEY|storePassword|keyPassword|client_secret|FIREBASE_SERVICE_ACCOUNT" .
   ```

   This checks the current tree. Also confirm that the release is being pushed from the clean root branch and that the legacy upload key has been rotated.

3. Build and test debug:

   ```powershell
   .\gradlew :app_pojavlauncher:assembleDebug --console=plain
   adb install .\app_pojavlauncher\build\outputs\apk\debug\app_pojavlauncher-debug.apk
   ```

4. Build the signed GitHub and Play artifacts:

   ```powershell
   .\gradlew :app_pojavlauncher:assembleGplay :app_pojavlauncher:bundleGplay --console=plain
   ```

5. Verify native libraries are packaged with 16 KB page-size compatibility.
6. Verify Battly login, Battly+, Firebase notifications, BattlyWorlds and Minecraft launch on a real device.

## Credits

Battly Mobile builds on work from PojavLauncher, Boardwalk, LWJGL, LWJGLX, GL4ES, MobileGlues, LTW, ANGLE, Mesa, OpenJDK, OpenAL-Soft, SDL and the wider open-source Android Minecraft launcher ecosystem.

See source headers and bundled license files for dependency-specific licensing.
