package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;

final class ModInstallHelper {
    private ModInstallHelper() {
    }

    static void installContent(Context context, ModDetail modDetail, int selectedVersion) throws IOException {
        installContent(context, modDetail, selectedVersion, null);
    }

    static void installContent(Context context,
                               ModDetail modDetail,
                               int selectedVersion,
                               MinecraftProfile targetProfile) throws IOException {
        File targetDirectory = getTargetDirectory(modDetail.contentType, targetProfile);
        FileUtils.ensureDirectory(targetDirectory);

        String url = modDetail.versionUrls[selectedVersion];
        String fileName = modDetail.versionFileNames[selectedVersion];
        if (fileName == null || fileName.isEmpty()) {
            fileName = Uri.parse(url).getLastPathSegment();
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IOException("Unable to resolve a destination filename for " + modDetail.title);
        }

        ModDownloader modDownloader = new ModDownloader(targetDirectory);
        modDownloader.submitDownload(1, fileName, modDetail.versionHashes[selectedVersion], url);
        DownloaderProgressWrapper progressWrapper = new DownloaderProgressWrapper(
                R.string.content_download_downloading,
                ProgressLayout.INSTALL_MODPACK
        );
        progressWrapper.extraString = modDetail.title;
        try {
            modDownloader.awaitFinish(progressWrapper);
            Telemetry.logContentInstall(modDetail.contentType, modDetail.title, true, null);
            Tools.runOnUiThread(() -> Toast.makeText(
                    context,
                    context.getString(getSuccessString(modDetail.contentType), modDetail.title),
                    Toast.LENGTH_SHORT
            ).show());
        } catch (IOException e) {
            Telemetry.logContentInstall(modDetail.contentType, modDetail.title, false, e);
            throw e;
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private static File getTargetDirectory(int contentType, MinecraftProfile targetProfile) {
        LauncherProfiles.load();
        File gameDir = Tools.getGameDirPath(targetProfile == null ? LauncherProfiles.getCurrentProfile() : targetProfile);
        switch (contentType) {
            case SearchFilters.TYPE_RESOURCEPACK:
                return new File(gameDir, "resourcepacks");
            case SearchFilters.TYPE_SHADER:
                return new File(gameDir, "shaderpacks");
            case SearchFilters.TYPE_DATAPACK:
                return new File(gameDir, "datapacks");
            case SearchFilters.TYPE_MOD:
            default:
                return new File(gameDir, "mods");
        }
    }

    private static int getSuccessString(int contentType) {
        switch (contentType) {
            case SearchFilters.TYPE_RESOURCEPACK:
                return R.string.resourcepack_install_success;
            case SearchFilters.TYPE_SHADER:
                return R.string.shader_install_success;
            case SearchFilters.TYPE_DATAPACK:
                return R.string.datapack_install_success;
            case SearchFilters.TYPE_MOD:
            default:
                return R.string.mod_install_success;
        }
    }
}
