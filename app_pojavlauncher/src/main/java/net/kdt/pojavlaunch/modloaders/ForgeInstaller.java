package net.kdt.pojavlaunch.modloaders;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.DependentLibrary;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ForgeInstaller {
    private ForgeInstaller() {
    }

    public static String install(Context context, File installerJar, boolean createProfile) throws IOException {
        long startedAt = System.currentTimeMillis();
        File workspace = ModloaderInstallUtils.createWorkspace("forge");
        ModloaderInstallUtils.extractZip(installerJar, workspace);

        File installProfileFile = new File(workspace, "install_profile.json");
        if (!installProfileFile.isFile()) {
            throw new IOException("Forge installer is missing install_profile.json");
        }

        JsonObject installProfile = JsonParser.parseString(Tools.read(installProfileFile)).getAsJsonObject();
        boolean isNewInstaller = installProfile.has("spec") || new File(workspace, "version.json").isFile();
        String versionId = isNewInstaller
                ? installNewForge(context, installerJar, workspace, installProfile)
                : installOldForge(context, workspace, installProfile);

        if (createProfile) {
            ModloaderInstallUtils.upsertProfile("Forge", versionId, "forge", null);
        }
        Logger.appendToLog("Forge installer: completed " + versionId + " in "
                + (System.currentTimeMillis() - startedAt) + " ms");
        return versionId;
    }

    private static String installOldForge(Context context, File workspace,
                                          JsonObject installProfile) throws IOException {
        JsonObject install = installProfile.getAsJsonObject("install");
        JsonObject versionInfo = installProfile.getAsJsonObject("versionInfo");
        JMinecraftVersionList.Version forgeVersion = Tools.GLOBAL_GSON.fromJson(versionInfo, JMinecraftVersionList.Version.class);
        if (forgeVersion == null || !Tools.isValidString(forgeVersion.id)) {
            throw new IOException("Forge installer returned an invalid versionInfo");
        }

        String installArtifactPath = ModloaderInstallUtils.artifactPathFromDescriptor(install.get("path").getAsString());
        ModloaderInstallUtils.downloadLibraries(context, installArtifactPath, forgeVersion.libraries);

        File sourceJar = new File(workspace, install.get("filePath").getAsString());
        File targetJar = new File(Tools.DIR_HOME_LIBRARY, installArtifactPath);
        FileUtils.ensureParentDirectory(targetJar);
        org.apache.commons.io.FileUtils.copyFile(sourceJar, targetJar);
        ModloaderInstallUtils.writeVersionJson(forgeVersion.id, versionInfo.toString());
        return forgeVersion.id;
    }

    private static String installNewForge(Context context, File installerJar, File workspace,
                                          JsonObject installProfile) throws IOException {
        File versionJsonFile = new File(workspace, "version.json");
        if (!versionJsonFile.isFile()) {
            throw new IOException("Forge installer is missing version.json");
        }

        String versionJson = Tools.read(versionJsonFile);
        JMinecraftVersionList.Version forgeVersion = Tools.GLOBAL_GSON.fromJson(versionJson, JMinecraftVersionList.Version.class);
        if (forgeVersion == null || !Tools.isValidString(forgeVersion.id)) {
            throw new IOException("Forge installer returned an invalid version.json");
        }

        ModloaderInstallUtils.ensureMinecraftDirectory(context);
        DependentLibrary[] profileLibraries = installProfile.has("libraries")
                && installProfile.get("libraries").isJsonArray()
                ? Tools.GLOBAL_GSON.fromJson(installProfile.get("libraries"), DependentLibrary[].class)
                : null;
        long prefetchStartedAt = System.currentTimeMillis();
        ModloaderInstallUtils.downloadLibraries(context, null,
                forgeVersion.libraries, profileLibraries);
        Logger.appendToLog("Forge installer: library prefetch completed in "
                + (System.currentTimeMillis() - prefetchStartedAt) + " ms");

        String runtimeName = ModloaderInstallUtils.selectRuntimeForJar(installerJar, 8);
        if (runtimeName == null) {
            throw new IOException("No compatible runtime found for Forge installer");
        }

        IOException officialInstallerFailure = null;
        long officialStartedAt = System.currentTimeMillis();
        try {
            HeadlessInstallerRunner.run(context.getApplicationContext(), runtimeName, workspace,
                    officialInstallerCommands(installerJar));
            Logger.appendToLog("Forge installer: official processors completed in "
                    + (System.currentTimeMillis() - officialStartedAt) + " ms");
        } catch (IOException exception) {
            officialInstallerFailure = exception;
            Logger.appendToLog("Warning: Official Forge CLI failed, retrying with compatibility bootstrapper: "
                    + exception.getMessage());
        }

        ModloaderInstallUtils.writeVersionJson(forgeVersion.id, versionJson);
        try {
            ForgeInstallationValidator.assertComplete(new File(Tools.DIR_GAME_NEW), forgeVersion.id);
        } catch (IOException incompleteInstallation) {
            try {
                long fallbackStartedAt = System.currentTimeMillis();
                HeadlessInstallerRunner.run(context.getApplicationContext(), runtimeName, workspace,
                        compatibilityInstallerCommands(context, installerJar));
                Logger.appendToLog("Forge installer: compatibility processors completed in "
                        + (System.currentTimeMillis() - fallbackStartedAt) + " ms");
                ModloaderInstallUtils.writeVersionJson(forgeVersion.id, versionJson);
                ForgeInstallationValidator.assertComplete(new File(Tools.DIR_GAME_NEW), forgeVersion.id);
            } catch (IOException compatibilityFailure) {
                if (officialInstallerFailure != null) {
                    compatibilityFailure.addSuppressed(officialInstallerFailure);
                }
                compatibilityFailure.addSuppressed(incompleteInstallation);
                throw compatibilityFailure;
            }
        }
        return forgeVersion.id;
    }

    private static ArrayList<String> officialInstallerCommands(File installerJar) {
        ArrayList<String> commands = baseInstallerCommands();
        commands.add("-classpath");
        commands.add(installerJar.getAbsolutePath());
        commands.add("net.minecraftforge.installer.SimpleInstaller");
        commands.add("--installClient");
        commands.add(Tools.DIR_GAME_NEW);
        return commands;
    }

    private static ArrayList<String> compatibilityInstallerCommands(Context context, File installerJar)
            throws IOException {
        ArrayList<String> commands = baseInstallerCommands();
        commands.add("-classpath");
        commands.add(ModloaderInstallUtils.ensureForgeBootstrapper(context)
                + ":" + installerJar.getAbsolutePath());
        commands.add("com.bangbang93.ForgeInstaller");
        commands.add(Tools.DIR_GAME_NEW);
        return commands;
    }

    private static ArrayList<String> baseInstallerCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("-Djava.io.tmpdir=" + Tools.DIR_CACHE.getAbsolutePath());
        commands.add("-Dos.name=Linux");
        return commands;
    }
}
