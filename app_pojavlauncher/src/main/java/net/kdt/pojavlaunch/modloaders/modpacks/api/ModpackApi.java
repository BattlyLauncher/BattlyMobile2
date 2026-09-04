package net.kdt.pojavlaunch.modloaders.modpacks.api;


import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDependency;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchCategory;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.utils.BattlyPlusCloud;
import net.kdt.pojavlaunch.utils.InstanceManager;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.IOException;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
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
        handleInstallation(context, modDetail, selectedVersion, targetProfile, null);
    }

    default void handleInstallation(Context context,
                                    ModDetail modDetail,
                                    int selectedVersion,
                                    MinecraftProfile targetProfile,
                                    @Nullable Runnable onOpenInstalledModpack) {
        // Doing this here since when starting installation, the progress does not start immediately
        // which may lead to two concurrent installations (very bad)
        boolean plusQueue = BattlyPlusCloud.canUsePremiumQueue(context);
        boolean isModpack = modDetail.isModpack
                || modDetail.contentType == SearchFilters.TYPE_MODPACK;
        InstallProgressDialogController blocker = showInstallGate(context, plusQueue, isModpack);
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0,
                plusQueue ? R.string.battly_plus_install_queue_message : R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            String completedProfileKey = null;
            try {
                if (!modDetail.isModpack && modDetail.contentType != SearchFilters.TYPE_MODPACK) {
                    installDependencies(context, modDetail, selectedVersion, targetProfile);
                }
                Set<String> previousProfiles = isModpack && LauncherProfiles.mainProfileJson != null
                        ? new HashSet<>(LauncherProfiles.mainProfileJson.profiles.keySet())
                        : new HashSet<>();
                ModLoader loaderInfo = installMod(context, modDetail, selectedVersion, targetProfile);
                if (loaderInfo == null) return;
                if (isModpack) {
                    completedProfileKey = findInstalledProfileKey(previousProfiles, loaderInfo, modDetail.title);
                }
                Telemetry.logContentInstall(modDetail.contentType, modDetail.title, true, null);
                NotificationDownloadListener listener = new NotificationDownloadListener(context, loaderInfo);
                loaderInfo.getDownloadTask(context, listener).run();
                if (!listener.wasSuccessful()) completedProfileKey = null;
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                if (context instanceof net.kdt.pojavlaunch.LauncherActivity) {
                    Tools.runOnUiThread(((net.kdt.pojavlaunch.LauncherActivity) context)
                            ::refreshHomeProfileUi);
                }
            }catch (Exception e) {
                Telemetry.logContentInstall(modDetail.contentType, modDetail.title, false, e);
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            } finally {
                dismissInstallGate(blocker);
            }
            if (isModpack && completedProfileKey != null) {
                showOpenModpackPrompt(context, completedProfileKey, modDetail.title,
                        onOpenInstalledModpack);
            }
        });
    }

    default String findInstalledProfileKey(Set<String> previousProfiles,
                                           ModLoader loaderInfo,
                                           String title) {
        if (LauncherProfiles.mainProfileJson == null
                || LauncherProfiles.mainProfileJson.profiles == null) return null;
        String fallback = null;
        for (Map.Entry<String, MinecraftProfile> entry
                : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null) continue;
            if (!previousProfiles.contains(entry.getKey())) return entry.getKey();
            if (loaderInfo.getVersionId().equals(profile.lastVersionId)
                    && title != null && title.equals(profile.name)) fallback = entry.getKey();
        }
        return fallback;
    }

    default void showOpenModpackPrompt(Context context, String profileKey, String title,
                                       @Nullable Runnable onOpenInstalledModpack) {
        if (!(context instanceof net.kdt.pojavlaunch.LauncherActivity)) return;
        net.kdt.pojavlaunch.LauncherActivity activity =
                (net.kdt.pojavlaunch.LauncherActivity) context;
        Tools.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            AlertDialog dialog = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                    .setTitle(activity.getString(R.string.modpack_install_ready_title, title))
                    .setMessage(R.string.modpack_install_ready_message)
                    .setNegativeButton(R.string.global_cancel, null)
                    .setPositiveButton(R.string.modpack_install_open, null)
                    .create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    String resolvedKey = resolveInstalledProfileKey(profileKey, title);
                    if (resolvedKey == null) {
                        throw new IOException(activity.getString(R.string.error_no_version));
                    }
                    InstanceManager.select(resolvedKey);
                    dialog.dismiss();
                    if (onOpenInstalledModpack != null) onOpenInstalledModpack.run();
                    Tools.backToMainMenu(activity);
                    activity.refreshHomeProfileUi();
                    Tools.MAIN_HANDLER.post(() ->
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
                } catch (Exception error) {
                    Tools.showError(activity, error);
                }
            });
        });
    }

    default String resolveInstalledProfileKey(String requestedKey, String title) {
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null) return null;
        if (LauncherProfiles.mainProfileJson.profiles.containsKey(requestedKey)) return requestedKey;
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null) continue;
            if (requestedKey != null && requestedKey.equals(profile.battlyInstanceId)) return entry.getKey();
            if (title != null && title.equals(profile.name) && Tools.isValidString(profile.lastVersionId)) return entry.getKey();
        }
        return null;
    }

    default void handleDependenciesInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        handleDependenciesInstallation(context, modDetail, selectedVersion, null);
    }

    default void handleDependenciesInstallation(Context context,
                                                ModDetail modDetail,
                                                int selectedVersion,
                                                MinecraftProfile targetProfile) {
        boolean plusQueue = BattlyPlusCloud.canUsePremiumQueue(context);
        InstallProgressDialogController blocker = showInstallGate(context, plusQueue, false);
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
    default InstallProgressDialogController showInstallGate(Context context,
                                                            boolean plusQueue,
                                                            boolean showNativeAd) {
        if (plusQueue) {
            Tools.runOnUiThread(() -> Toast.makeText(
                    context,
                    R.string.battly_plus_install_queue_message,
                    Toast.LENGTH_LONG
            ).show());
            return null;
        }
        return InstallProgressDialogController.show(context, showNativeAd);
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
