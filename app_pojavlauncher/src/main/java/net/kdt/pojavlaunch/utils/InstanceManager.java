package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** File-safe lifecycle operations for Battly instances backed by launcher profiles. */
public final class InstanceManager {
    public static final int SCHEMA_VERSION = 1;
    private static final String MANIFEST = "battly-instance.json";
    private static final long MAX_IMPORT_BYTES = 12L * 1024L * 1024L * 1024L;
    private static final long SIZE_CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final Map<String, DirectorySizeCache> DIRECTORY_SIZE_CACHE = new ConcurrentHashMap<>();

    private InstanceManager() {
    }

    public static synchronized List<InstanceRecord> list() {
        LauncherProfiles.load();
        List<InstanceRecord> records = new ArrayList<>();
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            if (entry.getValue() != null) records.add(new InstanceRecord(entry.getKey(), entry.getValue()));
        }
        records.sort(Comparator.comparing(record -> safe(record.profile.name).toLowerCase()));
        return records;
    }

    public static long getCachedDirectorySize(File directory) {
        String key = cacheKey(directory);
        DirectorySizeCache cached = DIRECTORY_SIZE_CACHE.get(key);
        if (cached == null || System.currentTimeMillis() - cached.createdAt > SIZE_CACHE_TTL_MS) {
            DIRECTORY_SIZE_CACHE.remove(key);
            return -1L;
        }
        return cached.sizeBytes;
    }

    public static long calculateDirectorySize(File directory) {
        long size = directorySize(directory);
        DIRECTORY_SIZE_CACHE.put(cacheKey(directory), new DirectorySizeCache(size, System.currentTimeMillis()));
        return size;
    }

    public static void invalidateDirectorySize(File directory) {
        DIRECTORY_SIZE_CACHE.remove(cacheKey(directory));
    }

    public static synchronized String duplicate(@NonNull String profileKey, @NonNull String requestedName)
            throws IOException {
        LauncherProfiles.load();
        MinecraftProfile source = requireProfile(profileKey);
        MinecraftProfile copy = new MinecraftProfile(source);
        String key = LauncherProfiles.getFreeProfileKey();
        copy.name = uniqueName(requestedName, list());
        copy.battlyInstanceId = key;
        copy.battlySchemaVersion = SCHEMA_VERSION;
        copy.battlyCreatedAt = System.currentTimeMillis();
        copy.battlyUpdatedAt = copy.battlyCreatedAt;

        File sourceDir = Tools.getGameDirPath(source);
        File destination = new File(Tools.DIR_GAME_HOME,
                "custom_instances/" + sanitize(copy.name) + "-" + key.substring(0, 8));
        if (sourceDir.isDirectory()) copyTree(sourceDir, destination, CopyMode.INSTANCE);
        invalidateDirectorySize(destination);
        copy.gameDir = destination.getAbsolutePath();
        LauncherProfiles.mainProfileJson.profiles.put(key, copy);
        LauncherProfiles.write();
        return key;
    }

    public static synchronized void rename(@NonNull String profileKey, @NonNull String name) {
        LauncherProfiles.load();
        MinecraftProfile profile = requireProfile(profileKey);
        profile.name = name.trim();
        profile.battlyUpdatedAt = System.currentTimeMillis();
        LauncherProfiles.write();
    }

    public static synchronized void select(@NonNull String profileKey) {
        LauncherProfiles.load();
        requireProfile(profileKey);
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                .apply();
    }

    public static synchronized void delete(@NonNull String profileKey, boolean deleteFiles) throws IOException {
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson.profiles.size() <= 1) {
            throw new IOException("At least one instance must remain");
        }
        MinecraftProfile profile = requireProfile(profileKey);
        File gameDir = Tools.getGameDirPath(profile);
        LauncherProfiles.mainProfileJson.profiles.remove(profileKey);
        String selected = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        if (profileKey.equals(selected)) {
            String fallback = LauncherProfiles.mainProfileJson.profiles.keySet().iterator().next();
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, fallback).apply();
        }
        LauncherProfiles.write();
        if (deleteFiles && isOwnedCustomInstanceDirectory(gameDir)
                && !isDirectoryUsedByAnotherProfile(gameDir, profileKey)) {
            org.apache.commons.io.FileUtils.deleteDirectory(gameDir);
        }
        invalidateDirectorySize(gameDir);
    }

    public static void exportInstance(@NonNull String profileKey, @NonNull OutputStream output) throws IOException {
        LauncherProfiles.load();
        MinecraftProfile profile = requireProfile(profileKey);
        JSONObject manifest = manifest(profileKey, profile);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            writeString(zip, MANIFEST, manifest.toString());
            File gameDir = Tools.getGameDirPath(profile);
            if (gameDir.isDirectory()) zipTree(zip, gameDir, "instance/", CopyMode.INSTANCE);
        }
    }

    public static synchronized String importInstance(@NonNull InputStream input, @NonNull String fallbackName)
            throws IOException {
        File staging = new File(Tools.DIR_GAME_HOME, "custom_instances/.import-" + UUID.randomUUID());
        if (!staging.mkdirs()) throw new IOException("Unable to create import staging directory");
        JSONObject manifest = null;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizeZipName(entry.getName());
                if (MANIFEST.equals(name)) {
                    manifest = new JSONObject(readEntry(zip, 2 * 1024 * 1024));
                    continue;
                }
                if (!name.startsWith("instance/")) continue;
                String relative = name.substring("instance/".length());
                if (relative.isEmpty()) continue;
                File target = safeChild(staging, relative);
                if (entry.isDirectory()) {
                    ensureDirectory(target);
                    continue;
                }
                ensureDirectory(target.getParentFile());
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_IMPORT_BYTES) throw new IOException("Instance archive is too large");
                        out.write(buffer, 0, read);
                    }
                }
            }
        } catch (Exception exception) {
            org.apache.commons.io.FileUtils.deleteDirectory(staging);
            if (exception instanceof IOException) throw (IOException) exception;
            throw new IOException("Invalid Battly instance archive", exception);
        }

        String key = LauncherProfiles.getFreeProfileKey();
        JSONObject profileJson = manifest == null ? null : manifest.optJSONObject("profile");
        MinecraftProfile profile = profileJson == null ? MinecraftProfile.createTemplate()
                : Tools.GLOBAL_GSON.fromJson(profileJson.toString(), MinecraftProfile.class);
        profile.name = uniqueName(firstNonEmpty(profile.name, fallbackName, "Imported instance"), list());
        profile.battlyInstanceId = key;
        profile.battlySchemaVersion = SCHEMA_VERSION;
        profile.battlyCreatedAt = System.currentTimeMillis();
        profile.battlyUpdatedAt = profile.battlyCreatedAt;
        File destination = new File(Tools.DIR_GAME_HOME,
                "custom_instances/" + sanitize(profile.name) + "-" + key.substring(0, 8));
        ensureDirectory(destination.getParentFile());
        if (!staging.renameTo(destination)) {
            copyTree(staging, destination, CopyMode.ALL);
            org.apache.commons.io.FileUtils.deleteDirectory(staging);
        }
        profile.gameDir = destination.getAbsolutePath();
        invalidateDirectorySize(destination);
        LauncherProfiles.mainProfileJson.profiles.put(key, profile);
        LauncherProfiles.write();
        return key;
    }

    public static File createSnapshot(@NonNull String profileKey, @NonNull String reason) throws IOException {
        LauncherProfiles.load();
        MinecraftProfile profile = requireProfile(profileKey);
        File directory = snapshotDirectory(profile);
        ensureDirectory(directory);
        File snapshot = new File(directory, System.currentTimeMillis() + ".zip");
        try (FileOutputStream output = new FileOutputStream(snapshot)) {
            exportInstance(profileKey, output);
        }
        JSONObject meta = new JSONObject();
        try {
            meta.put("reason", reason);
            meta.put("createdAt", System.currentTimeMillis());
            Tools.write(new File(directory, snapshot.getName() + ".json").getAbsolutePath(), meta.toString(2));
        } catch (Exception ignored) {
        }
        pruneSnapshots(directory, 5);
        return snapshot;
    }

    public static List<File> snapshots(@NonNull String profileKey) {
        LauncherProfiles.load();
        File[] files = snapshotDirectory(requireProfile(profileKey))
                .listFiles(file -> file.isFile() && file.getName().endsWith(".zip"));
        List<File> result = new ArrayList<>();
        if (files != null) CollectionsCompat.addAll(result, files);
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    public static void rollback(@NonNull String profileKey, @NonNull File snapshot) throws IOException {
        LauncherProfiles.load();
        MinecraftProfile profile = requireProfile(profileKey);
        createSnapshot(profileKey, "before-rollback");
        File gameDir = Tools.getGameDirPath(profile);
        File backup = new File(gameDir.getParentFile(), gameDir.getName() + ".rollback-" + System.currentTimeMillis());
        if (gameDir.exists() && !gameDir.renameTo(backup)) {
            throw new IOException("Unable to prepare instance rollback");
        }
        String importedKey;
        try (InputStream input = new FileInputStream(snapshot)) {
            importedKey = importInstance(input, profile.name);
        } catch (IOException exception) {
            if (!gameDir.exists()) backup.renameTo(gameDir);
            throw exception;
        }
        MinecraftProfile imported = LauncherProfiles.mainProfileJson.profiles.remove(importedKey);
        File importedDir = Tools.getGameDirPath(imported);
        if (!importedDir.renameTo(gameDir)) {
            copyTree(importedDir, gameDir, CopyMode.ALL);
            org.apache.commons.io.FileUtils.deleteDirectory(importedDir);
        }
        imported.gameDir = profile.gameDir;
        imported.battlyInstanceId = profile.battlyInstanceId;
        imported.name = profile.name;
        imported.battlyUpdatedAt = System.currentTimeMillis();
        LauncherProfiles.mainProfileJson.profiles.put(profileKey, imported);
        LauncherProfiles.write();
        if (backup.exists()) org.apache.commons.io.FileUtils.deleteDirectory(backup);
    }

    private static JSONObject manifest(String key, MinecraftProfile profile) throws IOException {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", "battly-mobile-instance-v1");
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("profileKey", key);
            root.put("exportedAt", System.currentTimeMillis());
            root.put("profile", new JSONObject(Tools.GLOBAL_GSON.toJson(profile)));
            return root;
        } catch (Exception exception) {
            throw new IOException("Unable to serialize instance", exception);
        }
    }

    private static MinecraftProfile requireProfile(String key) {
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
        if (profile == null) throw new IllegalArgumentException("Unknown profile: " + key);
        return profile;
    }

    private static File snapshotDirectory(MinecraftProfile profile) {
        return new File(Tools.DIR_GAME_HOME, ".battly/snapshots/" + sanitize(profile.battlyInstanceId));
    }

    private static void pruneSnapshots(File directory, int keep) {
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".zip"));
        if (files == null || files.length <= keep) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keep; i < files.length; i++) {
            files[i].delete();
            new File(directory, files[i].getName() + ".json").delete();
        }
    }

    private static boolean isDirectoryUsedByAnotherProfile(File gameDir, String excludedKey) {
        try {
            String canonical = gameDir.getCanonicalPath();
            for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
                if (entry.getKey().equals(excludedKey)) continue;
                if (Tools.getGameDirPath(entry.getValue()).getCanonicalPath().equals(canonical)) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static boolean isOwnedCustomInstanceDirectory(File file) throws IOException {
        File root = new File(Tools.DIR_GAME_HOME, "custom_instances");
        String rootPath = root.getCanonicalPath() + File.separator;
        return file.getCanonicalPath().startsWith(rootPath);
    }

    private static String uniqueName(String requested, List<InstanceRecord> existing) {
        String base = firstNonEmpty(requested, "Instance").trim();
        Set<String> names = new HashSet<>();
        for (InstanceRecord record : existing) names.add(safe(record.profile.name).toLowerCase());
        if (!names.contains(base.toLowerCase())) return base;
        int suffix = 2;
        while (names.contains((base + " " + suffix).toLowerCase())) suffix++;
        return base + " " + suffix;
    }

    private static void copyTree(File source, File destination, CopyMode mode) throws IOException {
        if (mode.skip(source.getName(), source.isDirectory())) return;
        if (source.isDirectory()) {
            ensureDirectory(destination);
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) copyTree(child, new File(destination, child.getName()), mode);
            }
        } else {
            ensureDirectory(destination.getParentFile());
            try (InputStream input = new BufferedInputStream(new FileInputStream(source));
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
                copy(input, output);
            }
        }
    }

    private static void zipTree(ZipOutputStream zip, File source, String path, CopyMode mode) throws IOException {
        if (mode.skip(source.getName(), source.isDirectory())) return;
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) zipTree(zip, child, path + child.getName() + (child.isDirectory() ? "/" : ""), mode);
            }
            return;
        }
        zip.putNextEntry(new ZipEntry(path));
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            copy(input, zip);
        }
        zip.closeEntry();
    }

    private static void writeString(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String readEntry(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("Manifest is too large");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    }

    private static File safeChild(File root, String relative) throws IOException {
        File child = new File(root, relative);
        String rootPath = root.getCanonicalPath() + File.separator;
        if (!child.getCanonicalPath().startsWith(rootPath)) throw new IOException("Unsafe archive path");
        return child;
    }

    private static String normalizeZipName(String name) {
        return name == null ? "" : name.replace('\\', '/').replaceFirst("^/+", "");
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create directory: " + directory);
        }
    }

    private static long directorySize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += directorySize(child);
        }
        return total;
    }

    private static String cacheKey(File directory) {
        if (directory == null) return "";
        try {
            return directory.getCanonicalPath();
        } catch (IOException ignored) {
            return directory.getAbsolutePath();
        }
    }

    private static String sanitize(String value) {
        String clean = safe(value).replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return clean.isEmpty() ? "instance" : clean;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (Tools.isValidString(value)) return value;
        return "";
    }

    private enum CopyMode {
        ALL,
        INSTANCE;

        boolean skip(String name, boolean directory) {
            if (this == ALL) return false;
            String lower = name.toLowerCase();
            return directory && ("logs".equals(lower) || "crash-reports".equals(lower)
                    || ".cache".equals(lower) || "webcache".equals(lower));
        }
    }

    private static final class CollectionsCompat {
        static void addAll(List<File> destination, File[] source) {
            java.util.Collections.addAll(destination, source);
        }
    }

    public static final class InstanceRecord {
        public final String key;
        public final MinecraftProfile profile;

        InstanceRecord(String key, MinecraftProfile profile) {
            this.key = key;
            this.profile = profile;
        }

        public File gameDirectory() {
            return Tools.getGameDirPath(profile);
        }
    }

    private static final class DirectorySizeCache {
        final long sizeBytes;
        final long createdAt;

        DirectorySizeCache(long sizeBytes, long createdAt) {
            this.sizeBytes = sizeBytes;
            this.createdAt = createdAt;
        }
    }
}
