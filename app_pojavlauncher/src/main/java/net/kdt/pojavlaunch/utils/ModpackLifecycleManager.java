package net.kdt.pojavlaunch.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.NotificationDownloadListener;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transactional update support for API-installed modpacks. */
public final class ModpackLifecycleManager {
    private static final Set<String> PERSONAL_PATHS = new HashSet<>(Arrays.asList(
            "saves", "screenshots", "resourcepacks", "shaderpacks", "servers.dat",
            "options.txt", "optionsof.txt", "optionsshaders.txt", "journeymap", "XaeroWaypoints"));

    private ModpackLifecycleManager() {
    }

    public static boolean isManaged(MinecraftProfile profile) {
        return profile != null && Tools.isValidString(profile.sourceProjectId)
                && ("modrinth".equals(profile.sourceProvider) || "curseforge".equals(profile.sourceProvider));
    }

    @NonNull
    public static UpdateInfo check(@NonNull Context context, @NonNull MinecraftProfile profile) throws IOException {
        if (!isManaged(profile)) return UpdateInfo.unmanaged();
        int source = "modrinth".equals(profile.sourceProvider)
                ? Constants.SOURCE_MODRINTH : Constants.SOURCE_CURSEFORGE;
        CommonApi api = new CommonApi(context.getString(R.string.curseforge_api_key));
        ModItem item = api.getModBySource(source, SearchFilters.TYPE_MODPACK, profile.sourceProjectId);
        if (item == null) throw new IOException("The modpack project is no longer available");
        ModDetail detail = api.getModDetails(item);
        if (detail == null || detail.versionUrls == null || detail.versionUrls.length == 0) {
            throw new IOException("No downloadable modpack versions were returned");
        }
        String minecraft = minecraftVersion(profile.lastVersionId);
        int selected = selectVersion(detail, minecraft);
        String candidateId = value(detail.versionFileNames, selected);
        String candidateHash = value(detail.versionHashes, selected);
        boolean update = !same(profile.sourceVersionId, candidateId)
                || Tools.isValidString(candidateHash) && !same(profile.sourceHash, candidateHash);
        return new UpdateInfo(true, update, detail, selected,
                value(detail.versionNames, selected), minecraft);
    }

    @NonNull
    public static ModLoader update(@NonNull Context context, @NonNull String profileKey,
                                   @NonNull UpdateInfo update) throws IOException {
        if (!update.available || update.detail == null) throw new IOException("No modpack update is available");
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
        if (profile == null || !isManaged(profile)) throw new IOException("This instance is not a managed modpack");
        File destination = Tools.getGameDirPath(profile);
        File customRoot = new File(Tools.DIR_GAME_HOME, "custom_instances").getCanonicalFile();
        if (!destination.getCanonicalPath().startsWith(customRoot.getPath() + File.separator)) {
            throw new IOException("Managed updates are restricted to isolated Battly instances");
        }

        InstanceManager.createSnapshot(profileKey, "before-modpack-update");
        File archive = new File(Tools.DIR_CACHE, "modpack-update-" + UUID.randomUUID() + ".zip");
        File staging = new File(customRoot, ".update-" + UUID.randomUUID());
        File backup = new File(customRoot, ".previous-" + UUID.randomUUID());
        ModLoader loader;
        try {
            DownloadUtils.ensureSha1(archive, value(update.detail.versionHashes, update.selectedVersion), () -> {
                DownloadUtils.downloadFile(value(update.detail.versionUrls, update.selectedVersion), archive);
                return null;
            });
            if (!staging.mkdirs()) throw new IOException("Unable to create modpack update staging directory");
            if (update.detail.apiSource == Constants.SOURCE_MODRINTH) {
                loader = new ModrinthApi().installPackArchive(archive, staging);
            } else {
                loader = new CurseforgeApi(context.getString(R.string.curseforge_api_key))
                        .installPackArchive(archive, staging);
            }
            if (loader == null) throw new IOException("The updated modpack manifest is invalid");
            preservePersonalData(destination, staging);
            if (destination.exists() && !destination.renameTo(backup)) {
                throw new IOException("Unable to prepare the current instance for update");
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination);
                throw new IOException("Unable to activate the updated instance");
            }

            profile.lastVersionId = loader.getVersionId();
            profile.sourceVersionId = value(update.detail.versionFileNames, update.selectedVersion);
            profile.sourceVersionName = value(update.detail.versionNames, update.selectedVersion);
            profile.sourceDownloadUrl = value(update.detail.versionUrls, update.selectedVersion);
            profile.sourceHash = value(update.detail.versionHashes, update.selectedVersion);
            profile.battlyUpdatedAt = System.currentTimeMillis();
            ModpackInstaller.writeManagedManifest(destination, profile);
            LauncherProfiles.write();
            if (backup.exists()) FileUtils.deleteDirectory(backup);

            Runnable loaderTask = loader.getDownloadTask(context,
                    new NotificationDownloadListener(context, loader));
            if (loaderTask != null) loaderTask.run();
            return loader;
        } catch (IOException | RuntimeException exception) {
            if (!destination.exists() && backup.exists()) backup.renameTo(destination);
            throw exception;
        } finally {
            archive.delete();
            if (staging.exists()) FileUtils.deleteQuietly(staging);
        }
    }

    private static int selectVersion(ModDetail detail, String minecraft) {
        if (detail.versionNames != null && Tools.isValidString(minecraft)) {
            for (int i = 0; i < detail.versionNames.length; i++) {
                if (detail.supportsMinecraftVersion(i, minecraft)) return i;
            }
        }
        return 0;
    }

    private static String minecraftVersion(String versionId) {
        if (versionId == null) return null;
        Matcher matcher = Pattern.compile("(?:^|[^0-9])(1\\.\\d+(?:\\.\\d+)?)").matcher(versionId);
        return matcher.find() ? matcher.group(1) : versionId;
    }

    private static void preservePersonalData(File current, File staging) throws IOException {
        if (!current.isDirectory()) return;
        for (String name : PERSONAL_PATHS) {
            File source = new File(current, name);
            if (!source.exists()) continue;
            File target = new File(staging, name);
            if (source.isDirectory()) FileUtils.copyDirectory(source, target);
            else FileUtils.copyFile(source, target);
        }
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String value(String[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : null;
    }

    public static final class UpdateInfo {
        public final boolean managed;
        public final boolean available;
        public final ModDetail detail;
        public final int selectedVersion;
        public final String versionName;
        public final String minecraftVersion;

        UpdateInfo(boolean managed, boolean available, ModDetail detail, int selectedVersion,
                   String versionName, String minecraftVersion) {
            this.managed = managed;
            this.available = available;
            this.detail = detail;
            this.selectedVersion = selectedVersion;
            this.versionName = versionName;
            this.minecraftVersion = minecraftVersion;
        }

        static UpdateInfo unmanaged() {
            return new UpdateInfo(false, false, null, -1, null, null);
        }
    }
}
