package net.kdt.pojavlaunch.modloaders.modpacks.api;


import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDependency;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchCategory;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.utils.BattlyPlusCloud;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.IOException;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;


/**
 *
 */
public interface ModpackApi {

    /**
     * @param searchFilters Filters
     * @param previousPageResult The result from the previous page
     * @return the list of mod items from specified offset
     */
    SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult);

    /**
     * @param searchFilters Filters
     * @return A list of mod items
     */
    default SearchResult searchMod(SearchFilters searchFilters) {
        return searchMod(searchFilters, null);
    }

    /**
     * Fetch the mod details
     * @param item The moditem that was selected
     * @return Detailed data about a mod(pack)
     */
    ModDetail getModDetails(ModItem item);

    default ModItem getModById(int contentType, String projectId) {
        return null;
    }

    default SearchCategory[] getCategories(SearchFilters searchFilters) {
        return new SearchCategory[0];
    }

    /**
     * Download and install the mod(pack)
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    default void handleInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        handleInstallation(context, modDetail, selectedVersion, null);
    }

    default void handleInstallation(Context context,
                                    ModDetail modDetail,
                                    int selectedVersion,
                                    MinecraftProfile targetProfile) {
        // Doing this here since when starting installation, the progress does not start immediately
        // which may lead to two concurrent installations (very bad)
        boolean plusQueue = BattlyPlusCloud.canUsePremiumQueue(context);
        InstallProgressDialogController blocker = showInstallGate(context, plusQueue);
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0,
                plusQueue ? R.string.battly_plus_install_queue_message : R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (!modDetail.isModpack && modDetail.contentType != SearchFilters.TYPE_MODPACK) {
                    installDependencies(context, modDetail, selectedVersion, targetProfile);
                }
                ModLoader loaderInfo = installMod(context, modDetail, selectedVersion, targetProfile);
                if (loaderInfo == null) return;
                Telemetry.logContentInstall(modDetail.contentType, modDetail.title, true, null);
                loaderInfo.getDownloadTask(context, new NotificationDownloadListener(context, loaderInfo)).run();
            }catch (Exception e) {
                Telemetry.logContentInstall(modDetail.contentType, modDetail.title, false, e);
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            } finally {
                dismissInstallGate(blocker);
            }
        });
    }

    default void handleDependenciesInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        handleDependenciesInstallation(context, modDetail, selectedVersion, null);
    }

    default void handleDependenciesInstallation(Context context,
                                                ModDetail modDetail,
                                                int selectedVersion,
                                                MinecraftProfile targetProfile) {
        boolean plusQueue = BattlyPlusCloud.canUsePremiumQueue(context);
        InstallProgressDialogController blocker = showInstallGate(context, plusQueue);
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0,
                plusQueue ? R.string.battly_plus_install_queue_message : R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                installDependencies(context, modDetail, selectedVersion, targetProfile);
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            } catch (Exception e) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(context, R.string.mod_dependency_install_failed, e);
            } finally {
                dismissInstallGate(blocker);
            }
        });
    }

    @Nullable
    default InstallProgressDialogController showInstallGate(Context context, boolean plusQueue) {
        if (plusQueue) {
            Tools.runOnUiThread(() -> Toast.makeText(
                    context,
                    R.string.battly_plus_install_queue_message,
                    Toast.LENGTH_LONG
            ).show());
            return null;
        }
        return InstallProgressDialogController.show(context);
    }

    default void dismissInstallGate(@Nullable InstallProgressDialogController controller) {
        if (controller == null) {
            return;
        }
        controller.dismiss();
    }

    /**
     * Install the mod(pack).
     * May require the download of additional files.
     * May requires launching the installation of a modloader
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    ModLoader installMod(Context context, ModDetail modDetail, int selectedVersion) throws IOException;

    default ModLoader installMod(Context context,
                                 ModDetail modDetail,
                                 int selectedVersion,
                                 MinecraftProfile targetProfile) throws IOException {
        return installMod(context, modDetail, selectedVersion);
    }

    default void installDependencies(Context context, ModDetail modDetail, int selectedVersion) throws IOException {
        installDependencies(context, modDetail, selectedVersion, null);
    }

    default void installDependencies(Context context,
                                     ModDetail modDetail,
                                     int selectedVersion,
                                     MinecraftProfile targetProfile) throws IOException {
        Set<String> visited = new HashSet<>();
        visited.add(modDetail.apiSource + ":" + modDetail.id);
        int installedCount = installDependenciesRecursive(context, modDetail, selectedVersion, targetProfile, visited);
        if (installedCount > 0) {
            int finalInstalledCount = installedCount;
            Tools.runOnUiThread(() -> Toast.makeText(
                    context,
                    context.getString(R.string.mod_dependency_install_success, finalInstalledCount),
                    Toast.LENGTH_LONG
            ).show());
        }
    }

    default int installDependenciesRecursive(Context context,
                                             ModDetail modDetail,
                                             int selectedVersion,
                                             MinecraftProfile targetProfile,
                                             Set<String> visited) throws IOException {
        if (modDetail.versionDependencies == null
                || selectedVersion < 0
                || selectedVersion >= modDetail.versionDependencies.length) {
            return 0;
        }
        ModDependency[] dependencies = modDetail.versionDependencies[selectedVersion];
        if (dependencies == null || dependencies.length == 0) {
            return 0;
        }

        int installedCount = 0;
        for (ModDependency dependency : dependencies) {
            if (dependency == null || !dependency.required || !Tools.isValidString(dependency.projectId)) {
                continue;
            }

            ProgressLayout.setProgress(
                    ProgressLayout.INSTALL_MODPACK,
                    0,
                    R.string.mod_dependency_installing_progress,
                    dependency.displayName == null ? dependency.projectId : dependency.displayName
            );
            ModItem dependencyItem = getModById(modDetail.contentType, dependency.projectId);
            if (dependencyItem == null) {
                continue;
            }
            String dependencyKey = dependencyItem.apiSource + ":" + dependencyItem.id;
            if (!visited.add(dependencyKey)) {
                continue;
            }

            ModDetail dependencyDetail = getModDetails(dependencyItem);
            if (dependencyDetail == null) {
                continue;
            }

            int compatibleVersion = findCompatibleVersion(modDetail, selectedVersion, dependencyDetail);
            if (compatibleVersion < 0) {
                continue;
            }

            installedCount += installDependenciesRecursive(context, dependencyDetail, compatibleVersion, targetProfile, visited);
            installMod(context, dependencyDetail, compatibleVersion, targetProfile);
            installedCount++;
        }

        return installedCount;
    }

    default int findCompatibleVersion(ModDetail baseDetail, int baseVersion, ModDetail dependencyDetail) {
        String[] targetMcVersions = baseDetail.getGameVersions(baseVersion);
        String[] targetLoaders = baseDetail.versionLoaders == null
                || baseVersion < 0
                || baseVersion >= baseDetail.versionLoaders.length
                ? null
                : baseDetail.versionLoaders[baseVersion];

        for (int i = 0; i < dependencyDetail.versionNames.length; i++) {
            boolean mcCompatible = minecraftVersionsOverlap(
                    targetMcVersions,
                    dependencyDetail.getGameVersions(i)
            );
            if (!mcCompatible) {
                continue;
            }

            if (loadersOverlap(targetLoaders, dependencyDetail.versionLoaders == null ? null : dependencyDetail.versionLoaders[i])) {
                return i;
            }
        }
        return dependencyDetail.versionNames.length == 0 ? -1 : 0;
    }

    default boolean minecraftVersionsOverlap(String[] expectedVersions, String[] actualVersions) {
        if (expectedVersions == null || expectedVersions.length == 0
                || actualVersions == null || actualVersions.length == 0) {
            return true;
        }
        for (String expected : expectedVersions) {
            if (!Tools.isValidString(expected)) continue;
            for (String actual : actualVersions) {
                if (Tools.isValidString(actual) && expected.equalsIgnoreCase(actual)) return true;
            }
        }
        return false;
    }

    default boolean loadersOverlap(String[] expectedLoaders, String[] actualLoaders) {
        if (expectedLoaders == null || expectedLoaders.length == 0 || actualLoaders == null || actualLoaders.length == 0) {
            return true;
        }

        for (String expected : expectedLoaders) {
            if (!Tools.isValidString(expected)) continue;
            for (String actual : actualLoaders) {
                if (Tools.isValidString(actual) && expected.equalsIgnoreCase(actual)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Imports the mod(pack) from a file.
     * May require the download of additional files.
     * May requires launching the installation of a modloader
     * @param activity any activity
     * @param zipUri URI to DocumentsUI selected zip file
     */
    ModLoader importModpack(Activity activity, Uri zipUri) throws IOException, NoSuchAlgorithmException;
}
