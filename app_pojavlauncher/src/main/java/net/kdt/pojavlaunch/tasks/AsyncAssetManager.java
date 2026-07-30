package net.kdt.pojavlaunch.tasks;


import static net.kdt.pojavlaunch.Architecture.archAsString;
import static net.kdt.pojavlaunch.Architecture.archAsStringAndroid;
import static net.kdt.pojavlaunch.Architecture.getDeviceArchitecture;
import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class AsyncAssetManager {

    private AsyncAssetManager(){}

    /**
     * Attempt to install the java 8 runtime, if necessary
     * @param am App context
     */
    public static void unpackRuntime(AssetManager am) {
        /* Check if JRE is included */
        String rt_version = null;
        String current_rt_version = MultiRTUtils.readInternalRuntimeVersion("Internal");
        String arch = archAsString(Tools.DEVICE_ARCHITECTURE);
        try {
            rt_version = Tools.read(am.open("components/jre/version"));
        } catch (IOException e) {
            Log.e("JREAuto", "JRE was not included on this APK.", e);
        }
        String exactJREName = MultiRTUtils.getExactJreName(8);
        if(current_rt_version == null && exactJREName != null && !exactJREName.equals("Internal")/*this clause is for when the internal runtime is goofed*/) return;
        if(rt_version == null) return;
        if(rt_version.equals(current_rt_version)) return;

        // Install the runtime in an async manner, hope for the best
        String finalRt_version = rt_version;
        ProgressLayout.setProgressMuted(ProgressLayout.UNPACK_RUNTIME, true);
        sExecutorService.execute(() -> {
            try {
                if (hasAsset(am, "components/jre/full-" + arch + "/release")) {
                    MultiRTUtils.installRuntimeAssetDirectory(am, "components/jre/full-" + arch, "Internal");
                    MultiRTUtils.writeInternalRuntimeVersion("Internal", finalRt_version);
                } else {
                    MultiRTUtils.installRuntimeNamedBinpack(
                            am.open("components/jre/universal.tar.xz"),
                            am.open("components/jre/bin-" + arch + ".tar.xz"),
                            "Internal", finalRt_version);
                }
                MultiRTUtils.postPrepare("Internal");
            }catch (IOException e) {
                Log.e("JREAuto", "Internal JRE unpack failed", e);
            } finally {
                ProgressLayout.setProgressMuted(ProgressLayout.UNPACK_RUNTIME, false);
            }
        });
    }

    /** Unpack single files, with no regard to version tracking */
    public static void unpackSingleFiles(Context ctx){
        ProgressLayout.setProgressMuted(ProgressLayout.EXTRACT_SINGLE_FILES, true);
        ProgressLayout.setProgress(ProgressLayout.EXTRACT_SINGLE_FILES, 0);
        sExecutorService.execute(() -> {
            try {
                Tools.copyAssetFile(ctx, "options.txt", Tools.DIR_GAME_NEW, false);
                Tools.copyAssetFile(ctx, "default_new.json", Tools.CTRLMAP_PATH, "default.json", false);

                Tools.copyAssetFile(ctx, "launcher_profiles.json", Tools.DIR_GAME_NEW, false);
                Tools.copyAssetFile(ctx,"resolv.conf",Tools.DIR_DATA, false);
            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed to unpack critical components !");
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.EXTRACT_SINGLE_FILES);
                ProgressLayout.setProgressMuted(ProgressLayout.EXTRACT_SINGLE_FILES, false);
            }
        });
    }

    public static void unpackComponents(Context ctx){
        ProgressLayout.setProgressMuted(ProgressLayout.EXTRACT_COMPONENTS, true);
        ProgressLayout.setProgress(ProgressLayout.EXTRACT_COMPONENTS, 0);
        sExecutorService.execute(() -> {
            try {
                unpackComponent(ctx, "caciocavallo", false);
                unpackComponent(ctx, "caciocavallo17", false);
                // Since the Java module system doesn't allow multiple JARs to declare the same module,
                // we repack them to a single file here
                unpackLwjglNatives(ctx);
                unpackComponent(ctx, "lwjgl3/3.3.3", false);
                unpackComponent(ctx, "lwjgl3/3.4.1", false);
                unpackComponent(ctx, "lwjgl3/3.4.2", false);
                unpackLwjglRootFiles(ctx);
                unpackComponent(ctx, "security", true);
                unpackComponent(ctx, "arc_dns_injector", true);
                unpackComponent(ctx, "lwjgl2_methods_injector", true);
                unpackComponent(ctx, "forge_installer", true);
            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed to unpack components !",e );
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.EXTRACT_COMPONENTS);
                ProgressLayout.setProgressMuted(ProgressLayout.EXTRACT_COMPONENTS, false);
            }
        });
    }

    public static synchronized void unpackLwjglNatives(Context ctx) throws IOException {
        AssetManager am = ctx.getAssets();
        String sArch = archAsStringAndroid(getDeviceArchitecture());
        File obsolete333Dir = new File(Tools.DIR_DATA, "lwjgl-3.3.3-natives/" + sArch);
        if (obsolete333Dir.exists()) {
            FileUtils.deleteDirectory(obsolete333Dir);
            Log.i("UnpackLwjgl", "Removed obsolete LWJGL 3.3.3 native component " + obsolete333Dir);
        }
        String[] lwjglVersions = {"3.4.1", "3.4.2"};
        for (String lwjglVersion : lwjglVersions) {
            String component = "lwjgl-" + lwjglVersion + "-natives";
            String componentPath = component + "/" + sArch;
            File targetDir = new File(Tools.DIR_DATA, componentPath);
            String[] fileList = am.list("components/" + componentPath);
            if (fileList == null || fileList.length == 0) {
                Log.w("UnpackLwjgl", "No LWJGL natives found for " + lwjglVersion + " / " + sArch);
                if (targetDir.exists()) {
                    FileUtils.deleteDirectory(targetDir);
                    Log.i("UnpackLwjgl", "Removed obsolete native component " + targetDir);
                }
                continue;
            }
            File versionMarker = new File(targetDir, ".version");
            String assetVersion = Tools.read(am.open("components/lwjgl3/" + lwjglVersion + "/version"));
            boolean shouldUpdate = true;
            if (versionMarker.exists()) {
                try (FileInputStream fis = new FileInputStream(versionMarker)) {
                    shouldUpdate = !assetVersion.equals(Tools.read(fis));
                }
            }
            if (!shouldUpdate) {
                for (String fileName : fileList) {
                    File installedFile = new File(targetDir, fileName);
                    if (!installedFile.isFile() || installedFile.length() == 0) {
                        Log.w("UnpackLwjgl", componentPath + " is incomplete; missing " + fileName);
                        shouldUpdate = true;
                        break;
                    }
                }
            }
            if (!shouldUpdate) {
                Log.i("UnpackLwjgl", componentPath + " is up-to-date with the launcher, continuing...");
                continue;
            }
            if (targetDir.exists()) {
                FileUtils.deleteDirectory(targetDir);
            }
            //noinspection ResultOfMethodCallIgnored
            targetDir.mkdirs();
            Log.i("UnpackLwjgl", "Unpacking " + componentPath);
            for (String fileName : fileList) {
                Tools.copyAssetFile(ctx, "components/" + componentPath + "/" + fileName,
                        targetDir.getAbsolutePath(), true);
            }
            Tools.write(versionMarker.getAbsolutePath(), assetVersion);
        }
    }

    private static void unpackLwjglRootFiles(Context ctx) throws IOException {
        AssetManager am = ctx.getAssets();
        String[] fileList = am.list("components/lwjgl3");
        if (fileList == null) return;
        File targetDir = new File(Tools.DIR_GAME_HOME, "lwjgl3");
        //noinspection ResultOfMethodCallIgnored
        targetDir.mkdirs();
        for (String fileName : fileList) {
            if (!hasAsset(am, "components/lwjgl3/" + fileName)) {
                continue;
            }
            Tools.copyAssetFile(ctx, "components/lwjgl3/" + fileName, targetDir.getAbsolutePath(), true);
        }
    }

    private static void unpackComponent(Context ctx, String component, boolean privateDirectory) throws IOException {
        AssetManager am = ctx.getAssets();
        String rootDir = privateDirectory ? Tools.DIR_DATA : Tools.DIR_GAME_HOME;

        File versionFile = new File(rootDir + "/" + component + "/version");
        InputStream is = am.open("components/" + component + "/version");
        if(!versionFile.exists()) {
            if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                FileUtils.deleteDirectory(versionFile.getParentFile());
            }
            versionFile.getParentFile().mkdir();

            Log.i("UnpackPrep", component + ": Pack was installed manually, or does not exist, unpacking new...");
            String[] fileList = am.list("components/" + component);
            for(String s : fileList) {
                Tools.copyAssetFile(ctx, "components/" + component + "/" + s, rootDir + "/" + component, true);
            }
        } else {
            FileInputStream fis = new FileInputStream(versionFile);
            String release1 = Tools.read(is);
            String release2 = Tools.read(fis);
            if (!release1.equals(release2)) {
                if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                    FileUtils.deleteDirectory(versionFile.getParentFile());
                }
                versionFile.getParentFile().mkdir();

                String[] fileList = am.list("components/" + component);
                for (String fileName : fileList) {
                    Tools.copyAssetFile(ctx, "components/" + component + "/" + fileName, rootDir + "/" + component, true);
                }
            } else {
                Log.i("UnpackPrep", component + ": Pack is up-to-date with the launcher, continuing...");
            }
        }
    }

    private static boolean hasAsset(AssetManager assetManager, String path) {
        try {
            assetManager.open(path).close();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
