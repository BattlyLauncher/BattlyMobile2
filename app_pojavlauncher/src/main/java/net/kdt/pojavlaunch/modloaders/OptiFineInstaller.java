package net.kdt.pojavlaunch.modloaders;

import android.content.Context;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.DependentLibrary;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OptiFineInstaller {
    private static final Pattern MC_VERSION_PATTERN = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("(?i)optifine[_-]([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)[_-]([A-Za-z0-9_]+)");

    private OptiFineInstaller() {
    }

    public static String install(Context context, File installerJar, OptiFineUtils.OptiFineVersion version, boolean createProfile) throws IOException {
        File workspace = ModloaderInstallUtils.createWorkspace("optifine");
        ModloaderInstallUtils.extractZip(installerJar, workspace);

        String normalizedMcVersion = normalizeMinecraftVersion(version.minecraftVersion);
        String normalizedOptiFineVersion = version.versionName.replace(' ', '_');
        String mavenVersion = normalizedMcVersion + "_" + normalizedOptiFineVersion;
        String versionId = normalizedMcVersion + "-OptiFine_" + normalizedOptiFineVersion;

        File installerLibrary = new File(Tools.DIR_HOME_LIBRARY,
                "optifine/OptiFine/" + mavenVersion + "/OptiFine-" + mavenVersion + "-installer.jar");
        File runtimeLibrary = new File(Tools.DIR_HOME_LIBRARY,
                "optifine/OptiFine/" + mavenVersion + "/OptiFine-" + mavenVersion + ".jar");
        copyInstaller(installerJar, installerLibrary);

        ArrayList<DependentLibrary> libraries = new ArrayList<>(2);
        libraries.add(ModloaderInstallUtils.createLibrary(
                "optifine:OptiFine:" + mavenVersion,
                "optifine/OptiFine/" + mavenVersion + "/OptiFine-" + mavenVersion + ".jar",
                runtimeLibrary.toURI().toString()
        ));

        if (new File(workspace, "optifine/Patcher.class").isFile()) {
            String runtimeName = MultiRTUtils.getExactJreName(17);
            if (runtimeName == null) {
                runtimeName = ModloaderInstallUtils.selectRuntimeForJar(installerJar, 8);
            }
            if (runtimeName == null) {
                throw new IOException("No compatible runtime found for OptiFine installer");
            }
            ArrayList<String> commands = new ArrayList<>();
            commands.add("-cp");
            commands.add(installerJar.getAbsolutePath());
            commands.add("optifine.Patcher");
            commands.add(new File(Tools.DIR_HOME_VERSION, normalizedMcVersion + "/" + normalizedMcVersion + ".jar").getAbsolutePath());
            commands.add(installerJar.getAbsolutePath());
            commands.add(runtimeLibrary.getAbsolutePath());
            HeadlessInstallerRunner.run(context.getApplicationContext(), runtimeName, workspace, commands);
            if (!runtimeLibrary.isFile() || runtimeLibrary.length() < 1) {
                throw new IOException("OptiFine patcher did not generate the runtime jar");
            }
        } else {
            copyInstaller(installerJar, runtimeLibrary);
        }

        DependentLibrary launchWrapperLibrary = installLaunchWrapper(workspace);
        libraries.add(launchWrapperLibrary);
        writeVersionJson(versionId, normalizedMcVersion, libraries);

        if (createProfile) {
            ModloaderInstallUtils.upsertProfile("OptiFine", versionId, "command_block", null);
            maybeCreateForgeCombination(normalizedMcVersion, runtimeLibrary);
        }
        return versionId;
    }

    public static String install(Context context, File installerJar, boolean createProfile) throws IOException {
        OptiFineUtils.OptiFineVersion version = parseVersionFromInstaller(installerJar);
        if (version == null) {
            throw new IOException("Unable to infer the OptiFine version from the installer name");
        }
        return install(context, installerJar, version, createProfile);
    }

    private static String normalizeMinecraftVersion(String minecraftVersion) throws IOException {
        Matcher matcher = MC_VERSION_PATTERN.matcher(minecraftVersion);
        if (!matcher.find()) {
            throw new IOException("Unable to determine the target Minecraft version for OptiFine");
        }
        StringBuilder builder = new StringBuilder();
        builder.append(matcher.group(1)).append('.').append(matcher.group(2));
        String patch = matcher.group(3);
        if (patch != null && !"0".equals(patch) && !patch.isEmpty()) {
            builder.append('.').append(patch);
        }
        return builder.toString();
    }

    private static void copyInstaller(File source, File destination) throws IOException {
        FileUtils.ensureParentDirectory(destination);
        org.apache.commons.io.FileUtils.copyFile(source, destination);
    }

    private static DependentLibrary installLaunchWrapper(File workspace) throws IOException {
        File launchWrapper2 = new File(workspace, "launchwrapper-2.0.jar");
        if (launchWrapper2.isFile()) {
            File target = new File(Tools.DIR_HOME_LIBRARY, "optifine/launchwrapper/2.0/launchwrapper-2.0.jar");
            copyInstaller(launchWrapper2, target);
            return ModloaderInstallUtils.createLibrary(
                    "optifine:launchwrapper:2.0",
                    "optifine/launchwrapper/2.0/launchwrapper-2.0.jar",
                    target.toURI().toString()
            );
        }

        File launchWrapperVersionFile = new File(workspace, "launchwrapper-of.txt");
        if (launchWrapperVersionFile.isFile()) {
            String wrapperVersion = Tools.read(launchWrapperVersionFile).trim();
            File wrapperJar = new File(workspace, "launchwrapper-of-" + wrapperVersion + ".jar");
            if (wrapperJar.isFile()) {
                String path = "optifine/launchwrapper-of/" + wrapperVersion + "/launchwrapper-of-" + wrapperVersion + ".jar";
                File target = new File(Tools.DIR_HOME_LIBRARY, path);
                copyInstaller(wrapperJar, target);
                return ModloaderInstallUtils.createLibrary(
                        "optifine:launchwrapper-of:" + wrapperVersion,
                        path,
                        target.toURI().toString()
                );
            }
        }

        String path = "net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar";
        File target = new File(Tools.DIR_HOME_LIBRARY, path);
        DownloadUtils.ensureSha1(target, null, () -> {
            DownloadUtils.downloadFile("https://libraries.minecraft.net/" + path, target);
            return null;
        });
        return ModloaderInstallUtils.createLibrary(
                "net.minecraft:launchwrapper:1.12",
                path,
                "https://libraries.minecraft.net/" + path
        );
    }

    private static void writeVersionJson(String versionId, String minecraftVersion, ArrayList<DependentLibrary> libraries) throws IOException {
        JMinecraftVersionList.Version baseVersion = Tools.GLOBAL_GSON.fromJson(
                Tools.read(new File(Tools.DIR_HOME_VERSION, minecraftVersion + "/" + minecraftVersion + ".json")),
                JMinecraftVersionList.Version.class
        );
        boolean usesModernArguments = baseVersion != null &&
                baseVersion.arguments != null &&
                baseVersion.arguments.game != null;

        JSONObject root = new JSONObject();
        try {
            root.put("id", versionId);
            root.put("inheritsFrom", minecraftVersion);
            root.put("mainClass", "net.minecraft.launchwrapper.Launch");
            root.put("type", "release");

            if (usesModernArguments) {
                JSONObject arguments = new JSONObject();
                JSONArray gameArguments = new JSONArray();
                gameArguments.put("--tweakClass");
                gameArguments.put("optifine.OptiFineTweaker");
                arguments.put("game", gameArguments);
                root.put("arguments", arguments);
            } else {
                String baseArguments = baseVersion != null ? baseVersion.minecraftArguments : null;
                if (Tools.isValidString(baseArguments)) {
                    root.put("minecraftArguments", baseArguments + " --tweakClass optifine.OptiFineTweaker");
                } else {
                    root.put("minecraftArguments", "--tweakClass optifine.OptiFineTweaker");
                }
            }
            root.put("libraries", new JSONArray(Tools.GLOBAL_GSON.toJson(libraries)));
        } catch (JSONException e) {
            throw new IOException("Failed to create OptiFine version json", e);
        }

        ModloaderInstallUtils.writeVersionJson(versionId, root.toString());
    }

    private static OptiFineUtils.OptiFineVersion parseVersionFromInstaller(File installerJar) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(installerJar.getName());
        if (!matcher.find()) {
            return null;
        }
        OptiFineUtils.OptiFineVersion version = new OptiFineUtils.OptiFineVersion();
        version.minecraftVersion = matcher.group(1);
        version.versionName = matcher.group(2);
        return version;
    }

    private static void maybeCreateForgeCombination(String minecraftVersion, File runtimeLibrary) throws IOException {
        String forgeVersionId = ModloaderInstallUtils.findInstalledForgeVersionForMinecraft(minecraftVersion);
        if (!Tools.isValidString(forgeVersionId)) {
            return;
        }

        String folderName = "forge_optifine_" + minecraftVersion.replace('.', '_');
        File comboDirectory = new File(Tools.DIR_GAME_HOME, "custom_instances/" + folderName + "/mods");
        FileUtils.ensureDirectory(comboDirectory);
        org.apache.commons.io.FileUtils.copyFile(runtimeLibrary, new File(comboDirectory, runtimeLibrary.getName()));
        ModloaderInstallUtils.upsertProfile(
                "Forge + OptiFine",
                forgeVersionId,
                "forge",
                "./custom_instances/" + folderName
        );
    }
}
