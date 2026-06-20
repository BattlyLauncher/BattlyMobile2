# Battly Mobile

Battly Mobile is the Android version of Battly Launcher. It runs Minecraft: Java Edition on Android with the Battly account system, Battly skins, mobile controls, modded versions, BattlyWorlds and Battly+ features.

This project is based on the Android Java launcher stack from PojavLauncher/Boardwalk and contains Battly-specific product, account, API, cloud and UI integrations.

## Main Features

- Minecraft: Java Edition on Android, including legacy and modern versions.
- Runtime management for Java 8, Java 17, Java 21 and newer runtimes used by recent Minecraft builds.
- Support for vanilla, Forge, Fabric, Quilt, NeoForge and LegacyFabric installation flows.
- Battly account login with Battly avatars, skins and authlib injector support.
- BattlyWorlds private LAN rooms for inviting friends without router port forwarding. This feature is temporarily disabled in production builds while Google Play reviews VPNService access.
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
- JDK compatible with Android Gradle Plugin 8.7.x.
- Android NDK `27.3.13750724`.
- `local.properties` pointing to your Android SDK.

Debug build:

```powershell
.\gradlew :app_pojavlauncher:assembleDebug --console=plain
```

Install debug build:

```powershell
adb install .\app_pojavlauncher\build\outputs\apk\debug\app_pojavlauncher-debug.apk
```

Release build:

```powershell
.\gradlew :app_pojavlauncher:assembleRelease --console=plain
```

Google Play signed build:

```powershell
.\gradlew :app_pojavlauncher:bundleGplay --console=plain
```

The Google Play build requires release signing configuration, described below.

## Secrets And Local Configuration

Do not commit signing keys, Firebase service accounts or API keys. The repository `.gitignore` excludes the expected local secret files.

Supported local inputs:

- `CURSEFORGE_API_KEY` environment variable, or local `curseforge_key.txt`.
- `keystore.properties`, or `GPLAY_KEYSTORE_FILE`, `GPLAY_KEYSTORE_PASSWORD`, `GPLAY_KEY_ALIAS`, `GPLAY_KEY_PASSWORD`.
- Variant-specific Firebase configs under local-only source sets when needed.
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

## Notifications

Battly Mobile uses Firebase Cloud Messaging for:

- BattlyWorlds friend invites.
- Important Battly announcements.
- Background install/download status.
- In-app messages shown while the launcher is open.

The notification small icon is generated from the real Battly logo asset and kept as an Android-safe alpha icon for notification rendering.

## BattlyWorlds Status

BattlyWorlds code remains in the repository, but the feature is currently disabled through `BattlyWorldsFeature.ENABLED = false` and the VPN service declaration is not exported in the Android manifest. This keeps the app reversible while avoiding VPNService usage until Google Play grants access.

## Storage

Battly Mobile does not require `MANAGE_EXTERNAL_STORAGE`. Runtime data, versions, assets, libraries, logs, worlds, mods, resource packs, shaders and launcher configuration are stored under the app-owned external files directory returned by `getExternalFilesDir(null)`.

External imports should use Android document pickers (`ACTION_OPEN_DOCUMENT` or `ACTION_OPEN_DOCUMENT_TREE`) and then copy selected files into Battly storage.

## Release Checklist

1. Confirm there are no committed secrets:

   ```powershell
   git status --short
   rg --hidden --glob '!**/.git/**' --glob '!**/build/**' --glob '!MobileGlues/**' --glob '!app_pojavlauncher/src/main/jni/SDL/**' -n "BEGIN PRIVATE KEY|storePassword|keyPassword|client_secret|FIREBASE_SERVICE_ACCOUNT" .
   ```

2. Build and test debug:

   ```powershell
   .\gradlew :app_pojavlauncher:assembleDebug --console=plain
   adb install .\app_pojavlauncher\build\outputs\apk\debug\app_pojavlauncher-debug.apk
   ```

3. Build the Play artifact with signing configured:

   ```powershell
   .\gradlew :app_pojavlauncher:bundleGplay --console=plain
   ```

4. Verify native libraries are packaged with 16 KB page-size compatibility.
5. Verify Battly login, Battly+, Firebase notifications, BattlyWorlds and Minecraft launch on a real device.

## Credits

Battly Mobile builds on work from PojavLauncher, Boardwalk, LWJGL, LWJGLX, GL4ES, MobileGlues, ANGLE, Mesa, OpenJDK, OpenAL-Soft, SDL and the wider open-source Android Minecraft launcher ecosystem.

See source headers and bundled license files for dependency-specific licensing.
