package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.net.Uri;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.InstanceDirectoryPolicy;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModpackInstaller {

    public static ModLoader installModpack(ModDetail modDetail, int selectedVersion, InstallFunction installFunction) throws IOException {
        String versionUrl = modDetail.versionUrls[selectedVersion];
        String versionHash = modDetail.versionHashes[selectedVersion];
        String modpackName = (modDetail.title.toLowerCase(Locale.ROOT) + " " + modDetail.versionNames[selectedVersion])
                .trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_" );
        if (versionHash != null) {
            modpackName += "_" + versionHash;
        }
        if (modpackName.length() > 255){
            modpackName = modpackName.substring(0,255);
        }

        String profileKey = LauncherProfiles.getFreeProfileKey();
        String gameDir = InstanceDirectoryPolicy.isolatedGameDir(modDetail.title, profileKey);

        // Get the modpack file
        File modpackFile = new File(Tools.DIR_CACHE, modpackName + ".cf"); // Cache File
        ModLoader modLoaderInfo;
        try {
            byte[] downloadBuffer = new byte[8192];
            DownloadUtils.ensureSha1(modpackFile, versionHash, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(versionUrl, modpackFile, downloadBuffer,
                        new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                ProgressLayout.INSTALL_MODPACK));
                return null;
            });

            // Install the modpack
            modLoaderInfo = installFunction.installModpack(
                    modpackFile, new File(Tools.DIR_GAME_HOME, gameDir));

        } finally {
            modpackFile.delete();
        }
        if(modLoaderInfo == null) {
            return null;
        }

        // Create the instance
        MinecraftProfile profile = new MinecraftProfile();
        profile.gameDir = gameDir;
        profile.name = modDetail.title;
        profile.lastVersionId = modLoaderInfo.getVersionId();
        profile.icon = ModIconCache.getBase64Image(modDetail.getIconCacheTag());

        applySourceMetadata(profile, modDetail, selectedVersion);
        writeManagedManifest(new File(Tools.DIR_GAME_HOME, profile.gameDir), profile);

        profile.battlyInstanceId = profileKey;
        LauncherProfiles.putNewProfile(profileKey, profile);
        LauncherProfiles.write();
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 96,
                R.string.modpack_import_finalizing);

        return modLoaderInfo;
    }

    public static ModLoader importModpack(Activity activity, Uri zipUri, InstallFunction installFunction) throws IOException, NoSuchAlgorithmException {
        String modrinthPackInfoFileName = "modrinth.index.json";
        String curseforgePackInfoFileName = "manifest.json";
        InputStream inputStream = null;
        inputStream = activity.getContentResolver().openInputStream(zipUri);
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        ZipEntry zipEntry;
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            boolean isModrinth = zipEntry.getName().equals(modrinthPackInfoFileName);
            boolean isCurseforge = zipEntry.getName().equals(curseforgePackInfoFileName);
            if (!(isModrinth || isCurseforge)) continue;
            // Read Manifest JSON
            BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream));
            String str;
            StringBuilder jsonString = new StringBuilder();
            while ((str = reader.readLine()) != null) {
                jsonString.append(str).append("\n");
            }
            zipInputStream.close();

            // Hash the ZIP File
            inputStream = activity.getContentResolver().openInputStream(zipUri);
            MessageDigest algorithm = MessageDigest.getInstance("SHA-1");
            DigestInputStream hashingStream = new DigestInputStream(inputStream, algorithm);

            byte[] buffer = new byte[8192];
            while (hashingStream.read(buffer) != -1) {} // just read to update the digest
            hashingStream.close();
            byte[] digest = algorithm.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String hash = sb.toString();

            // Parse the JSON to prepare for instance creation
            JsonObject packInfoJson = JsonParser.parseString(jsonString.toString()).getAsJsonObject();
            String modpackName;
            if(isModrinth){
                // Added a for because there is an awkward __ that I can't be bothered to fix
                // FO only deduplication be like:
                modpackName = (packInfoJson.get("name").getAsString().toLowerCase(Locale.ROOT) +
                        packInfoJson.get("versionId") + "for" +
                        packInfoJson.get("dependencies").getAsJsonObject().get("minecraft"));
            } else {
                modpackName = (packInfoJson.get("name").getAsString().toLowerCase(Locale.ROOT) +
                        packInfoJson.get("version") + "for" +
                        packInfoJson.get("minecraft").getAsJsonObject().get("version"));
            }
            modpackName = modpackName.trim().replaceAll("[\\\\/:*?\"<>| \\t\\n]", "_");
            modpackName = modpackName + hash;

            // Copy ZIP file to cache
            if(modpackName == null) throw new IOException("Corrupt Modpack manifest file.");
            File modpackFile = null;
            modpackFile = new File(Tools.DIR_CACHE, modpackName + ".cf");
            inputStream = activity.getContentResolver().openInputStream(zipUri);
            FileOutputStream output = new FileOutputStream(modpackFile);
            byte[] b = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(b)) != -1) {
                output.write(b, 0, read);
            }
            output.flush();

            String profileKey = LauncherProfiles.getFreeProfileKey();
            String gameDir = InstanceDirectoryPolicy.isolatedGameDir(
                    packInfoJson.get("name").getAsString(), profileKey);

            // Install the actual pack into its own custom_instances directory.
            ModLoader modLoaderInfo = installFunction.installModpack(
                    modpackFile, new File(Tools.DIR_GAME_HOME, gameDir));
            // We have to do this because installModpack doesn't clean up after itself
            modpackFile.delete();
            if(modLoaderInfo == null) {
                return null;
            }

            // Create the instance (We don't have a picture guys)
            MinecraftProfile profile = new MinecraftProfile();
            profile.gameDir = gameDir;
            profile.name = packInfoJson.get("name").getAsString();
            profile.lastVersionId = modLoaderInfo.getVersionId();
            profile.sourceProvider = isModrinth ? "modrinth-local" : "curseforge-local";
            profile.sourceVersionId = isModrinth
                    ? string(packInfoJson, "versionId") : string(packInfoJson, "version");
            profile.sourceVersionName = profile.sourceVersionId;
            profile.sourceHash = hash;
            writeManagedManifest(new File(Tools.DIR_GAME_HOME, profile.gameDir), profile);
            profile.battlyInstanceId = profileKey;
            LauncherProfiles.putNewProfile(profileKey, profile);
            LauncherProfiles.write();
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 96,
                    R.string.modpack_import_finalizing);

            return modLoaderInfo;
        }
        throw new IOException("Can't find manifest file in modpack provided");
}

    private static void applySourceMetadata(MinecraftProfile profile, ModDetail detail, int selectedVersion) {
        profile.sourceProvider = detail.apiSource == Constants.SOURCE_MODRINTH ? "modrinth" : "curseforge";
        profile.sourceProjectId = detail.id;
        profile.sourceVersionId = value(detail.versionFileNames, selectedVersion);
        profile.sourceVersionName = value(detail.versionNames, selectedVersion);
        profile.sourceDownloadUrl = value(detail.versionUrls, selectedVersion);
        profile.sourceHash = value(detail.versionHashes, selectedVersion);
        profile.battlyUpdatedAt = System.currentTimeMillis();
    }

    public static void writeManagedManifest(File instanceDirectory, MinecraftProfile profile) throws IOException {
        File metadataDirectory = new File(instanceDirectory, ".battly");
        if (!metadataDirectory.exists() && !metadataDirectory.mkdirs()) {
            throw new IOException("Unable to create modpack metadata directory");
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schema", "battly-modpack-v1");
        manifest.addProperty("provider", profile.sourceProvider);
        manifest.addProperty("projectId", profile.sourceProjectId);
        manifest.addProperty("versionId", profile.sourceVersionId);
        manifest.addProperty("versionName", profile.sourceVersionName);
        manifest.addProperty("downloadUrl", profile.sourceDownloadUrl);
        manifest.addProperty("hash", profile.sourceHash);
        manifest.addProperty("minecraftVersion", profile.lastVersionId);
        manifest.addProperty("updatedAt", System.currentTimeMillis());
        try (FileWriter writer = new FileWriter(new File(metadataDirectory, "modpack.json"))) {
            Tools.GLOBAL_GSON.toJson(manifest, writer);
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static String value(String[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : null;
    }

interface InstallFunction {
        ModLoader installModpack(File modpackFile, File instanceDestination) throws IOException;
    }
}
