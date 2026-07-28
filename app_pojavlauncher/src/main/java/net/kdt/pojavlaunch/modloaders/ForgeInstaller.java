package net.kdt.pojavlaunch.modloaders;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ForgeInstaller {
    private ForgeInstaller() {
    }

    public static String install(Context context, File installerJar, boolean createProfile) throws IOException {
        File workspace = ModloaderInstallUtils.createWorkspace("forge");
        ModloaderInstallUtils.extractZip(installerJar, workspace);

        File installProfileFile = new File(workspace, "install_profile.json");
        if (!installProfileFile.isFile()) {
            throw new IOException("Forge installer is missing install_profile.json");
        }

        JsonObject installProfile = JsonParser.parseString(Tools.read(installProfileFile)).getAsJsonObject();
        boolean isNewInstaller = installProfile.has("spec") || new File(workspace, "version.json").isFile();
        String versionId = isNewInstaller
                ? installNewForge(context, installerJar, workspace)
                : installOldForge(workspace, installProfile);

        if (createProfile) {
            ModloaderInstallUtils.upsertProfile("Forge", versionId, "forge", null);
        }
        return versionId;
    }

    private static String installOldForge(File workspace, JsonObject installProfile) throws IOException {
        JsonObject install = installProfile.getAsJsonObject("install");
        JsonObject versionInfo = installProfile.getAsJsonObject("versionInfo");
        JMinecraftVersionList.Version forgeVersion = Tools.GLOBAL_GSON.fromJson(versionInfo, JMinecraftVersionList.Version.class);
        if (forgeVersion == null || !Tools.isValidString(forgeVersion.id)) {
            throw new IOException("Forge installer returned an invalid versionInfo");
        }

        String installArtifactPath = ModloaderInstallUtils.artifactPathFromDescriptor(install.get("path").getAsString());
        ModloaderInstallUtils.downloadLibraries(forgeVersion.libraries, installArtifactPath);

        File sourceJar = new File(workspace, install.get("filePath").getAsString());
        File targetJar = new File(Tools.DIR_HOME_LIBRARY, installArtifactPath);
        FileUtils.ensureParentDirectory(targetJar);
        org.apache.commons.io.FileUtils.copyFile(sourceJar, targetJar);
        ModloaderInstallUtils.writeVersionJson(forgeVersion.id, versionInfo.toString());
        return forgeVersion.id;
    }

    private static String installNewForge(Context context, File installerJar, File workspace) throws IOException {
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

        String runtimeName = ModloaderInstallUtils.selectRuntimeForJar(installerJar, 8);
        if (runtimeName == null) {
            throw new IOException("No compatible runtime found for Forge installer");
        }

        IOException officialInstallerFailure = null;
        try {
            HeadlessInstallerRunner.run(context.getApplicationContext(), runtimeName, workspace,
                    officialInstallerCommands(installerJar));
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
                HeadlessInstallerRunner.run(context.getApplicationContext(), runtimeName, workspace,
                        compatibilityInstallerCommands(context, installerJar));
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
