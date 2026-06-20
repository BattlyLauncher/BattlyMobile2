package net.kdt.pojavlaunch.utils;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftLauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BattlyPlusCloud {
    private static final String TAG = "BattlyPlusCloud";
    private static final String API_BASE = "https://api.battlylauncher.com/api/mobile/plus";
    public static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";

    public interface Callback {
        void onResult(boolean ok, String message);
    }

    private BattlyPlusCloud() {
    }

    public static void syncNow(Context context, Callback callback) {
        runPremiumAction(context, callback, () -> {
            JSONObject body = new JSONObject();
            body.put("deviceName", getDeviceName());
            body.put("snapshot", buildSyncSnapshot(context));
            JSONObject response = post(context, "/sync", body);
            return response.optString("cdnUrl", context.getString(R.string.battly_plus_cloud_sync_done));
        });
    }

    public static void restoreLatestSync(Context context, Callback callback) {
        runPremiumAction(context, callback, () -> {
            JSONObject response = get(context, "/sync");
            JSONObject sync = response.optJSONObject("sync");
            JSONObject snapshot = sync == null ? null : sync.optJSONObject("snapshot");
            if (snapshot == null) {
                throw new IllegalStateException(context.getString(R.string.battly_plus_cloud_restore_empty));
            }
            applySyncSnapshot(context, snapshot);
            return context.getString(R.string.battly_plus_cloud_restore_done);
        });
    }

    public static void recordGoogleDriveBackup(Context context, Callback callback) {
        runPremiumAction(context, callback, () -> {
            MinecraftProfile profile = safeCurrentProfile();
            File world = findLatestWorld(profile);
            JSONObject body = new JSONObject();
            body.put("profileName", profile == null ? "" : safe(profile.name));
            body.put("worldName", world == null ? "" : world.getName());
            body.put("sizeBytes", world == null ? 0 : folderSize(world, 1000));
            body.put("status", "pending_native_drive_upload");
            JSONObject response = post(context, "/backups", body);
            JSONObject backup = response.optJSONObject("backup");
            return backup == null
                    ? context.getString(R.string.battly_plus_backup_ready)
                    : context.getString(R.string.battly_plus_backup_recorded,
                    backup.optString("worldName", context.getString(R.string.launcher_version_unknown)));
        });
    }

    public static void uploadLatestWorldToGoogleDrive(Context context, GoogleSignInAccount account, Callback callback) {
        uploadAllWorldsToGoogleDrive(context, account, callback);
    }

    public static void uploadAllWorldsToGoogleDrive(Context context, GoogleSignInAccount account, Callback callback) {
        runPremiumAction(context, callback, () -> {
            if (account == null || account.getAccount() == null) {
                throw new IllegalStateException(context.getString(R.string.battly_plus_drive_account_required));
            }

            WorldArchive archiveInfo = zipAllProfileWorlds(context);
            if (archiveInfo.worldCount <= 0 || archiveInfo.archive == null || !archiveInfo.archive.exists()) {
                throw new IllegalStateException(context.getString(R.string.battly_plus_drive_backup_no_worlds));
            }

            JSONObject driveFile = uploadArchiveToDrive(context, account.getAccount(), archiveInfo.archive,
                    "All_Worlds", true);

            JSONObject body = new JSONObject();
            body.put("profileName", "All profiles");
            body.put("worldName", "All worlds");
            body.put("worldCount", archiveInfo.worldCount);
            body.put("profileCount", archiveInfo.profileCount);
            body.put("sizeBytes", archiveInfo.archive.length());
            body.put("status", "uploaded");
            body.put("driveFileId", driveFile.optString("id", ""));
            body.put("driveUrl", driveFile.optString("webViewLink", ""));
            post(context, "/backups", body);
            return context.getString(R.string.battly_plus_drive_backup_uploaded,
                    archiveInfo.worldCount + " worlds");
        });
    }

    public static void shareCurrentInstallation(Context context, Callback callback) {
        shareCurrentInstallation(context, safeCurrentProfile(), callback);
    }

    public static void shareCurrentInstallation(Context context, MinecraftProfile profile, Callback callback) {
        runPremiumAction(context, callback, () -> {
            JSONObject manifest = buildInstallationManifest(profile);
            JSONObject body = new JSONObject();
            body.put("title", manifest.optString("name", "Battly installation"));
            body.put("manifest", manifest);
            JSONObject response = post(context, "/share-installation", body);
            String publicUrl = response.optString("publicUrl");
            String deepLink = response.optString("deepLink");
            String code = response.optString("code");
            return context.getString(R.string.battly_plus_shared_installation_done,
                    Tools.isValidString(code) ? code : "------",
                    Tools.isValidString(deepLink) ? deepLink
                            : Tools.isValidString(publicUrl) ? publicUrl : response.optString("cdnUrl", ""));
        });
    }

    public static void checkModUpdates(Context context, Callback callback) {
        checkModUpdates(context, safeCurrentProfile(), callback);
    }

    public static void checkModUpdates(Context context, MinecraftProfile profile, Callback callback) {
        runPremiumAction(context, callback, () -> {
            JSONObject body = new JSONObject();
            body.put("mods", scanMods(profile));
            body.put("minecraftVersion", inferMinecraftVersion(profile));
            body.put("loader", inferLoader(profile));
            body.put("profileName", profile == null ? "" : safe(profile.name));
            JSONObject response = post(context, "/mods/update-plan", body);
            int checked = response.optInt("checked", 0);
            int updates = response.optJSONArray("updates") == null ? 0 : response.optJSONArray("updates").length();
            return context.getString(R.string.battly_plus_mod_updates_done, checked, updates);
        });
    }

    public static void importSharedInstallation(Context context, String code, Callback callback) {
        Context appContext = context.getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String cleanCode = safe(code).replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
                if (!Tools.isValidString(cleanCode)) {
                    throw new IllegalArgumentException(appContext.getString(R.string.battly_plus_shared_import_invalid));
                }
                JSONObject response = getPublic(appContext, "/share-installation/" + cleanCode);
                JSONObject manifest = response.optJSONObject("manifest");
                if (manifest == null) {
                    throw new IllegalStateException(appContext.getString(R.string.battly_plus_shared_import_invalid));
                }
                MinecraftProfile profile = MinecraftProfile.createTemplate();
                String name = manifest.optString("name", response.optString("title", "Battly installation"));
                profile.name = name + " [" + cleanCode + "]";
                profile.lastVersionId = manifest.optString("minecraftVersion", MinecraftProfile.LATEST_RELEASE);
                profile.pojavRendererName = manifest.optString("renderer", "");
                profile.controlFile = manifest.optString("controlFile", "");
                profile.gameDir = "instances/" + sanitizeFileName(profile.name);

                if (LauncherProfiles.mainProfileJson == null) {
                    LauncherProfiles.load();
                }
                LauncherProfiles.insertMinecraftProfile(profile);
                LauncherProfiles.write();
                Tools.runOnUiThread(() -> {
                    if (callback != null) {
                        callback.onResult(true, appContext.getString(
                                R.string.battly_plus_shared_import_done, profile.name));
                    }
                });
            } catch (Throwable throwable) {
                String message = throwable.getMessage();
                Tools.runOnUiThread(() -> {
                    if (callback != null) {
                        callback.onResult(false, Tools.isValidString(message)
                                ? message : appContext.getString(R.string.global_error));
                    }
                });
            }
        });
    }

    public static void applyBattlyBoost(Context context, Callback callback) {
        runPremiumAction(context, callback, () -> {
            JSONObject body = new JSONObject();
            JSONObject device = new JSONObject();
            device.put("manufacturer", Build.MANUFACTURER);
            device.put("model", Build.MODEL);
            device.put("sdk", Build.VERSION.SDK_INT);
            device.put("memoryMb", Tools.getTotalDeviceMemory(context));
            body.put("device", device);

            JSONObject settings = new JSONObject();
            settings.put("resolutionPercent", LauncherPreferences.DEFAULT_PREF == null
                    ? 100 : LauncherPreferences.DEFAULT_PREF.getInt("resolutionRatio", 100));
            settings.put("sustainedPerformance", LauncherPreferences.DEFAULT_PREF != null
                    && LauncherPreferences.DEFAULT_PREF.getBoolean("sustainedPerformance", false));
            body.put("settings", settings);

            post(context, "/boost/report", body);
            applyLocalBoost(context);
            return context.getString(R.string.battly_plus_boost_done);
        });
    }

    public static boolean canUsePremiumQueue(Context context) {
        return BattlyPlusManager.isPlus(context);
    }

    private interface Worker {
        String run() throws Exception;
    }

    private static void runPremiumAction(Context context, Callback callback, Worker worker) {
        Context appContext = context.getApplicationContext();
        BattlyPlusManager.refreshAsync(appContext, plus -> {
            if (!plus) {
                if (callback != null) {
                    Tools.runOnUiThread(() -> callback.onResult(false, appContext.getString(R.string.battly_plus_required)));
                }
                return;
            }
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    String message = worker.run();
                    if (callback != null) {
                        Tools.runOnUiThread(() -> callback.onResult(true, message));
                    }
                } catch (Throwable throwable) {
                    if (callback != null) {
                        String message = throwable.getMessage();
                        Tools.runOnUiThread(() -> callback.onResult(false,
                                Tools.isValidString(message) ? message : appContext.getString(R.string.global_error)));
                    }
                }
            });
        });
    }

    private static JSONObject buildSyncSnapshot(Context context) throws Exception {
        if (LauncherProfiles.mainProfileJson == null) {
            LauncherProfiles.load();
        }
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF == null
                ? PreferenceManager.getDefaultSharedPreferences(context)
                : LauncherPreferences.DEFAULT_PREF;

        JSONObject snapshot = new JSONObject();
        snapshot.put("schema", "battly-mobile-sync-v1");
        snapshot.put("profiles", new JSONObject(Tools.GLOBAL_GSON.toJson(LauncherProfiles.mainProfileJson)));
        snapshot.put("profileIndex", buildProfileIndex());
        snapshot.put("currentProfile", prefs.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, ""));
        snapshot.put("defaultControls", prefs.getString("defaultCtrl", Tools.CTRLDEF_FILE));
        snapshot.put("background", prefs.getString(BattlyBackgrounds.PREF_SELECTED_BACKGROUND, "default"));
        snapshot.put("renderer", prefs.getString("renderer", ""));
        snapshot.put("ramAllocation", prefs.getInt("allocation", LauncherPreferences.PREF_RAM_ALLOCATION));
        snapshot.put("resolutionRatio", prefs.getInt("resolutionRatio", 100));
        snapshot.put("timestamp", System.currentTimeMillis());
        return snapshot;
    }

    private static void applySyncSnapshot(Context context, JSONObject snapshot) {
        JSONObject profiles = snapshot.optJSONObject("profiles");
        if (profiles != null) {
            MinecraftLauncherProfiles restored = Tools.GLOBAL_GSON.fromJson(profiles.toString(),
                    MinecraftLauncherProfiles.class);
            if (restored != null && restored.profiles != null && !restored.profiles.isEmpty()) {
                LauncherProfiles.mainProfileJson = restored;
                LauncherProfiles.write();
            }
        }

        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF == null
                ? PreferenceManager.getDefaultSharedPreferences(context)
                : LauncherPreferences.DEFAULT_PREF;
        SharedPreferences.Editor editor = prefs.edit();
        putStringIfValid(editor, LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                snapshot.optString("currentProfile", ""));
        putStringIfValid(editor, "defaultCtrl", snapshot.optString("defaultControls", ""));
        putStringIfValid(editor, BattlyBackgrounds.PREF_SELECTED_BACKGROUND,
                snapshot.optString("background", ""));
        putStringIfValid(editor, "renderer", snapshot.optString("renderer", ""));
        if (snapshot.has("ramAllocation")) {
            editor.putInt("allocation", snapshot.optInt("ramAllocation", LauncherPreferences.PREF_RAM_ALLOCATION));
        }
        if (snapshot.has("resolutionRatio")) {
            editor.putInt("resolutionRatio", snapshot.optInt("resolutionRatio", 100));
        }
        editor.apply();
        LauncherPreferences.loadPreferences(context);
        LauncherProfiles.load();
    }

    private static void putStringIfValid(SharedPreferences.Editor editor, String key, String value) {
        if (Tools.isValidString(value)) {
            editor.putString(key, value);
        }
    }

    private static JSONObject buildInstallationManifest() throws Exception {
        return buildInstallationManifest(safeCurrentProfile());
    }

    private static JSONObject buildInstallationManifest(MinecraftProfile profile) throws Exception {
        if (LauncherProfiles.mainProfileJson == null) {
            LauncherProfiles.load();
        }
        if (profile == null) {
            profile = safeCurrentProfile();
        }
        JSONObject manifest = new JSONObject();
        manifest.put("schema", "battly-mobile-installation-v1");
        manifest.put("name", safe(profile.name));
        manifest.put("minecraftVersion", safe(profile.lastVersionId));
        manifest.put("renderer", safe(profile.pojavRendererName));
        manifest.put("controlFile", safe(profile.controlFile));
        manifest.put("mods", scanMods(profile));
        manifest.put("gameDir", safe(profile.gameDir));
        manifest.put("createdAt", System.currentTimeMillis());
        return manifest;
    }

    private static JSONArray scanCurrentMods() throws Exception {
        return scanMods(safeCurrentProfile());
    }

    private static JSONArray scanMods(MinecraftProfile profile) throws Exception {
        JSONArray mods = new JSONArray();
        File modsDir = new File(Tools.getGameDirPath(profile), "mods");
        File[] files = modsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (files == null) {
            return mods;
        }
        for (File file : files) {
            JSONObject mod = new JSONObject();
            mod.put("fileName", file.getName());
            mod.put("normalizedName", normalizeModFileName(file.getName()));
            mod.put("sizeBytes", file.length());
            mod.put("sha1", sha1(file));
            mod.put("curseforgeFingerprint", Integer.toUnsignedLong(curseForgeFingerprint(file)));
            mod.put("profileName", profile == null ? "" : safe(profile.name));
            enrichModMetadata(file, mod);
            mods.put(mod);
        }
        return mods;
    }

    private static void enrichModMetadata(File file, JSONObject mod) {
        try (JarFile jarFile = new JarFile(file)) {
            Set<String> ids = new LinkedHashSet<>();
            Set<String> names = new LinkedHashSet<>();
            Set<String> versions = new LinkedHashSet<>();
            JSONArray mcmodEntries = readMcmodInfo(jarFile, ids, names, versions);
            if (mcmodEntries.length() > 0) {
                mod.put("mcmodInfo", mcmodEntries);
            }

            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                JSONObject manifestJson = new JSONObject();
                Attributes attributes = manifest.getMainAttributes();
                addManifestValue(attributes, manifestJson, "Implementation-Title");
                addManifestValue(attributes, manifestJson, "Implementation-Version");
                addManifestValue(attributes, manifestJson, "Specification-Title");
                addManifestValue(attributes, manifestJson, "Specification-Version");
                addManifestValue(attributes, manifestJson, "TweakClass");
                addManifestValue(attributes, manifestJson, "FMLCorePlugin");
                addManifestValue(attributes, manifestJson, "FMLCorePluginContainsFMLMod");
                if (manifestJson.length() > 0) {
                    mod.put("manifest", manifestJson);
                }
                addSetValue(ids, attributes.getValue("Implementation-Title"));
                addSetValue(versions, attributes.getValue("Implementation-Version"));
            }

            JSONArray idArray = toJsonArray(ids);
            JSONArray nameArray = toJsonArray(names);
            JSONArray versionArray = toJsonArray(versions);
            if (idArray.length() > 0) mod.put("modIds", idArray);
            if (nameArray.length() > 0) mod.put("names", nameArray);
            if (versionArray.length() > 0) mod.put("versions", versionArray);
        } catch (Throwable ignored) {
            // Metadata is best-effort; hashes and fingerprints are still enough for most providers.
        }
    }

    private static JSONArray readMcmodInfo(JarFile jarFile, Set<String> ids, Set<String> names,
                                           Set<String> versions) throws Exception {
        JSONArray entries = new JSONArray();
        ZipEntry entry = jarFile.getEntry("mcmod.info");
        if (entry == null) {
            entry = jarFile.getEntry("META-INF/mods.toml");
        }
        if (entry == null) {
            return entries;
        }
        String content;
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            content = readFully(inputStream);
        }
        if (!Tools.isValidString(content)) {
            return entries;
        }
        if (entry.getName().endsWith(".toml")) {
            JSONObject tomlSummary = parseModsTomlSummary(content, ids, names, versions);
            if (tomlSummary.length() > 0) {
                entries.put(tomlSummary);
            }
            return entries;
        }
        Object parsed = content.trim().startsWith("[")
                ? new JSONArray(content)
                : new JSONObject(content);
        if (parsed instanceof JSONArray) {
            JSONArray array = (JSONArray) parsed;
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                collectModInfo(object, ids, names, versions);
                entries.put(object);
            }
        } else {
            JSONObject object = (JSONObject) parsed;
            JSONArray modList = object.optJSONArray("modList");
            if (modList != null) {
                for (int i = 0; i < modList.length(); i++) {
                    JSONObject modObject = modList.optJSONObject(i);
                    if (modObject == null) continue;
                    collectModInfo(modObject, ids, names, versions);
                    entries.put(modObject);
                }
            } else {
                collectModInfo(object, ids, names, versions);
                entries.put(object);
            }
        }
        return entries;
    }

    private static JSONObject parseModsTomlSummary(String content, Set<String> ids, Set<String> names,
                                                   Set<String> versions) {
        JSONObject summary = new JSONObject();
        for (String rawLine : content.split("\n")) {
            String line = rawLine.trim();
            int equals = line.indexOf('=');
            if (equals <= 0) continue;
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                value = value.substring(1, value.length() - 1);
            }
            if ("modId".equals(key)) {
                addSetValue(ids, value);
                putIfValid(summary, "modid", value);
            } else if ("displayName".equals(key)) {
                addSetValue(names, value);
                putIfValid(summary, "name", value);
            } else if ("version".equals(key)) {
                addSetValue(versions, value);
                putIfValid(summary, "version", value);
            }
        }
        return summary;
    }

    private static void collectModInfo(JSONObject object, Set<String> ids, Set<String> names, Set<String> versions) {
        addSetValue(ids, object.optString("modid", object.optString("modId", "")));
        addSetValue(names, object.optString("name", object.optString("displayName", "")));
        addSetValue(versions, object.optString("version", ""));
        JSONArray dependencies = object.optJSONArray("dependencies");
        if (dependencies != null) {
            for (int i = 0; i < dependencies.length(); i++) {
                JSONObject dependency = dependencies.optJSONObject(i);
                if (dependency != null) {
                    addSetValue(ids, dependency.optString("modid", dependency.optString("modId", "")));
                }
            }
        }
    }

    private static void addManifestValue(Attributes attributes, JSONObject target, String name) throws Exception {
        putIfValid(target, name, attributes.getValue(name));
    }

    private static void putIfValid(JSONObject target, String key, String value) {
        if (!Tools.isValidString(value)) {
            return;
        }
        try {
            target.put(key, value);
        } catch (Throwable ignored) {
        }
    }

    private static void addSetValue(Set<String> values, String value) {
        if (Tools.isValidString(value)) {
            values.add(value.trim());
        }
    }

    private static JSONArray toJsonArray(Set<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        return array;
    }

    private static String normalizeModFileName(String fileName) {
        String value = safe(fileName).replaceAll("(?i)\\.jar$", "");
        value = value.replaceAll("(?i)([-_ ]?mc)?1\\.\\d+(?:\\.\\d+)?", " ");
        value = value.replaceAll("(?i)[-_ ]?(forge|fabric|quilt|neoforge|universal|client)", " ");
        value = value.replaceAll("[-_ ]?\\d+(?:\\.\\d+)+(?:[-+._a-zA-Z0-9]*)?", " ");
        value = value.replaceAll("[^a-zA-Z0-9]+", " ").trim().toLowerCase(Locale.ROOT);
        return Tools.isValidString(value) ? value : safe(fileName).replaceAll("(?i)\\.jar$", "");
    }

    private static String inferMinecraftVersion(MinecraftProfile profile) {
        String versionId = safe(profile == null ? "" : profile.lastVersionId);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+(?:\\.\\d+)?)")
                .matcher(versionId);
        return matcher.find() ? matcher.group(1) : versionId;
    }

    private static String inferLoader(MinecraftProfile profile) {
        String versionId = safe(profile == null ? "" : profile.lastVersionId).toLowerCase(Locale.ROOT);
        if (versionId.contains("neoforge")) return "neoforge";
        if (versionId.contains("fabric")) return "fabric";
        if (versionId.contains("quilt")) return "quilt";
        if (versionId.contains("forge")) return "forge";
        if (versionId.contains("optifine")) return "optifine";
        return "";
    }

    public static List<MinecraftProfile> getAvailableProfiles() {
        try {
            if (LauncherProfiles.mainProfileJson == null) {
                LauncherProfiles.load();
            }
            if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null) {
                return Collections.singletonList(MinecraftProfile.getDefaultProfile());
            }
            List<MinecraftProfile> profiles = new ArrayList<>();
            for (MinecraftProfile profile : LauncherProfiles.mainProfileJson.profiles.values()) {
                if (profile != null) {
                    profiles.add(profile);
                }
            }
            profiles.sort((a, b) -> safe(a.name).compareToIgnoreCase(safe(b.name)));
            return profiles.isEmpty() ? Collections.singletonList(MinecraftProfile.getDefaultProfile()) : profiles;
        } catch (Throwable ignored) {
            return Collections.singletonList(MinecraftProfile.getDefaultProfile());
        }
    }

    private static JSONArray buildProfileIndex() throws Exception {
        if (LauncherProfiles.mainProfileJson == null) {
            LauncherProfiles.load();
        }
        JSONArray index = new JSONArray();
        if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null) {
            return index;
        }
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null) continue;
            JSONObject item = new JSONObject();
            item.put("id", entry.getKey());
            item.put("name", safe(profile.name));
            item.put("minecraftVersion", safe(profile.lastVersionId));
            item.put("gameDir", safe(profile.gameDir));
            item.put("mods", scanMods(profile).length());
            item.put("worlds", listWorlds(profile).length);
            index.put(item);
        }
        return index;
    }

    private static MinecraftProfile safeCurrentProfile() {
        try {
            if (LauncherProfiles.mainProfileJson == null) {
                LauncherProfiles.load();
            }
            return LauncherProfiles.getCurrentProfile();
        } catch (Throwable ignored) {
            List<MinecraftProfile> profiles = getAvailableProfiles();
            return profiles.isEmpty() ? MinecraftProfile.getDefaultProfile() : profiles.get(0);
        }
    }

    private static File findLatestWorld(MinecraftProfile profile) {
        File[] worlds = listWorlds(profile);
        if (worlds == null || worlds.length == 0) {
            return null;
        }
        File latest = worlds[0];
        for (File world : worlds) {
            if (world.lastModified() > latest.lastModified()) {
                latest = world;
            }
        }
        return latest;
    }

    private static File[] listWorlds(MinecraftProfile profile) {
        File saves = new File(Tools.getGameDirPath(profile), "saves");
        File[] worlds = saves.listFiles(File::isDirectory);
        return worlds == null ? new File[0] : worlds;
    }

    private static long folderSize(File file, int maxFiles) {
        if (file == null || !file.exists() || maxFiles <= 0) {
            return 0;
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0;
        File[] children = file.listFiles();
        if (children == null) {
            return 0;
        }
        int remaining = maxFiles;
        for (File child : children) {
            if (remaining-- <= 0) break;
            total += folderSize(child, remaining);
        }
        return total;
    }

    private static void applyLocalBoost(Context context) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF == null
                ? PreferenceManager.getDefaultSharedPreferences(context)
                : LauncherPreferences.DEFAULT_PREF;
        int currentResolution = prefs.getInt("resolutionRatio", 100);
        prefs.edit()
                .putBoolean("sustainedPerformance", true)
                .putBoolean("force_vsync", false)
                .putInt("resolutionRatio", Math.min(currentResolution, 80))
                .apply();
        LauncherPreferences.loadPreferences(context);
    }

    private static File zipWorld(Context context, File world) throws Exception {
        File outputDir = new File(context.getCacheDir(), "battly-drive-backups");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("Unable to prepare backup cache");
        }
        File archive = new File(outputDir, sanitizeFileName(world.getName()) + "-" + System.currentTimeMillis() + ".zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(archive)))) {
            zipFileRecursive(world, world.getName(), zipOutputStream);
        }
        return archive;
    }

    private static WorldArchive zipAllProfileWorlds(Context context) throws Exception {
        File outputDir = new File(context.getCacheDir(), "battly-drive-backups");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("Unable to prepare backup cache");
        }
        File archive = new File(outputDir, "BattlyMobile_AllWorlds_" + System.currentTimeMillis() + ".zip");
        JSONArray index = new JSONArray();
        int profileCount = 0;
        int worldCount = 0;
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(archive)))) {
            for (MinecraftProfile profile : getAvailableProfiles()) {
                File[] worlds = listWorlds(profile);
                if (worlds.length == 0) {
                    continue;
                }
                profileCount++;
                String profileName = Tools.isValidString(profile.name) ? profile.name : "Default";
                String profileFolder = "profiles/" + sanitizeFileName(profileName);
                JSONObject profileIndex = new JSONObject();
                profileIndex.put("name", profileName);
                profileIndex.put("minecraftVersion", safe(profile.lastVersionId));
                profileIndex.put("gameDir", safe(profile.gameDir));
                JSONArray worldIndex = new JSONArray();
                for (File world : worlds) {
                    if (world == null || !world.exists()) continue;
                    worldCount++;
                    String worldFolder = profileFolder + "/worlds/" + sanitizeFileName(world.getName());
                    JSONObject worldInfo = new JSONObject();
                    worldInfo.put("name", world.getName());
                    worldInfo.put("path", worldFolder);
                    worldInfo.put("lastModified", world.lastModified());
                    worldInfo.put("sizeBytes", folderSize(world, 10000));
                    worldIndex.put(worldInfo);
                    zipFileRecursive(world, worldFolder, zipOutputStream);
                }
                profileIndex.put("worlds", worldIndex);
                index.put(profileIndex);
            }

            JSONObject rootIndex = new JSONObject();
            rootIndex.put("schema", "battly-mobile-world-backup-v1");
            rootIndex.put("createdAt", System.currentTimeMillis());
            rootIndex.put("profileCount", profileCount);
            rootIndex.put("worldCount", worldCount);
            rootIndex.put("profiles", index);
            zipString("index.json", rootIndex.toString(2), zipOutputStream);
        }
        return new WorldArchive(archive, profileCount, worldCount);
    }

    private static void zipString(String entryName, String content, ZipOutputStream zipOutputStream) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static void zipFileRecursive(File file, String entryName, ZipOutputStream zipOutputStream) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
        String normalizedName = entryName.replace('\\', '/');
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null || children.length == 0) {
                zipOutputStream.putNextEntry(new ZipEntry(normalizedName + "/"));
                zipOutputStream.closeEntry();
                return;
            }
            for (File child : children) {
                zipFileRecursive(child, normalizedName + "/" + child.getName(), zipOutputStream);
            }
            return;
        }
        zipOutputStream.putNextEntry(new ZipEntry(normalizedName));
        byte[] buffer = new byte[16384];
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                zipOutputStream.write(buffer, 0, read);
            }
        }
        zipOutputStream.closeEntry();
    }

    private static final class WorldArchive {
        final File archive;
        final int profileCount;
        final int worldCount;

        WorldArchive(File archive, int profileCount, int worldCount) {
            this.archive = archive;
            this.profileCount = profileCount;
            this.worldCount = worldCount;
        }
    }

    private static JSONObject uploadArchiveToDrive(Context context, Account account, File archive,
                                                   String worldName, boolean retryOnAuthFailure) throws Exception {
        String token = GoogleAuthUtil.getToken(context, account, "oauth2:" + DRIVE_FILE_SCOPE);
        String boundary = "BattlyDriveBoundary" + System.currentTimeMillis();
        JSONObject metadata = new JSONObject();
        metadata.put("name", "BattlyMobile_" + sanitizeFileName(worldName) + ".zip");
        metadata.put("mimeType", "application/zip");
        JSONObject appProperties = new JSONObject();
        appProperties.put("source", "Battly Mobile");
        appProperties.put("kind", "minecraft_world_backup");
        metadata.put("appProperties", appProperties);

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink,webContentLink,size"
        ).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);

        try (OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream())) {
            writeUtf8(outputStream, "--" + boundary + "\r\n");
            writeUtf8(outputStream, "Content-Type: application/json; charset=UTF-8\r\n\r\n");
            writeUtf8(outputStream, metadata.toString());
            writeUtf8(outputStream, "\r\n--" + boundary + "\r\n");
            writeUtf8(outputStream, "Content-Type: application/zip\r\n\r\n");
            byte[] buffer = new byte[16384];
            try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(archive))) {
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
            writeUtf8(outputStream, "\r\n--" + boundary + "--\r\n");
        }

        int code = connection.getResponseCode();
        InputStream inputStream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readFully(inputStream);
        connection.disconnect();
        JSONObject json = Tools.isValidString(response) ? new JSONObject(response) : new JSONObject();
        if (code >= 400) {
            if (retryOnAuthFailure && (code == 401 || code == 403)) {
                GoogleAuthUtil.clearToken(context, token);
                return uploadArchiveToDrive(context, account, archive, worldName, false);
            }
            JSONObject error = json.optJSONObject("error");
            throw new IllegalStateException(error == null
                    ? "Google Drive HTTP " + code
                    : error.optString("message", "Google Drive HTTP " + code));
        }
        return json;
    }

    private static void writeUtf8(OutputStream outputStream, String value) throws Exception {
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitizeFileName(String value) {
        String cleaned = safe(value).replaceAll("[^a-zA-Z0-9._-]+", "_");
        return Tools.isValidString(cleaned) ? cleaned : "world";
    }

    private static JSONObject post(Context context, String path, JSONObject body) throws Exception {
        List<String> tokens = BattlyPlusManager.getTokenCandidates(context);
        if (tokens.isEmpty()) {
            throw new IllegalStateException(context.getString(R.string.battlyworlds_invite_login_required));
        }
        ApiResult lastResult = null;
        for (String token : tokens) {
            ApiResult result = executePost(context, token, path, body);
            lastResult = result;
            Log.i(TAG, "POST " + API_BASE + path + " -> HTTP " + result.code + " token=" + tokenHint(token)
                    + " response=" + shortResponse(result.response));
            if (result.code == 401 || result.code == 403) {
                continue;
            }
            return parseSuccessfulApiResult(context, result);
        }
        if (lastResult != null) {
            return parseSuccessfulApiResult(context, lastResult);
        }
        throw new IllegalStateException(context.getString(R.string.battly_plus_api_error, 0));
    }

    private static JSONObject get(Context context, String path) throws Exception {
        List<String> tokens = BattlyPlusManager.getTokenCandidates(context);
        if (tokens.isEmpty()) {
            throw new IllegalStateException(context.getString(R.string.battlyworlds_invite_login_required));
        }
        ApiResult lastResult = null;
        for (String token : tokens) {
            ApiResult result = executeGet(context, token, path);
            lastResult = result;
            Log.i(TAG, "GET " + API_BASE + path + " -> HTTP " + result.code + " token=" + tokenHint(token)
                    + " response=" + shortResponse(result.response));
            if (result.code == 401 || result.code == 403) {
                continue;
            }
            return parseSuccessfulApiResult(context, result);
        }
        if (lastResult != null) {
            return parseSuccessfulApiResult(context, lastResult);
        }
        throw new IllegalStateException(context.getString(R.string.battly_plus_api_error, 0));
    }

    private static JSONObject getPublic(Context context, String path) throws Exception {
        ApiResult result = executePublicGet(path);
        Log.i(TAG, "GET " + API_BASE + path + " -> HTTP " + result.code
                + " response=" + shortResponse(result.response));
        return parseSuccessfulApiResult(context, result);
    }

    private static ApiResult executePost(Context context, String token, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        connection.setRequestMethod("POST");
        applyApiHeaders(connection, token);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setDoOutput(true);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bytes);
        }
        return readApiResult(connection);
    }

    private static ApiResult executeGet(Context context, String token, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        connection.setRequestMethod("GET");
        applyApiHeaders(connection, token);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        return readApiResult(connection);
    }

    private static ApiResult executePublicGet(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        return readApiResult(connection);
    }

    private static void applyApiHeaders(HttpURLConnection connection, String token) {
        connection.setRequestProperty("Authorization", "Bearer " + token);
    }

    private static ApiResult readApiResult(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        InputStream inputStream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readFully(inputStream);
        connection.disconnect();
        return new ApiResult(code, response);
    }

    private static JSONObject parseSuccessfulApiResult(Context context, ApiResult result) throws Exception {
        JSONObject json = parseApiResponse(context, result.code, result.response);
        if (result.code >= 400 || !json.optBoolean("ok", false)) {
            throw new IllegalStateException(json.optString("error",
                    context.getString(R.string.battly_plus_api_error, result.code)));
        }
        return json;
    }

    private static JSONObject parseApiResponse(Context context, int code, String response) throws Exception {
        if (!Tools.isValidString(response)) {
            return new JSONObject();
        }
        String trimmed = response.trim();
        if (!trimmed.startsWith("{")) {
            throw new IllegalStateException(context.getString(R.string.battly_plus_api_error, code));
        }
        return new JSONObject(trimmed);
    }

    private static String readFully(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String tokenHint(String token) {
        if (!Tools.isValidString(token)) {
            return "empty";
        }
        int tailStart = Math.max(0, token.length() - 6);
        return "len=" + token.length() + ",tail=" + token.substring(tailStart);
    }

    private static String shortResponse(String response) {
        if (!Tools.isValidString(response)) {
            return "";
        }
        String compact = response.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() <= 900 ? compact : compact.substring(0, 900) + "...";
    }

    private static String sha1(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] buffer = new byte[8192];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (byte b : digest.digest()) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static int curseForgeFingerprint(File file) throws Exception {
        byte[] source = new byte[(int) Math.min(file.length(), Integer.MAX_VALUE)];
        int offset = 0;
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read;
            while (offset < source.length && (read = inputStream.read(source, offset, source.length - offset)) != -1) {
                offset += read;
            }
        }
        byte[] normalized = new byte[offset];
        int length = 0;
        for (int i = 0; i < offset; i++) {
            byte value = source[i];
            if (value != 9 && value != 10 && value != 13 && value != 32) {
                normalized[length++] = value;
            }
        }
        return murmurHash2(normalized, length, 1);
    }

    private static int murmurHash2(byte[] data, int length, int seed) {
        final int m = 0x5bd1e995;
        final int r = 24;
        int h = seed ^ length;
        int len4 = length >> 2;
        for (int i = 0; i < len4; i++) {
            int index = i << 2;
            int k = (data[index] & 0xff)
                    | ((data[index + 1] & 0xff) << 8)
                    | ((data[index + 2] & 0xff) << 16)
                    | ((data[index + 3] & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }
        int index = len4 << 2;
        switch (length - index) {
            case 3:
                h ^= (data[index + 2] & 0xff) << 16;
            case 2:
                h ^= (data[index + 1] & 0xff) << 8;
            case 1:
                h ^= (data[index] & 0xff);
                h *= m;
            default:
                break;
        }
        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h;
    }

    private static String getDeviceName() {
        return safe(Build.MANUFACTURER) + " " + safe(Build.MODEL);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ApiResult {
        final int code;
        final String response;

        ApiResult(int code, String response) {
            this.code = code;
            this.response = response;
        }
    }
}
