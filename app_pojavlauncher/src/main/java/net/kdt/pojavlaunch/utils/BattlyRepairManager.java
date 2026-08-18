package net.kdt.pojavlaunch.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.authenticator.BattlyAuthlibManager;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Repairs launcher-managed files without touching worlds, mods or user configuration. */
public final class BattlyRepairManager {
    private BattlyRepairManager() {
    }

    public interface ProgressListener {
        void onProgress(int percent, String stage);
    }

    @NonNull
    public static Result repair(@NonNull Context context, @NonNull ProgressListener listener) {
        ArrayList<String> actions = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        int removedTemporaryFiles = 0;
        int removedBrokenFiles = 0;

        listener.onProgress(5, context.getString(R.string.maintenance_stage_cleaning));
        removedTemporaryFiles += cleanTemporaryFiles(Tools.DIR_CACHE);
        removedTemporaryFiles += cleanTemporaryFiles(new File(Tools.DIR_HOME_VERSION));
        removedTemporaryFiles += cleanTemporaryFiles(new File(Tools.DIR_GAME_NEW, "libraries"));
        removedTemporaryFiles += cleanTemporaryFiles(new File(Tools.DIR_GAME_NEW, "assets"));
        removedTemporaryFiles += cleanTemporaryFiles(new File(Tools.DIR_GAME_HOME, "lwjgl3"));
        RuntimeHealthManager.removeBrokenDownloadDirectories();

        listener.onProgress(24, context.getString(R.string.maintenance_stage_installation));
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
        String versionId = profile.lastVersionId == null ? "" : profile.lastVersionId;
        removedBrokenFiles += removeBrokenVersionFiles(context, versionId, actions);
        removedBrokenFiles += removeZeroLengthManagedFiles(new File(Tools.DIR_GAME_NEW, "libraries"));
        removedBrokenFiles += removeZeroLengthManagedFiles(new File(Tools.DIR_GAME_NEW, "assets/indexes"));

        listener.onProgress(48, context.getString(R.string.maintenance_stage_components));
        try {
            AsyncAssetManager.repairPackagedComponents(context);
            actions.add(context.getString(R.string.maintenance_action_components));
        } catch (IOException exception) {
            warnings.add(context.getString(R.string.maintenance_warning_components, exception.getMessage()));
        }

        listener.onProgress(68, context.getString(R.string.maintenance_stage_java));
        MinecraftCompatibilityEngine.Report compatibility = MinecraftCompatibilityEngine.evaluate(
                context, versionId, null, profile.pojavRendererName);
        if (compatibility.runtimeName == null
                || !RuntimeHealthManager.inspect(compatibility.runtimeName).isHealthy()) {
            try {
                String runtime = JREAutoDownloader.downloadAndExtractJRE(compatibility.javaMajor);
                actions.add(context.getString(R.string.maintenance_action_java_installed,
                        compatibility.javaMajor, runtime));
            } catch (Exception exception) {
                warnings.add(context.getString(R.string.maintenance_warning_java,
                        compatibility.javaMajor, exception.getMessage()));
            }
        } else {
            actions.add(context.getString(R.string.maintenance_action_java_verified,
                    compatibility.javaMajor));
        }

        listener.onProgress(88, context.getString(R.string.maintenance_stage_auth));
        try {
            BattlyAuthlibManager.ensureAuthlib();
            actions.add(context.getString(R.string.maintenance_action_auth));
        } catch (IOException exception) {
            warnings.add(context.getString(R.string.maintenance_warning_auth, exception.getMessage()));
        }

        if (removedTemporaryFiles > 0) {
            actions.add(context.getString(R.string.maintenance_action_temporary_removed,
                    removedTemporaryFiles));
        }
        if (removedBrokenFiles > 0) {
            actions.add(context.getString(R.string.maintenance_action_corrupt_removed,
                    removedBrokenFiles));
        }
        listener.onProgress(100, context.getString(R.string.maintenance_repair_complete));
        return new Result(versionId, removedTemporaryFiles, removedBrokenFiles,
                Collections.unmodifiableList(actions), Collections.unmodifiableList(warnings));
    }

    static boolean isSafeTemporaryFile(@NonNull File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".download") || name.endsWith(".part") || name.endsWith(".tmp");
    }

    private static int cleanTemporaryFiles(File root) {
        if (root == null || !root.exists()) return 0;
        File[] files = root.listFiles();
        if (files == null) return 0;
        int removed = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                removed += cleanTemporaryFiles(file);
            } else if (isSafeTemporaryFile(file) && file.delete()) {
                removed++;
            }
        }
        return removed;
    }

    private static int removeBrokenVersionFiles(Context context, String versionId, List<String> actions) {
        if (versionId == null || versionId.trim().isEmpty()) return 0;
        File versionDirectory = new File(Tools.DIR_HOME_VERSION, versionId);
        File json = new File(versionDirectory, versionId + ".json");
        File jar = new File(versionDirectory, versionId + ".jar");
        int removed = 0;
        if (json.isFile() && json.length() == 0 && json.delete()) removed++;
        if (jar.isFile() && jar.length() == 0 && jar.delete()) removed++;
        if (!json.isFile() || !jar.isFile()) {
            actions.add(context.getString(R.string.maintenance_action_version_incomplete, versionId));
        }
        return removed;
    }

    private static int removeZeroLengthManagedFiles(File root) {
        if (root == null || !root.exists()) return 0;
        File[] files = root.listFiles();
        if (files == null) return 0;
        int removed = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                removed += removeZeroLengthManagedFiles(file);
            } else if (file.length() == 0 && isManagedDownload(file) && file.delete()) {
                removed++;
            }
        }
        return removed;
    }

    private static boolean isManagedDownload(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".json") || name.endsWith(".sha1");
    }

    public static final class Result {
        public final String versionId;
        public final int temporaryFilesRemoved;
        public final int corruptFilesRemoved;
        public final List<String> actions;
        public final List<String> warnings;

        Result(String versionId, int temporaryFilesRemoved, int corruptFilesRemoved,
               List<String> actions, List<String> warnings) {
            this.versionId = versionId;
            this.temporaryFilesRemoved = temporaryFilesRemoved;
            this.corruptFilesRemoved = corruptFilesRemoved;
            this.actions = actions;
            this.warnings = warnings;
        }

        public boolean isSuccessful() {
            return warnings.isEmpty();
        }
    }
}
