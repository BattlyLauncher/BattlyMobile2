package net.kdt.pojavlaunch.modloaders;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FileUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SmartJarInstaller {
    private SmartJarInstaller() {
    }

    public static void install(Activity activity, Uri uri) {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.installer_internal_running);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File installerJar = copyToCache(activity, uri);
                InstallerKind installerKind = detectInstallerKind(installerJar);
                String installedLabel;
                switch (installerKind) {
                    case FORGE:
                        ForgeInstaller.install(activity.getApplicationContext(), installerJar, true);
                        installedLabel = "Forge";
                        break;
                    case NEOFORGE:
                        NeoForgeInstaller.install(activity.getApplicationContext(), installerJar, true);
                        installedLabel = "NeoForge";
                        break;
                    case OPTIFINE:
                        OptiFineInstaller.install(activity.getApplicationContext(), installerJar, true);
                        installedLabel = "OptiFine";
                        break;
                    case LEGACY:
                    default:
                        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                        Tools.runOnUiThread(() -> {
                            Toast.makeText(activity, R.string.installer_internal_fallback, Toast.LENGTH_LONG).show();
                            Tools.launchModInstaller(activity, uri);
                        });
                        return;
                }

                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                String finalInstalledLabel = installedLabel;
                Tools.runOnUiThread(() -> Toast.makeText(
                        activity,
                        activity.getString(R.string.installer_internal_success, finalInstalledLabel),
                        Toast.LENGTH_LONG
                ).show());
            } catch (Exception e) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(activity, R.string.execute_jar_failed_to_read_file, e);
            }
        });
    }

    private static File copyToCache(Context context, Uri uri) throws IOException {
        String name = Tools.getFileName(context, uri);
        if (!Tools.isValidString(name)) {
            name = "installer.jar";
        }
        File destination = new File(Tools.DIR_CACHE, "selected_installers/" + name);
        FileUtils.ensureParentDirectory(destination);
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(destination)) {
            if (inputStream == null) {
                throw new IOException("Unable to open installer uri");
            }
            IOUtils.copy(inputStream, outputStream);
        }
        return destination;
    }

    private static InstallerKind detectInstallerKind(File installerJar) throws IOException {
        String lowerName = installerJar.getName().toLowerCase(Locale.ROOT);
        try (ZipFile zipFile = new ZipFile(installerJar)) {
            if (lowerName.contains("optifine") || zipFile.getEntry("optifine/Patcher.class") != null) {
                return InstallerKind.OPTIFINE;
            }
            if (lowerName.contains("neoforge") || containsEntry(zipFile, "neoforge")) {
                return InstallerKind.NEOFORGE;
            }
            if (zipFile.getEntry("install_profile.json") != null || lowerName.contains("forge")) {
                return InstallerKind.FORGE;
            }
        }
        return InstallerKind.LEGACY;
    }

    private static boolean containsEntry(ZipFile zipFile, String needle) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private enum InstallerKind {
        FORGE,
        NEOFORGE,
        OPTIFINE,
        LEGACY
    }
}
