package net.kdt.pojavlaunch.modloaders;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.DependentLibrary;
import net.kdt.pojavlaunch.value.MinecraftLibraryArtifact;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModloaderInstallUtils {
    private static final String DEFAULT_LIBRARY_REPOSITORY = "https://libraries.minecraft.net/";
    private static final String BOOTSTRAPPER_ASSET = "plugin/installer/forge-install-bootstrapper.jar";
    private static final String LAUNCHER_PROFILES_ASSET = "launcher_profiles.json";

    private ModloaderInstallUtils() {
    }

    public static File createWorkspace(String name) throws IOException {
        File workspace = new File(Tools.DIR_CACHE, "modloader_install/" + name);
        if (workspace.exists()) {
            org.apache.commons.io.FileUtils.deleteDirectory(workspace);
        }
        FileUtils.ensureDirectory(workspace);
        return workspace;
    }

    public static void extractZip(File zipFile, File destination) throws IOException {
        FileUtils.ensureDirectory(destination);
        try (ZipFile archive = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File target = new File(destination, entry.getName());
                if (entry.isDirectory()) {
                    FileUtils.ensureDirectory(target);
                    continue;
                }
                FileUtils.ensureParentDirectory(target);
                try (InputStream inputStream = archive.getInputStream(entry);
                     FileOutputStream outputStream = new FileOutputStream(target)) {
                    IOUtils.copy(inputStream, outputStream);
                }
            }
        }
    }

    public static void downloadLibraries(DependentLibrary[] libraries) throws IOException {
        downloadLibraries(libraries, null);
    }

    public static void downloadLibraries(DependentLibrary[] libraries, String skippedArtifactPath) throws IOException {
        if (libraries == null) {
            return;
        }
        Tools.preProcessLibraries(libraries);
        for (DependentLibrary library : libraries) {
            if (library == null || library.name == null) {
                continue;
            }
            if (library.name.startsWith("org.lwjgl")) {
                continue;
            }
            if (library.name.contains("lwjgl-platform-2.9.1-nightly-20130708-debug3")) {
                continue;
            }

            String artifactPath = artifactPath(library);
            if (artifactPath == null || artifactPath.equals(skippedArtifactPath)) {
                continue;
            }

            String url = libraryUrl(library, artifactPath);
            if (url == null) {
                continue;
            }

            String sha1 = librarySha1(library);
            File targetFile = new File(Tools.DIR_HOME_LIBRARY, artifactPath);
            DownloadUtils.ensureSha1(targetFile, sha1, () -> {
                DownloadUtils.downloadFile(url, targetFile);
                return null;
            });
        }
    }

    public static String libraryUrl(DependentLibrary library, String artifactPath) {
        if (library.downloads != null &&
                library.downloads.artifact != null) {
            if (Tools.isValidString(library.downloads.artifact.url)) {
                return library.downloads.artifact.url;
            }
            // Forge/NeoForge installers may declare artifacts that are generated locally later.
            if (library.downloads.artifact.url != null && library.downloads.artifact.url.isEmpty()) {
                return null;
            }
        }
        if (Tools.isValidString(library.url)) {
            return library.url.replace("http://", "https://") + artifactPath;
        }
        return DEFAULT_LIBRARY_REPOSITORY + artifactPath;
    }

    public static String librarySha1(DependentLibrary library) {
        if (library.downloads == null || library.downloads.artifact == null) {
            return null;
        }
        return library.downloads.artifact.sha1;
    }

    public static String artifactPath(DependentLibrary library) {
        if (library == null || !Tools.isValidString(library.name)) {
            return null;
        }
        if (library.downloads != null &&
                library.downloads.artifact != null &&
                Tools.isValidString(library.downloads.artifact.path)) {
            return library.downloads.artifact.path;
        }
        return artifactPathFromDescriptor(library.name);
    }

    public static String artifactPathFromDescriptor(String descriptor) {
        if (!Tools.isValidString(descriptor)) {
            return null;
        }
        String[] descriptorParts = descriptor.split(":", 4);
        if (descriptorParts.length < 3) {
            return null;
        }

        String extension = "jar";
        int versionIndex = 2;
        String classifier = null;

        String[] versionAndExtension = descriptorParts[versionIndex].split("@", 2);
        String version = versionAndExtension[0];
        if (versionAndExtension.length == 2) {
            extension = versionAndExtension[1];
        }

        if (descriptorParts.length == 4) {
            String[] classifierAndExtension = descriptorParts[3].split("@", 2);
            classifier = classifierAndExtension[0];
            if (classifierAndExtension.length == 2) {
                extension = classifierAndExtension[1];
            }
        }

        String group = descriptorParts[0].replace('.', '/');
        String artifact = descriptorParts[1];
        StringBuilder fileName = new StringBuilder(artifact).append('-').append(version);
        if (Tools.isValidString(classifier)) {
            fileName.append('-').append(classifier);
        }
        fileName.append('.').append(extension);
        return group + "/" + artifact + "/" + version + "/" + fileName;
    }

    public static void writeVersionJson(String versionId, String json) throws IOException {
        File versionDir = new File(Tools.DIR_HOME_VERSION, versionId);
        FileUtils.ensureDirectory(versionDir);
        Tools.write(new File(versionDir, versionId + ".json").getAbsolutePath(), json);
    }

    public static void ensureMinecraftDirectory(Context context) throws IOException {
        FileUtils.ensureDirectory(new File(Tools.DIR_GAME_NEW));
        FileUtils.ensureDirectory(new File(Tools.DIR_HOME_VERSION));
        FileUtils.ensureDirectory(new File(Tools.DIR_HOME_LIBRARY));
        Tools.copyAssetFile(context, LAUNCHER_PROFILES_ASSET, Tools.DIR_GAME_NEW, false);
    }

    public static String upsertProfile(String name, String versionId, String icon, String gameDir) {
        LauncherProfiles.load();
        MinecraftProfile profile = null;
        String profileKey = null;
        for (java.util.Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile currentProfile = entry.getValue();
            if (versionId.equals(currentProfile.lastVersionId)) {
                profile = currentProfile;
                profileKey = entry.getKey();
                break;
            }
        }

        if (profile == null) {
            profile = new MinecraftProfile();
            profileKey = LauncherProfiles.getFreeProfileKey();
            LauncherProfiles.mainProfileJson.profiles.put(profileKey, profile);
        }

        profile.name = name;
        profile.lastVersionId = versionId;
        profile.icon = icon;
        if (gameDir != null) {
            profile.gameDir = gameDir;
        }
        LauncherProfiles.write();
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                .apply();
        return profileKey;
    }

    public static String ensureForgeBootstrapper(Context context) throws IOException {
        File outputDir = new File(Tools.DIR_DATA, "installer");
        Tools.copyAssetFile(context, BOOTSTRAPPER_ASSET, outputDir.getAbsolutePath(), true);
        return new File(outputDir, "forge-install-bootstrapper.jar").getAbsolutePath();
    }

    public static int getJavaVersion(File jarFile) throws IOException {
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            ZipEntry manifest = zipFile.getEntry("META-INF/MANIFEST.MF");
            if (manifest == null) {
                return -1;
            }

            String manifestString = Tools.read(zipFile.getInputStream(manifest));
            String mainClass = Tools.extractUntilCharacter(manifestString, "Main-Class:", '\n');
            if (mainClass == null) {
                return -1;
            }

            String classPath = mainClass.trim().replace('.', '/') + ".class";
            ZipEntry classEntry = zipFile.getEntry(classPath);
            if (classEntry == null) {
                return -1;
            }

            try (InputStream classStream = zipFile.getInputStream(classEntry)) {
                byte[] classHeader = new byte[8];
                if (classStream.read(classHeader) < 8) {
                    return -1;
                }
                ByteBuffer byteBuffer = ByteBuffer.wrap(classHeader);
                if (byteBuffer.getInt() != 0xCAFEBABE) {
                    return -1;
                }
                byteBuffer.getShort();
                short majorVersion = byteBuffer.getShort();
                return majorVersion < 46 ? 2 : majorVersion - 44;
            }
        }
    }

    public static void validateInstallerJar(File jarFile) throws IOException {
        if (jarFile == null || !jarFile.isFile() || jarFile.length() == 0) {
            throw new IOException("Installer download is empty");
        }
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            ZipEntry manifest = zipFile.getEntry("META-INF/MANIFEST.MF");
            ZipEntry installProfile = zipFile.getEntry("install_profile.json");
            if (manifest == null || installProfile == null) {
                throw new IOException("Installer archive is missing required metadata");
            }
            // Reading both entries forces ZipFile to validate their local headers as well as CEN.
            try (InputStream manifestStream = zipFile.getInputStream(manifest);
                 InputStream profileStream = zipFile.getInputStream(installProfile)) {
                if (manifestStream.read() < 0 || profileStream.read() < 0) {
                    throw new IOException("Installer archive contains empty metadata");
                }
            }
        } catch (java.util.zip.ZipException exception) {
            throw new IOException("Downloaded installer archive is corrupt", exception);
        }
    }

    public static String selectRuntimeForJar(File jarFile, int fallbackVersion) throws IOException {
        int javaVersion = getJavaVersion(jarFile);
        if (javaVersion < 0) {
            javaVersion = fallbackVersion;
        }
        return MultiRTUtils.getNearestJreName(javaVersion);
    }

    public static String findInstalledVersion(String preferredId, String prefix) {
        File preferredJson = new File(Tools.DIR_HOME_VERSION, preferredId + "/" + preferredId + ".json");
        if (preferredJson.exists()) {
            return preferredId;
        }

        File versionsDir = new File(Tools.DIR_HOME_VERSION);
        File[] candidates = versionsDir.listFiles();
        if (candidates == null) {
            return preferredId;
        }

        File latestMatch = null;
        for (File candidate : candidates) {
            if (!candidate.isDirectory() || !candidate.getName().startsWith(prefix)) {
                continue;
            }
            File versionJson = new File(candidate, candidate.getName() + ".json");
            if (!versionJson.exists()) {
                continue;
            }
            if (latestMatch == null || candidate.lastModified() > latestMatch.lastModified()) {
                latestMatch = candidate;
            }
        }
        return latestMatch == null ? preferredId : latestMatch.getName();
    }

    public static String findInstalledForgeVersionForMinecraft(String minecraftVersion) {
        File versionsDir = new File(Tools.DIR_HOME_VERSION);
        File[] candidates = versionsDir.listFiles();
        if (candidates == null) {
            return null;
        }

        File latestMatch = null;
        String prefix = minecraftVersion + "-forge-";
        for (File candidate : candidates) {
            if (!candidate.isDirectory() || !candidate.getName().startsWith(prefix)) {
                continue;
            }
            File versionJson = new File(candidate, candidate.getName() + ".json");
            if (!versionJson.exists()) {
                continue;
            }
            if (latestMatch == null || candidate.lastModified() > latestMatch.lastModified()) {
                latestMatch = candidate;
            }
        }
        return latestMatch == null ? null : latestMatch.getName();
    }

    public static DependentLibrary createLibrary(String name, String path, String url) {
        DependentLibrary dependentLibrary = new DependentLibrary();
        dependentLibrary.name = name;
        MinecraftLibraryArtifact artifact = new MinecraftLibraryArtifact();
        artifact.path = path;
        artifact.url = url;
        dependentLibrary.downloads = new DependentLibrary.LibraryDownloads(artifact);
        return dependentLibrary;
    }

    public static DependentLibrary[] librariesFromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new DependentLibrary[0];
        }
        JsonArray array = element.getAsJsonArray();
        return Tools.GLOBAL_GSON.fromJson(array, DependentLibrary[].class);
    }
}
