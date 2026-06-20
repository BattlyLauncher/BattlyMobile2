package net.kdt.pojavlaunch.modloaders;

import android.content.Context;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class NeoForgeInstaller {
    private NeoForgeInstaller() {
    }

    public static String install(Context context, File installerJar, String loaderVersion, boolean createProfile) throws IOException {
        String runtimeName = ModloaderInstallUtils.selectRuntimeForJar(installerJar, 17);
        if (runtimeName == null) {
            throw new IOException("No compatible runtime found for NeoForge installer");
        }

        ArrayList<String> commands = new ArrayList<>();
        commands.add("-jar");
        commands.add(installerJar.getAbsolutePath());
        commands.add("--install-client");
        HeadlessInstallerRunner.run(context.getApplicationContext(), runtimeName, installerJar.getParentFile(), commands);

        String expectedVersion = "neoforge-" + loaderVersion;
        String versionId = ModloaderInstallUtils.findInstalledVersion(expectedVersion, "neoforge");
        if (!Tools.isValidString(versionId)) {
            throw new IOException("NeoForge installer finished without producing a valid version");
        }
        if (createProfile) {
            ModloaderInstallUtils.upsertProfile("NeoForge", versionId, "command_block", null);
        }
        return versionId;
    }

    public static String install(Context context, File installerJar, boolean createProfile) throws IOException {
        String runtimeName = ModloaderInstallUtils.selectRuntimeForJar(installerJar, 17);
        if (runtimeName == null) {
            throw new IOException("No compatible runtime found for NeoForge installer");
        }

        ArrayList<String> commands = new ArrayList<>();
        commands.add("-jar");
        commands.add(installerJar.getAbsolutePath());
        commands.add("--install-client");
        HeadlessInstallerRunner.run(context.getApplicationContext(), runtimeName, installerJar.getParentFile(), commands);

        String versionId = ModloaderInstallUtils.findInstalledVersion("neoforge-", "neoforge");
        if (!Tools.isValidString(versionId)) {
            throw new IOException("NeoForge installer finished without producing a valid version");
        }
        if (createProfile) {
            ModloaderInstallUtils.upsertProfile("NeoForge", versionId, "command_block", null);
        }
        return versionId;
    }
}
