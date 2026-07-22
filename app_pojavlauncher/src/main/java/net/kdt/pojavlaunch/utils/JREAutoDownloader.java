package net.kdt.pojavlaunch.utils;

import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JREAutoDownloader {

    private static final String TAG = "JREAutoDownloader";
    private static final String BASE_URL = "https://github.com/BattlyLauncher/battly-openjdk-build/releases/download/last-release/";
    
    private static final Map<Integer, Set<String>> AVAILABLE_ASSETS = new HashMap<>();
    
    static {
        AVAILABLE_ASSETS.put(8, new HashSet<>(Arrays.asList(
                "jre8-android-arm.tar.xz",
                "jre8-android-arm64.tar.xz",
                "jre8-android-x86.tar.xz",
                "jre8-android-x86_64.tar.xz"
        )));
        AVAILABLE_ASSETS.put(17, new HashSet<>(Arrays.asList(
                "jre17-android-arm.tar.xz",
                "jre17-android-arm64.tar.xz",
                "jre17-android-x86.tar.xz",
                "jre17-android-x86_64.tar.xz"
        )));
        AVAILABLE_ASSETS.put(21, new HashSet<>(Arrays.asList(
                "jre21-android-arm.tar.xz",
                "jre21-android-arm64.tar.xz",
                "jre21-android-x86.tar.xz",
                "jre21-android-x86_64.tar.xz"
        )));
        AVAILABLE_ASSETS.put(25, new HashSet<>(Arrays.asList(
                "jre25-android-arm.tar.xz",
                "jre25-android-arm64.tar.xz",
                "jre25-android-x86_64.tar.xz"
        )));
    }

    public interface DownloadCallback {
        void onSuccess(String jreName);
        void onError(Exception e);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Downloads and extracts the requested JRE version asynchronously.
     * @param targetVersion The Java version requested (8, 17, 21, 25).
     * @param callback Callback to return the name or error.
     */
    public static void downloadJREAsync(int targetVersion, DownloadCallback callback) {
        executor.submit(() -> {
            try {
                String name = downloadAndExtractJRE(targetVersion);
                callback.onSuccess(name);
            } catch (Exception e) {
                Log.e(TAG, "Error downloading JRE " + targetVersion, e);
                callback.onError(e);
            }
        });
    }

    public static boolean isJavaVersionInstalled(int targetVersion) {
        return findCompatibleInstalledJreName(targetVersion) != null;
    }

    public static String getCompatibleInstalledJreName(int targetVersion) {
        return findCompatibleInstalledJreName(targetVersion);
    }

    public static boolean isDownloadAvailableForCurrentArchitecture(int targetVersion) {
        int architecture = Architecture.getDeviceArchitecture();
        if (architecture == Architecture.UNSUPPORTED_ARCH) return false;
        String assetName = "jre" + targetVersion + "-android-"
                + Architecture.archAsString(architecture) + ".tar.xz";
        Set<String> assets = AVAILABLE_ASSETS.get(targetVersion);
        return assets != null && assets.contains(assetName);
    }

    /**
     * Downloads and extracts the requested JRE version synchronously.
     * @param targetVersion The Java version requested (8, 17, 21, 25).
     * @return The final name of the extracted JRE inside MultiRT (e.g. "jre-25").
     * @throws Exception If there is any error during the process.
     */
    public static String downloadAndExtractJRE(int targetVersion) throws Exception {
        int architecture = Architecture.getDeviceArchitecture();
        String archString = Architecture.archAsString(architecture);
        
        if (architecture == Architecture.UNSUPPORTED_ARCH) {
            throw new Exception("Unsupported architecture");
        }

        if (targetVersion == 25 && (architecture == Architecture.ARCH_ARM
                || architecture == Architecture.ARCH_X86)) {
            throw new Exception("Java 25 is not compatible with 32-bit devices. This device can use Minecraft versions that require Java 8, 17 or 21.");
        }

        String assetName = "jre" + targetVersion + "-android-" + archString + ".tar.xz";
        
        Set<String> versionAssets = AVAILABLE_ASSETS.get(targetVersion);
        if (versionAssets == null || !versionAssets.contains(assetName)) {
            throw new Exception("Asset not found for Java " + targetVersion + " on architecture " + archString + ": " + assetName);
        }

        String downloadUrl = BASE_URL + assetName;
        String jreName = "jre-" + targetVersion;
        final int downloadJavaVersion = targetVersion;
        
        File runtimeBaseDir = new File(Tools.MULTIRT_HOME);
        File downloadsDir = new File(runtimeBaseDir, "downloads");
        File jreDir = new File(runtimeBaseDir, jreName);
        
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw new IOException("Could not create downloads directory: " + downloadsDir.getAbsolutePath());
        }

        // Si ya está extraído y es válido, retornamos inmediatamente.
        if (isJREValid(jreDir)) {
            Log.i(TAG, "JRE " + targetVersion + " already exists and is valid at " + jreDir.getAbsolutePath());
            return jreName;
        }

        File downloadedFile = new File(downloadsDir, assetName);
        
        if (!downloadedFile.exists() || downloadedFile.length() == 0) {
            Log.i(TAG, "Downloading JRE from " + downloadUrl);
            ProgressLayout.setProgress(ProgressLayout.UNPACK_RUNTIME, 0,
                    net.kdt.pojavlaunch.R.string.java_runtime_download_downloading, targetVersion);
            DownloadUtils.downloadFileMonitored(downloadUrl, downloadedFile, null, (curr, max) -> {
                if (max > 0) {
                    ProgressLayout.setProgress(ProgressLayout.UNPACK_RUNTIME,
                            (int) Math.min(95, Math.max(0, (long) curr * 95L / (long) max)),
                            net.kdt.pojavlaunch.R.string.java_runtime_download_progress,
                            downloadJavaVersion,
                            curr / 1024f / 1024f,
                            max / 1024f / 1024f);
                } else {
                    ProgressLayout.setProgress(ProgressLayout.UNPACK_RUNTIME, 0,
                            net.kdt.pojavlaunch.R.string.java_runtime_download_downloading, downloadJavaVersion);
                }
            });
        } else {
            Log.i(TAG, "Archive already downloaded at " + downloadedFile.getAbsolutePath());
        }

        Log.i(TAG, "Extracting JRE " + jreName);
        ProgressLayout.setProgress(ProgressLayout.UNPACK_RUNTIME, 96,
                net.kdt.pojavlaunch.R.string.java_runtime_download_installing, targetVersion);
        try (FileInputStream fis = new FileInputStream(downloadedFile)) {
            MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR, fis, jreName);
            MultiRTUtils.postPrepare(jreName);
        } catch (Exception e) {
            // Si la extracción falla, borramos el archivo descargado para volver a intentar en el futuro.
            try {
                MultiRTUtils.removeRuntimeNamed(jreName);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            downloadedFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
            throw new Exception("Error extracting JRE archive: " + e.getMessage(), e);
        }
        // Clear cached Runtime entry so the next read reflects the newly extracted files on disk.
        MultiRTUtils.forceReread(jreName);

        if (!isJREValid(jreDir)) {
            throw new Exception("Extracted JRE is not valid. Missing java binary in " + jreDir.getAbsolutePath());
        }

        Log.i(TAG, "JRE " + targetVersion + " successfully prepared as " + jreName);
        return jreName;
    }

    private static boolean isJREValid(File jreDir) {
        if (!jreDir.exists() || !jreDir.isDirectory()) {
            return false;
        }
        File javaBin = new File(jreDir, "bin/java");
        if (!javaBin.exists() || !javaBin.isFile()) {
            return false;
        }
        // Verify the installed JRE's architecture matches the current device.
        // Use forceReread to bypass the cache and always read the actual release file on disk.
        net.kdt.pojavlaunch.multirt.Runtime runtime = MultiRTUtils.forceReread(jreDir.getName());
        if (runtime.arch == null) {
            return false; // Can't determine arch — treat as invalid
        }
        int deviceArch = Architecture.getDeviceArchitecture();
        int runtimeArch = Architecture.archAsInt(runtime.arch);
        return runtimeArch == deviceArch;
    }

    private static String findCompatibleInstalledJreName(int targetVersion) {
        for (Runtime runtime : MultiRTUtils.getRuntimes()) {
            if (runtime.javaVersion != targetVersion || runtime.arch == null) {
                continue;
            }
            if (Architecture.archAsInt(runtime.arch) == Architecture.getDeviceArchitecture()
                    && isJREValid(new File(Tools.MULTIRT_HOME, runtime.name))) {
                return runtime.name;
            }
        }
        return null;
    }

    public static void promoteDefaultRuntimeIfMissing(String jreName) {
        if (LauncherPreferences.DEFAULT_PREF == null) {
            return;
        }
        String currentDefault = LauncherPreferences.DEFAULT_PREF.getString("defaultRuntime", "");
        Runtime currentRuntime = MultiRTUtils.read(currentDefault);
        boolean hasUsableDefault = currentRuntime.javaVersion > 0
                && currentRuntime.arch != null
                && Architecture.archAsInt(currentRuntime.arch) == Architecture.getDeviceArchitecture();
        if (hasUsableDefault) {
            return;
        }
        LauncherPreferences.PREF_DEFAULT_RUNTIME = jreName;
        LauncherPreferences.DEFAULT_PREF.edit().putString("defaultRuntime", jreName).apply();
    }
}
