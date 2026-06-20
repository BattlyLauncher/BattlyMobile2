package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Architecture.archAsString;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.MathUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NewJREUtil {
    private static boolean checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        String launcher_runtime_version;
        String installed_runtime_version = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        try {
            launcher_runtime_version = Tools.read(assetManager.open(internalRuntime.path+"/version"));
        }catch (IOException exc) {
            //we don't have a runtime included in the APK!
            //if we have one installed AND it matches our arch -> return true -> proceed
            //if we don't have one, or arch is wrong -> return false -> fall back to auto-downloader
            if (installed_runtime_version == null) return false;
            Runtime installedRuntime = MultiRTUtils.forceReread(internalRuntime.name);
            boolean archOk = installedRuntime.arch != null &&
                    Architecture.archAsInt(installedRuntime.arch) == Tools.DEVICE_ARCHITECTURE;
            return archOk;
        }
        // this implicitly checks for null, so it will unpack the runtime even if we don't have one installed
        if(!launcher_runtime_version.equals(installed_runtime_version))
            return unpackInternalRuntime(assetManager, internalRuntime, launcher_runtime_version);
        else return true;
    }

    private static boolean unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String version) {
        String arch = archAsString(Tools.DEVICE_ARCHITECTURE);
        try {
            if (hasAsset(assetManager, internalRuntime.path + "/full-" + arch + ".tar.xz")) {
                MultiRTUtils.installRuntimeNamed(
                        Tools.NATIVE_LIB_DIR,
                        assetManager.open(internalRuntime.path + "/full-" + arch + ".tar.xz"),
                        internalRuntime.name);
                MultiRTUtils.writeInternalRuntimeVersion(internalRuntime.name, version);
            } else if (hasAsset(assetManager, internalRuntime.path + "/full-" + arch + "/release")) {
                MultiRTUtils.installRuntimeAssetDirectory(assetManager, internalRuntime.path + "/full-" + arch, internalRuntime.name);
                MultiRTUtils.writeInternalRuntimeVersion(internalRuntime.name, version);
            } else {
                MultiRTUtils.installRuntimeNamedBinpack(
                        assetManager.open(internalRuntime.path+"/universal.tar.xz"),
                        assetManager.open(internalRuntime.path+"/bin-" + arch + ".tar.xz"),
                        internalRuntime.name, version);
            }
            MultiRTUtils.postPrepare(internalRuntime.name);
            return true;
        }catch (IOException e) {
            Log.e("NewJREAuto", "Internal JRE unpack failed", e);
            return false;
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

    private static InternalRuntime getInternalRuntime(Runtime runtime) {
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.name.equals(runtime.name)) return internalRuntime;
        }
        return null;
    }

    private static MathUtils.RankedValue<Runtime> getNearestInstalledRuntime(int targetVersion) {
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        return MathUtils.findNearestPositive(targetVersion, runtimes, (runtime)->runtime.javaVersion);
    }

    private static MathUtils.RankedValue<InternalRuntime> getNearestInternalRuntime(int targetVersion) {
        List<InternalRuntime> runtimeList = Arrays.asList(InternalRuntime.values());
        return MathUtils.findNearestPositive(targetVersion, runtimeList, (runtime)->runtime.majorVersion);
    }


    /** @return true if everything is good, false otherwise.  */
    public static boolean installNewJreIfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) {
        // Legacy metadata means Java 8, not "no runtime required". The startup bootstrap normally
        // installs it first, but launches still need this synchronous guard if the user is faster
        // than the background download or the first attempt failed.
        int gameRequiredVersion = 8;
        if (versionInfo.javaVersion != null
                && !"jre-legacy".equalsIgnoreCase(versionInfo.javaVersion.component)) {
            gameRequiredVersion = versionInfo.javaVersion.majorVersion;
        }

        LauncherProfiles.load();
        AssetManager assetManager = activity.getAssets();
        MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();
        String profileRuntime = Tools.getSelectedRuntime(minecraftProfile);
        Runtime runtime = MultiRTUtils.read(profileRuntime);

        // Prefer an EXACT version match over a higher one. Some mods (e.g. Cobblemon via Fabric)
        // declare an exact Java version dependency and will reject a newer JVM even if >= would
        // satisfy vanilla Minecraft.
        if (runtime.javaVersion == gameRequiredVersion) {
            boolean archOk = runtime.arch != null &&
                    Architecture.archAsInt(runtime.arch) == Tools.DEVICE_ARCHITECTURE;
            if (!archOk) {
                Log.w("NewJREUtil", "Runtime " + runtime.name + " arch mismatch (" + runtime.arch +
                        "), selecting a compatible runtime instead.");
            } else {
                InternalRuntime internalRuntime = getInternalRuntime(runtime);
                if (internalRuntime != null) {
                    if (checkInternalRuntime(assetManager, internalRuntime)) {
                        return true;
                    }
                    Log.w("NewJREUtil", "Internal runtime " + internalRuntime.name +
                            " unavailable for arch " + archAsString(Tools.DEVICE_ARCHITECTURE) +
                            ", falling back to auto-downloader.");
                } else {
                    return true;
                }
            }
        } else if (runtime.javaVersion > gameRequiredVersion) {
            Log.i("NewJREUtil", "Selected runtime " + runtime.name + " (Java " + runtime.javaVersion +
                    ") is higher than required Java " + gameRequiredVersion +
                    ". Preferring exact version to avoid mod compatibility issues.");
        }

        // Look for an already-installed runtime with the exact required version.
        for (Runtime rt : MultiRTUtils.getRuntimes()) {
            if (rt.javaVersion == gameRequiredVersion && rt.arch != null &&
                    Architecture.archAsInt(rt.arch) == Tools.DEVICE_ARCHITECTURE) {
                Log.i("NewJREUtil", "Found exact installed match: " + rt.name);
                minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + rt.name;
                LauncherProfiles.write();
                return true;
            }
        }

        // Check whether there is a bundled (internal) runtime for the exact version.
        for (InternalRuntime ir : InternalRuntime.values()) {
            if (ir.majorVersion == gameRequiredVersion) {
                if (checkInternalRuntime(assetManager, ir)) {
                    minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + ir.name;
                    LauncherProfiles.write();
                    return true;
                }
                break;
            }
        }

        // Download the exact required version from the remote repository.
        try {
            String jreName = net.kdt.pojavlaunch.utils.JREAutoDownloader.downloadAndExtractJRE(gameRequiredVersion);
            minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + jreName;
            LauncherProfiles.write();
            return true;
        } catch (Exception e) {
            Log.e("NewJREAuto", "Failed to download exact JRE " + gameRequiredVersion +
                    ", trying any compatible installed version.", e);
        }

        // Last resort: fall back to the nearest installed runtime that is >= required.
        // This restores the old ">=" behaviour for cases where an exact version is unavailable.
        MathUtils.RankedValue<?> nearestInstalledRuntime = getNearestInstalledRuntime(gameRequiredVersion);
        if (nearestInstalledRuntime != null && nearestInstalledRuntime.value instanceof Runtime) {
            Runtime selectedRuntime = (Runtime) nearestInstalledRuntime.value;
            boolean selectedArchOk = selectedRuntime.arch != null &&
                    Architecture.archAsInt(selectedRuntime.arch) == Tools.DEVICE_ARCHITECTURE;
            if (selectedRuntime.javaVersion >= gameRequiredVersion && selectedArchOk) {
                minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + selectedRuntime.name;
                LauncherProfiles.write();
                return true;
            }
        }

        showRuntimeFail(activity, versionInfo);
        return false;
    }

    private static void showRuntimeFail(Activity activity, JMinecraftVersionList.Version verInfo) {
        Tools.dialogOnUiThread(activity, activity.getString(R.string.global_error),
                activity.getString(R.string.multirt_nocompatiblert, verInfo.javaVersion.majorVersion));
    }

    private enum InternalRuntime {
        JRE_17(17, "Internal-17", "components/jre-new"),
        JRE_21(21, "Internal-21", "components/jre-21"),
        JRE_25(25, "Internal-25", "components/jre-25");
        public final int majorVersion;
        public final String name;
        public final String path;
        InternalRuntime(int majorVersion, String name, String path) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
        }
    }

}
