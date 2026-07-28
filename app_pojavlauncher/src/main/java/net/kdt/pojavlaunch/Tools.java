package net.kdt.pojavlaunch;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;
import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;
import static net.kdt.pojavlaunch.PojavProfile.getAllProfiles;
import static net.kdt.pojavlaunch.Architecture.archAsStringAndroid;
import static net.kdt.pojavlaunch.Architecture.getDeviceArchitecture;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_IGNORE_NOTCH;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_NOTCH_SIZE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.kdt.pojavlaunch.authenticator.BattlyAuthlibManager;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.lifecycle.ContextExecutorTask;
import net.kdt.pojavlaunch.lifecycle.LifecycleAwareAlertDialog;
import net.kdt.pojavlaunch.memory.MemoryHoleFinder;
import net.kdt.pojavlaunch.memory.SelfMapsParser;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.plugins.FFmpegPlugin;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;
import net.kdt.pojavlaunch.utils.LegacyShaderpackCompat;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.OfflineSkinManager;
import net.kdt.pojavlaunch.utils.PromotedServersManager;
import net.kdt.pojavlaunch.utils.OldVersionsUtils;
import net.kdt.pojavlaunch.utils.BattlyNotify;
import net.kdt.pojavlaunch.utils.RendererPluginRegistry;
import net.kdt.pojavlaunch.value.DependentLibrary;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.MinecraftLibraryArtifact;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.libsdl.app.SDLControllerManager;
import org.lwjgl.glfw.CallbackBridge;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

@SuppressWarnings("IOStreamConstructor")
public final class Tools {
    public  static final float BYTE_TO_MB = 1024 * 1024;
    public static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    public static String APP_NAME = "Battly Launcher";

    public static final Gson GLOBAL_GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String URL_HOME = "https://battlylauncher.com";
    public static String NATIVE_LIB_DIR;
    public static String DIR_DATA; //Initialized later to get context
    public static File DIR_CACHE;
    public static String MULTIRT_HOME;
    public static String LOCAL_RENDERER = null;
    public static int DEVICE_ARCHITECTURE;
    public static final String LAUNCHERPROFILES_RTPREFIX = "battly://";
    private static final String LEGACY_LAUNCHERPROFILES_RTPREFIX = "ame" + "thyst://";

    // New since 3.3.1
    public static String DIR_ACCOUNT_NEW;
    public static String DIR_GAME_HOME;
    public static String DIR_GAME_NEW;
    public static String GAME_PROFILES_FILE;

    // New since 3.0.0
    public static String DIRNAME_HOME_JRE = "lib";

    // New since 2.4.2
    public static String DIR_HOME_VERSION;
    public static String DIR_HOME_LIBRARY;

    public static String DIR_HOME_CRASH;

    public static String ASSETS_PATH;
    public static String OBSOLETE_RESOURCES_PATH;
    public static String CTRLMAP_PATH;
    public static String CTRLDEF_FILE;
    private static RenderersList sCompatibleRenderers;
    public static int iLwjglVersion = 0;
    public static String sLwjglVersion = null;
    public static String lwjglNativesDir = null;


    private static File getPojavStorageRoot(Context ctx) {
        File externalFilesDir = ctx.getExternalFilesDir(null);
        return externalFilesDir != null ? externalFilesDir : ctx.getFilesDir();
    }

    /**
     * Checks if the Pojav's storage root is accessible and read-writable
     * @param context context to get the storage root if it's not set yet
     * @return true if storage is fine, false if storage is not accessible
     */
    public static boolean checkStorageRoot(Context context) {
        File externalFilesDir = DIR_GAME_HOME  == null ? Tools.getPojavStorageRoot(context) : new File(DIR_GAME_HOME);
        //externalFilesDir == null when the storage is not mounted if it was obtained with the context call
        return externalFilesDir != null && Environment.getExternalStorageState(externalFilesDir).equals(Environment.MEDIA_MOUNTED);
    }

    public static boolean canBrowseSharedStorage() {
        return false;
    }

    /**
     * Checks if the Pojav's storage root is accessible and read-writable. If it's not, starts
     * the MissingStorageActivity and finishes the supplied activity.
     * @param context the Activity that checks for storage availability
     * @return whether the storage is available or not.
     */
    public static boolean checkStorageInteractive(Activity context) {
        if(!Tools.checkStorageRoot(context)) {
            context.startActivity(new Intent(context, MissingStorageActivity.class));
            context.finish();
            return false;
        }
        return true;
    }

    /**
     * Initialize context constants most necessary for launcher's early startup phase
     * that are not dependent on user storage.
     * All values that depend on DIR_DATA and are not dependent on DIR_GAME_HOME must
     * be initialized here.
     * @param ctx the context for initialization.
     */
    public static void initEarlyConstants(Context ctx) {
        DIR_CACHE = ctx.getCacheDir();
        DIR_DATA = ctx.getFilesDir().getParent();
        MULTIRT_HOME = DIR_DATA + "/runtimes";
        DIR_ACCOUNT_NEW = DIR_DATA + "/accounts";
        NATIVE_LIB_DIR = ctx.getApplicationInfo().nativeLibraryDir;
    }

    /**
     * Initialize context constants that depend on user storage.
     * Any value (in)directly dependent on DIR_GAME_HOME should be set only here.
     * You ABSOLUTELY MUST check for storage presence using checkStorageRoot() before calling this.
     */
    public static void initStorageConstants(Context ctx){
        initEarlyConstants(ctx);
        DIR_GAME_HOME = getPojavStorageRoot(ctx).getAbsolutePath();
        DIR_GAME_NEW = DIR_GAME_HOME + "/.minecraft";
        DIR_HOME_VERSION = DIR_GAME_NEW + "/versions";
        DIR_HOME_LIBRARY = DIR_GAME_NEW + "/libraries";
        DIR_HOME_CRASH = DIR_GAME_NEW + "/crash-reports";
        ASSETS_PATH = DIR_GAME_NEW + "/assets";
        OBSOLETE_RESOURCES_PATH = DIR_GAME_NEW + "/resources";
        CTRLMAP_PATH = DIR_GAME_HOME + "/controlmap";
        CTRLDEF_FILE = DIR_GAME_HOME + "/controlmap/default.json";
        GAME_PROFILES_FILE = Tools.DIR_GAME_NEW + "/launcher_profiles.json";
        switchDemo(isDemoProfile(ctx));
    }

    @SuppressLint("PrivateApi")
    private static String systemPropertiesGet(String systemProperty) throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
        Class<?> cSystemProperties = Class.forName("android.os.SystemProperties");
        Method get = cSystemProperties.getMethod("get", String.class);
        return (String) get.invoke(null, systemProperty);
    }

    private static boolean isAdreno740(){
        try {
            BufferedReader br = new BufferedReader(
                    new FileReader("/sys/class/kgsl/kgsl-3d0/gpu_model")
            );
            String gpuRenderer = br.readLine();
            return gpuRenderer != null &&
                    gpuRenderer.toLowerCase().contains("adreno") &&
                    gpuRenderer.contains("740");
        } catch (IOException e) {
            // If it doesn't exist, we definitely aren't on 740
            return false;
        }
    }

    /**
     * Detects whether or not you are on OneUI and using Adreno 740
     * <a href="https://gitlab.freedesktop.org/mesa/mesa/-/blob/main/src/freedreno/common/freedreno_devices.py?ref_type=heads#L1007-L1009">
     *     Mesa sets it to 0 by default due to vendor quirks
     * </a>
     * It is possible that OneUI simply deviates from this commonality, hence why
     * <a href="https://github.com/K11MCH1/AdrenoToolsDrivers/releases/tag/v26.0.0-rc07">
     *     this is a common fix
     * </a>
     * @return Whether or not to export FD_DEV_FEATURES=enable_ubwc_flag_hint=1
     */
    public static boolean shouldUseUBWC() {
        try {
            boolean isSamsung = Build.MANUFACTURER.equalsIgnoreCase("samsung");
            boolean isOneUI = !systemPropertiesGet("ro.build.version.oneui").isBlank();
            return isOneUI && isSamsung && isAdreno740();
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * @return The selected "Custom path" of the current profile
     */
    @NonNull
    private static File getGameDir() {
        return getGameDirPath(LauncherProfiles.getCurrentProfile());
    }

    /**
     * Searches for mod in mods directory of current selected profile
     * @param filenames Filename(s) of the .jar mod(s)
     * @return Whether or not the .jar is found
     */
    public static boolean hasMods(String... filenames) {
        File gameDir = getGameDir();
        File modsDir = new File(gameDir, "mods");
        File[] modFiles = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if (modFiles == null) return false;
        for (File file : modFiles) {
            for (String filename : filenames)
                if (file.getName().contains(filename)) return true;
        }
        return false;
    }

    public static List<File> getAndroidIncompatibleNativeMods() {
        File modsDir = new File(getGameDir(), "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        List<File> incompatibleMods = new ArrayList<>();
        if (mods == null) {
            return incompatibleMods;
        }
        for (File file : mods) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.contains("axiom")) {
                incompatibleMods.add(file);
            }
        }
        return incompatibleMods;
    }

    public static void disableAndroidIncompatibleNativeMods() {
        for (File file : getAndroidIncompatibleNativeMods()) {
            File disabledFile = new File(file.getParentFile(), file.getName() + ".disabled");
            if (disabledFile.exists() && !disabledFile.delete()) {
                throw new RuntimeException("Failed to replace disabled mod: " + file.getName());
            }
            if (!file.renameTo(disabledFile)) {
                throw new RuntimeException("Failed to disable incompatible mod: " + file.getName());
            }
        }
    }

    /**
     * Tries to delete any sodium related mods of the currently selected profile via string matching
     * the files in the mods folder.
     */
    public static void deleteSodiumMods() {
        File modsDir = new File(getGameDir(), "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods == null) ;
        for(File file : mods) {
            String name = file.getName().toLowerCase();
            if(name.contains("sodium") ||
                    name.contains("beddium")    || // Also covers embeddium
                    name.contains("rubidium")   ||
                    name.contains("xenon")      || // Name conflicts with another mod
                    name.contains("celeritas")  ||
                    name.contains("relictium")  ||
                    name.contains("vintagium")  ||
                    name.contains("podium")     ||
                    name.contains("indium")     ||
                    name.contains("lazurite")   ||
                    name.contains("iris")       ||
                    name.contains("monocle")    ||
                    name.contains("voxy")       ||
                    name.contains("nvidium")    ||
                    name.contains("chloride")   ||
                    name.contains("bedrodium")  ||
                    name.contains("substrate")  || // Name conflicts with another mod
                    name.contains("blendium")   ||
                    name.contains("ryoamium")
                // The name conflicts are for pretty dead mods so we ignore them.
                // I doubt they're using some mod with less than 5k downloads with sodium.
            ) if(!file.delete())
                throw new RuntimeException("Failed to delete Sodium and related mods!");
        }
    }

    /**
     * Search for TouchController mod to automatically enable TouchController mod support.
     *
     * @param gameDir current game directory
     * @return whether TouchController is found
     */
    public static boolean hasTouchController(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if (mods == null) {
            return false;
        }
        for (File file : mods) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.contains("touchcontroller")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Initialize OpenGL and do checks to see if the GPU of the device is affected by the render
     * distance issue.

     * Currently only checks whether the user has an Adreno GPU capable of OpenGL ES 3.

     * This issue is caused by a very severe limit on the amount of GL buffer names that could be allocated
     * by the Adreno properietary GLES driver.

     * @return whether the GPU is affected by the Large Thin Wrapper render distance issue on vanilla
     */
    private static boolean affectedByRenderDistanceIssue() {
        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        return info.isAdreno() && info.glesMajorVersion >= 3;
    }

    private static boolean affectedByLTWRenderDistanceIssue() {
        if(!"opengles3_ltw".equals(Tools.LOCAL_RENDERER)) return false;
        if(!affectedByRenderDistanceIssue()) return false;
        if(hasMods("sodium", "embeddium", "rubidium")) return false;

        int renderDistance;
        try {
            MCOptionUtils.load();
            String renderDistanceString = MCOptionUtils.get("renderDistance");
            renderDistance = Integer.parseInt(renderDistanceString);
        }catch (Exception e) {
            Log.e("Tools", "Failed to check render distance", e);
            renderDistance = 12; // Assume Minecraft's default render distance
        }
        // 7 is the render distance "magic number" above which MC creates too many buffers
        // for Adreno's OpenGL ES implementation
        return renderDistance > 7;
    }

    public static void launchMinecraft(final AppCompatActivity activity, MinecraftAccount minecraftAccount,
                                       MinecraftProfile minecraftProfile, String versionId, int versionJavaRequirement) throws Throwable {
        int freeDeviceMemory = getFreeDeviceMemory(activity);
        int localeString;
        int freeAddressSpace = Architecture.is32BitsDevice() ? getMaxContinuousAddressSpaceSize() : -1;
        Log.i("MemStat", "Free RAM: " + freeDeviceMemory + " Addressable: " + freeAddressSpace);
        if(freeDeviceMemory > freeAddressSpace && freeAddressSpace != -1) {
            freeDeviceMemory = freeAddressSpace;
            localeString = R.string.address_memory_warning_msg;
        } else {
            localeString = R.string.memory_warning_msg;
        }

        if(LauncherPreferences.PREF_RAM_ALLOCATION > freeDeviceMemory) {
            int finalDeviceMemory = freeDeviceMemory;
            BattlyNotify.warning(
                    activity,
                    activity.getString(R.string.memory_warning_title),
                    activity.getString(localeString, finalDeviceMemory, LauncherPreferences.PREF_RAM_ALLOCATION)
            );
        }
        LauncherProfiles.load();
        File gamedir = Tools.getGameDirPath(minecraftProfile);
        startControllableMitigation(activity, gamedir);
        startOldLegacy4JMitigation(activity, gamedir);
        if(affectedByLTWRenderDistanceIssue()) {
            LifecycleAwareAlertDialog.DialogCreator dialogCreator = ((alertDialog, dialogBuilder) ->
                    dialogBuilder.setMessage(activity.getString(R.string.ltw_render_distance_warning_msg))
                            .setPositiveButton(android.R.string.ok, (d, w)->{}));
            if(LifecycleAwareAlertDialog.haltOnDialog(activity.getLifecycle(), activity, dialogCreator)) {
                return;
            }
            // If the code goes here, it means that the user clicked "OK". Fix the render distance.
            try {
                MCOptionUtils.set("renderDistance", "7");
                MCOptionUtils.save();
            }catch (Exception e) {
                Log.e("Tools", "Failed to fix render distance setting", e);
            }
        }


        Runtime runtime = MultiRTUtils.forceReread(Tools.pickRuntime(minecraftProfile, versionJavaRequirement));
        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(versionId);


        // Pre-process specific files
        disableSplash(gamedir);
        clearMinecraftSkinCache();
        applyLowEndMinecraftOptions(gamedir);
        PromotedServersManager.syncBeforeLaunch(activity.getApplicationContext(), gamedir);
        LegacyShaderpackCompat.applyIfNeeded(gamedir, versionInfo);
        List<String> launchArgs = new ArrayList<>(Arrays.asList(getMinecraftClientArgs(minecraftAccount, versionInfo, gamedir)));
        augmentCustomClientLaunchArgs(versionInfo, launchArgs);
        ensureLegacyMixinProvider(activity, gamedir, versionInfo);

        // Select the appropriate openGL version
        OldVersionsUtils.selectOpenGlVersion(versionInfo);


        boolean useAndroidNarratorAdapter = supportsGlobalUrlAgents(versionInfo);
        String launchClassPath = appendLegacyMixinClasspathIfNeeded(
                gamedir, versionInfo, generateLaunchClassPath(versionInfo, versionId));
        if (useAndroidNarratorAdapter) {
            launchClassPath = stripMojangTextToSpeech(launchClassPath);
        } else {
            Logger.appendToLog("Info: Keeping the Minecraft text2speech module for the modular launcher");
        }

        List<String> javaArgList = new ArrayList<>();

        boolean legacyAwtFrame = runtime.javaVersion == 8 && requiresLegacyAwtFrame(versionInfo);
        getCacioJavaArgs(javaArgList, runtime.javaVersion == 8, activity, !legacyAwtFrame);
        if (legacyAwtFrame) {
            Logger.appendToLog("Info: Legacy AWT mode enabled for " + versionInfo.id);
        }

        if (versionInfo.logging != null) {
            String configFile = Tools.DIR_DATA + "/security/" + versionInfo.logging.client.file.id.replace("client", "log4j-rce-patch");
            if (!new File(configFile).exists()) {
                configFile = Tools.DIR_GAME_NEW + "/" + versionInfo.logging.client.file.id;
            }
            javaArgList.add("-Dlog4j.configurationFile=" + configFile);
        }

        File versionSpecificNativesDir = new File(Tools.DIR_CACHE, "natives/"+versionId);
        if(versionSpecificNativesDir.exists()) {
            String dirPath = versionSpecificNativesDir.getAbsolutePath();
            javaArgList.add("-Djna.boot.library.path="+dirPath);
        }

        // Attach the LWJGL patch agent so GL.initCapabilities() is injected into
        // org.lwjgl.opengl.GL at class-load time, regardless of which JAR LabyMod
        // (or any other mod loader) loads GL from.
        File lwjglPatchAgent = new File(Tools.DIR_GAME_HOME, "lwjgl3/lwjgl-patch-agent.jar");
        if (runtime.javaVersion >= 17 && canAttachJavaAgentForRuntime(
                runtime, lwjglPatchAgent, "net/kdt/patch/LwjglPatchAgent.class")) {
            javaArgList.add("-javaagent:" + lwjglPatchAgent.getAbsolutePath());
        }

        if (minecraftAccount.isBattly() && shouldAttachBattlyAuthlib(versionInfo)) {
            BattlyAuthlibManager.addJvmArgumentsIfAvailable(javaArgList);
        } else if (minecraftAccount.isBattly()) {
            Logger.appendToLog("Info: Battly authlib skipped for " + versionInfo.id
                    + " (safe compatibility range: Minecraft 1.7.10-1.16.5)");
        }
        OfflineSkinManager.appendJvmArgs(activity, minecraftAccount, versionInfo, javaArgList);

        List<String> minecraftJvmArgs = new ArrayList<>(
                Arrays.asList(getMinecraftJVMArgs(versionId, gamedir)));
        if (useAndroidNarratorAdapter) {
            minecraftJvmArgs = stripMojangTextToSpeechFromJvmArgs(minecraftJvmArgs);
        }
        javaArgList.addAll(minecraftJvmArgs);
        if (!supportsGlobalUrlAgents(versionInfo)) {
            javaArgList.removeIf(argument -> argument.startsWith("-javaagent:")
                    && argument.contains("arc_dns_injector"));
        }
        String lwjglClassPath = getLWJGL3ClassPath(
                activity, launchClassPath, useAndroidNarratorAdapter);
        // Resolving the classpath also selects the exact bundled native directory.
        addNativeLibraryPathOverrides(javaArgList, versionId);
        javaArgList.add("-cp");
        if (launchClassPath.contains("bta-client-")){ // BTADownloadTask.BASE_JSON sets this. Jank.
            // BTA for some reason needs this to be last or else it uses the wrong lwjgl
            javaArgList.add(launchClassPath + ":" + lwjglClassPath);
        // Legacy Fabric needs this to be first or else it uses the wrong lwjgl
        } else javaArgList.add(lwjglClassPath + ":" + launchClassPath);

        // Forge 1.6.4 crash mitigation
        // https://github.com/MinecraftForge/FML/blob/f1b3381e61fac1a0ae90f521223c6bc613eb4888/common/cpw/mods/fml/common/asm/FMLSanityChecker.java#L192-L208
        // It for some reason fails certification and crashes because it thinks Minecraft is corrupted.
        // This also has no loading screen as a result.
        javaArgList.add("-Dfml.ignoreInvalidMinecraftCertificates=true");

        javaArgList.add(versionInfo.mainClass);
        javaArgList.addAll(launchArgs);
        // ctx.appendlnToLog("full args: "+javaArgList.toString());
        String args = LauncherPreferences.PREF_CUSTOM_JAVA_ARGS;
        if(Tools.isValidString(minecraftProfile.javaArgs)) args = minecraftProfile.javaArgs;
        FFmpegPlugin.discover(activity);
        JREUtils.launchJavaVM(activity, runtime, gamedir, javaArgList, args);
        // If we returned, this means that the JVM exit dialog has been shown and we don't need to be active anymore.
        // We never return otherwise. The process will be killed anyway, and thus we will become inactive
    }
    private static Logger.eventLogListener controllableMitigationLogListener;

    private static boolean requiresLegacyAwtFrame(JMinecraftVersionList.Version versionInfo) {
        if (versionInfo == null) {
            return false;
        }
        String versionId = versionInfo.id == null ? "" : versionInfo.id.toLowerCase(Locale.ROOT);
        if (versionId.matches("^(a|b)?1\\.[0-5](\\..*)?$") || versionId.matches("^1\\.[0-5]$")) {
            return true;
        }
        String creationTime = versionInfo.time;
        if (!Tools.isValidString(creationTime)) {
            return false;
        }
        try {
            Date creationDate = DateUtils.parseReleaseDate(creationTime);
            return creationDate != null && DateUtils.dateBefore(creationDate, 2013, 7, 1);
        } catch (ParseException exception) {
            return false;
        }
    }

    private static boolean shouldAttachBattlyAuthlib(JMinecraftVersionList.Version versionInfo) {
        if (versionInfo == null || !supportsGlobalUrlAgents(versionInfo)) {
            return false;
        }
        String id = ((versionInfo.id == null ? "" : versionInfo.id) + " "
                + (versionInfo.inheritsFrom == null ? "" : versionInfo.inheritsFrom))
                .toLowerCase(Locale.ROOT);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:^|[^0-9])(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
                .matcher(id);
        if (!matcher.find()) {
            return false;
        }
        int major = parseIntOrZero(matcher.group(1));
        int minor = parseIntOrZero(matcher.group(2));
        int patch = parseIntOrZero(matcher.group(3));
        return major == 1
                && (minor > 7 || (minor == 7 && patch >= 10))
                && minor < 17;
    }

    private static boolean supportsGlobalUrlAgents(JMinecraftVersionList.Version versionInfo) {
        if (versionInfo == null) {
            return false;
        }
        String mainClass = versionInfo.mainClass == null
                ? "" : versionInfo.mainClass.toLowerCase(Locale.ROOT);
        if (mainClass.contains("bootstraplauncher") || mainClass.contains("modlauncher")) {
            return false;
        }
        if (versionInfo.libraries == null) {
            return true;
        }
        for (DependentLibrary library : versionInfo.libraries) {
            if (library == null || library.name == null) {
                continue;
            }
            String name = library.name.toLowerCase(Locale.ROOT);
            if (name.contains("securejarhandler") || name.contains("bootstraplauncher")
                    || name.contains("modlauncher")) {
                return false;
            }
        }
        return true;
    }

    private static int parseIntOrZero(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /*
     * This is does not work when debugging. This is not reliable.
     * This is a monstrosity that races the mod, trying to ensure that when the folder is checked
     * after extraction but before dlopen, it is empty, so it loads the bundled SDL2 we have instead
     */
    private static void startControllableMitigation(Activity activity ,File gamedir) {
        String TAG = "ControllableMitigation";
        File deleted = new File(gamedir + "/controllable_natives/SDL");
        boolean hasControllable = false;
        File modsDir = new File(gamedir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if (mods != null) {
            for (File file : mods) {
                String name = file.getName();
                if (name.contains("controllable")) {
                    hasControllable = true;
                    break;
                }
            }
        }
        if (hasControllable) {
            Tools.runOnUiThread(() -> {
                Tools.dialog(activity, activity.getString(R.string.global_warning), activity.getString(R.string.controllableFound));
            });
            Thread mitigationThread = new Thread(() -> {
                // This is total garbage but it seems to be the best jank for the job
                Log.i(TAG, "Controllable detected! Starting mitigation thread");
                try {org.apache.commons.io.FileUtils.deleteDirectory(deleted);} catch (IOException ignored) {}
                while (!Thread.currentThread().isInterrupted()) {
                    // Looks for controllable_natives/SDL/<sdl_version_number>/libSDL2.so and
                    // deletes it. We can assume array index 0 because this dir gets fully deleted
                    // before the loop is started.
                    if (deleted.isDirectory()) {
                        if (deleted.listFiles().length > 0) {
                            if (deleted.listFiles()[0].listFiles().length > 0) {
                                if (deleted.listFiles()[0].listFiles()[0].exists()) {
                                    deleted.listFiles()[0].listFiles()[0].delete();
                                    break;
                                }
                            }
                        }
                    }
                }
                // We can end here because SdlNativeLibraryLoader only extracts libSDL2.so once
                // If NativeLibrary can't find it in the folder to load() it uses java.library.path
                Log.i(TAG, "Success! Ending Controllable crash mitigation..");
            });
            mitigationThread.start();
            controllableMitigationLogListener = loggedLine -> {
                // Hard off switch if it somehow didn't delete anything, just in case.
                if (loggedLine.contains("Sound engine started") && mitigationThread.isAlive()) {
                    Log.i(TAG, "Nothing happened. Ending Controllable crash mitigation..");
                    Logger.removeLogListener(controllableMitigationLogListener);
                    mitigationThread.interrupt();
                }
            };
            Logger.addLogListener(controllableMitigationLogListener);
        }
    }

    private static Logger.eventLogListener oldL4JMitigationLogListener;
    /// TODO: Remove when the time is right
    /**
     * Legacy4J for a long time had broken SDL detection for android, we need to check and
     * accommodate this for now. At least until the broken logic are on versions considered
     * obsolete.
     * <p>
     * This is of course, very jank, it does not work for anything below 1.7.5 but why is anyone
     * on that version anyway? Legacy4J has LTS for like all the versions.
     */
    private static void startOldLegacy4JMitigation(Activity activity, File gamedir) {
        boolean hasLegacy4J = false;
        File modsDir = new File(gamedir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if(mods != null) {
            for (File file : mods) {
                String name = file.getName();
                if (name.contains("Legacy4J")) {
                    hasLegacy4J = true;
                    break;
                }
            }
        }
        if (hasLegacy4J) {
            String TAG = "OldLegacy4JMitigation";
            Log.i(TAG, "Legacy4J detected!");
            oldL4JMitigationLogListener = loggedLine -> {
                if (LauncherPreferences.PREF_GAMEPAD_SDL_PASSTHRU && loggedLine.contains("literal{SDL3 (isXander's libsdl4j)} isn't supported in this system. GLFW will be used instead.")) {
                    Log.i(TAG, "Old version of Legacy4J detected! Force enabling SDL");
                    Tools.SDL.initializeControllerSubsystems();
                    Tools.runOnUiThread(() -> {
                        Tools.dialog(activity, activity.getString(R.string.global_warning), activity.getString(R.string.oldL4JFound));
                    });
                    Logger.removeLogListener(oldL4JMitigationLogListener);
                } else if (LauncherPreferences.PREF_GAMEPAD_SDL_PASSTHRU && loggedLine.contains("Added SDL Controller Mappings")) {
                    Log.i(TAG, "Fixed version of Legacy4J detected! Have fun!");
                    Logger.removeLogListener(oldL4JMitigationLogListener);
                }
            };
            Logger.addLogListener(oldL4JMitigationLogListener);
        }
    }

    public static File getGameDirPath(@NonNull MinecraftProfile minecraftProfile){
        if(minecraftProfile.gameDir != null){
            String relativePath = stripLauncherProfilePrefix(minecraftProfile.gameDir);
            if(relativePath != null)
                return new File(Tools.DIR_GAME_HOME, relativePath);
            else
                return new File(Tools.DIR_GAME_HOME,minecraftProfile.gameDir);
        }
        return new File(Tools.DIR_GAME_NEW);
    }

    private static boolean canAttachJavaAgentForRuntime(net.kdt.pojavlaunch.multirt.Runtime runtime, File agentFile, String classEntry) {
        if (!agentFile.exists()) {
            return false;
        }
        int maxClassMajor = getMaxClassMajorForJava(runtime.javaVersion);
        int agentClassMajor = readJarClassMajor(agentFile, classEntry);
        if (agentClassMajor < 0) {
            Log.w("JavaAgent", "Skipping " + agentFile.getName() + ": could not read " + classEntry);
            return false;
        }
        if (agentClassMajor > maxClassMajor) {
            Log.w("JavaAgent", "Skipping " + agentFile.getName() + ": class version " + agentClassMajor
                    + " is newer than Java " + runtime.javaVersion + " supports (" + maxClassMajor + ")");
            return false;
        }
        return true;
    }

    private static int getMaxClassMajorForJava(int javaVersion) {
        if (javaVersion <= 8) return 52;
        if (javaVersion <= 11) return 55;
        if (javaVersion <= 17) return 61;
        if (javaVersion <= 21) return 65;
        if (javaVersion <= 25) return 69;
        return javaVersion + 44;
    }

    private static int readJarClassMajor(File jarFile, String classEntry) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.jar.JarEntry entry = jar.getJarEntry(classEntry);
            if (entry == null) return -1;
            try (java.io.InputStream inputStream = jar.getInputStream(entry)) {
                byte[] header = new byte[8];
                int read = inputStream.read(header);
                if (read != header.length) return -1;
                return ((header[6] & 0xff) << 8) | (header[7] & 0xff);
            }
        } catch (IOException e) {
            Log.w("JavaAgent", "Could not inspect " + jarFile.getAbsolutePath(), e);
            return -1;
        }
    }

    public static void buildNotificationChannel(Context context){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                context.getString(R.string.notif_channel_id),
                context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.createNotificationChannel(channel);
    }
    public static void disableSplash(File dir) {
        File configDir = new File(dir, "config");
        if(FileUtils.ensureDirectorySilently(configDir)) {
            File forgeSplashFile = new File(dir, "config/splash.properties");
            String forgeSplashContent = "enabled=true";
            try {
                if (forgeSplashFile.exists()) {
                    forgeSplashContent = Tools.read(forgeSplashFile.getAbsolutePath());
                }
                if (forgeSplashContent.contains("enabled=true")) {
                    Tools.write(forgeSplashFile.getAbsolutePath(),
                            forgeSplashContent.replace("enabled=true", "enabled=false"));
                }
            } catch (IOException e) {
                Log.w(Tools.APP_NAME, "Could not disable Forge 1.12.2 and below splash screen!", e);
            }
        } else {
            Log.w(Tools.APP_NAME, "Failed to create the configuration directory");
        }
    }

    public static void getCacioJavaArgs(List<String> javaArgList, boolean isJava8, Activity activity) {
        getCacioJavaArgs(javaArgList, isJava8, activity, true);
    }

    public static void getCacioJavaArgs(List<String> javaArgList, boolean isJava8, Activity activity, boolean headless) {
        // headless=true makes Toolkit.loadLibraries() use libawt_headless.so instead of libawt_xawt.so.
        // libawt_xawt.so is the X11 library — absent/non-functional on Android and broken in JRE 25.
        // The cacio agent (CTCPreloadAgent/CTCToolkit) installs itself after Toolkit init, so rendering
        // still works via cacio's own pipeline regardless of this flag.
        javaArgList.add("-Djava.awt.headless=" + headless);
        javaArgList.add("-Dcacio.managed.screensize=" + AWTCanvasView.AWT_CANVAS_WIDTH + "x" + AWTCanvasView.AWT_CANVAS_HEIGHT);
        javaArgList.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager");
        javaArgList.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler");
        javaArgList.add("-Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel");
        if (isJava8) {
            javaArgList.add("-Dawt.toolkit=net.java.openjdk.cacio.ctc.CTCToolkit");
            javaArgList.add("-Djava.awt.graphicsenv=net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment");
        } else {
            File caciocavavallo17Dir = new File(Tools.DIR_GAME_HOME, "caciocavallo17");
            File[] caciocavallo17Jars = caciocavavallo17Dir.listFiles((f, s) ->s.contains("cacio-tta"));
            if(caciocavallo17Jars == null || caciocavallo17Jars.length < 1) {
            // We wanna avoid the launch being interrupted so we extract again if it isn't found
                AsyncAssetManager.unpackComponents(activity);
                caciocavallo17Jars = caciocavavallo17Dir.listFiles((f, s) ->s.contains("cacio-tta"));
                if(caciocavallo17Jars == null || caciocavallo17Jars.length < 1)
                    throw new RuntimeException("Failed to extract required assets!");
            }
            javaArgList.add("-javaagent:"+caciocavallo17Jars[0].getAbsolutePath());
            javaArgList.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit");
            javaArgList.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment");
            // This approach breaks kilt so we use an agent instead
//          javaArgList.add("-Djava.system.class.loader=com.github.caciocavallosilano.cacio.ctc.CTCPreloadClassLoader");
            javaArgList.add("--add-exports=java.desktop/java.awt=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/sun.java2d=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.desktop/sun.font=ALL-UNNAMED");
            javaArgList.add("--add-exports=java.base/sun.security.action=ALL-UNNAMED");
            javaArgList.add("--add-opens=java.base/java.util=ALL-UNNAMED");
            javaArgList.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED");
            javaArgList.add("--add-opens=java.desktop/sun.font=ALL-UNNAMED");
            javaArgList.add("--add-opens=java.desktop/sun.java2d=ALL-UNNAMED");
            javaArgList.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");

            // Opens the java.net package to Arc DNS injector on Java 9+
            javaArgList.add("--add-opens=java.base/java.net=ALL-UNNAMED");
        }

        StringBuilder cacioClasspath = new StringBuilder();
        cacioClasspath.append("-Xbootclasspath/").append(isJava8 ? "p" : "a");
        File cacioDir = new File(DIR_GAME_HOME + "/caciocavallo" + (isJava8 ? "" : "17"));
        File[] cacioFiles = cacioDir.listFiles();
        if (cacioFiles != null) {
            for (File file : cacioFiles) {
                if (file.getName().endsWith(".jar")) {
                    cacioClasspath.append(":").append(file.getAbsolutePath());
                }
            }
        }
        javaArgList.add(cacioClasspath.toString());
    }

    private static void addNativeLibraryPathOverrides(List<String> javaArgList, String versionId) {
        removeJvmProperty(javaArgList, "-Djava.library.path=");
        removeJvmProperty(javaArgList, "-Dorg.lwjgl.librarypath=");

        String lwjglDir = Tools.isValidString(lwjglNativesDir) ? lwjglNativesDir : Tools.NATIVE_LIB_DIR;
        File versionSpecificNativesDir = new File(Tools.DIR_CACHE, "natives/" + versionId);
        StringBuilder javaLibraryPath = new StringBuilder();
        javaLibraryPath.append(lwjglDir).append(":").append(Tools.NATIVE_LIB_DIR);
        if (versionSpecificNativesDir.exists()) {
            javaLibraryPath.append(":").append(versionSpecificNativesDir.getAbsolutePath());
        }

        javaArgList.add("-Djava.library.path=" + javaLibraryPath);
        javaArgList.add("-Dorg.lwjgl.librarypath=" + lwjglDir);
    }

    private static void removeJvmProperty(List<String> javaArgList, String propertyPrefix) {
        for (int i = javaArgList.size() - 1; i >= 0; i--) {
            if (javaArgList.get(i).startsWith(propertyPrefix)) {
                javaArgList.remove(i);
            }
        }
    }

    public static String[] getMinecraftJVMArgs(String versionName, File gameDir) {
        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(versionName, true);
        // Parse additional JVM Arguments from the version JSON (Forge 1.17+, LabyMod 4, etc.)
        if (versionInfo.arguments == null || versionInfo.arguments.jvm == null) {
            return new String[0];
        }

        Map<String, String> varArgMap = new ArrayMap<>();
        varArgMap.put("classpath_separator", ":");
        varArgMap.put("library_directory", DIR_HOME_LIBRARY);
        varArgMap.put("version_name", versionInfo.id);
        varArgMap.put("launcher_name", Tools.APP_NAME);
        varArgMap.put("launcher_version", BuildConfig.VERSION_NAME);
        File nativesDirectory = new File(Tools.DIR_CACHE, "natives/" + versionName);
        //noinspection ResultOfMethodCallIgnored
        nativesDirectory.mkdirs();
        varArgMap.put("natives_directory", nativesDirectory.getAbsolutePath());

        List<String> minecraftArgs = new ArrayList<>();
        boolean skipNext = false;
        for (Object arg : versionInfo.arguments.jvm) {
            if (arg instanceof String) {
                String argStr = (String) arg;
                if (skipNext) {
                    skipNext = false;
                    continue;
                }
                if (argStr.startsWith("-Djava.library.path=")
                        || argStr.startsWith("-Dorg.lwjgl.librarypath=")) {
                    continue;
                }
                // When not inheriting from another version, the launcher builds its own
                // classpath. Skip -cp/-classpath to avoid overriding it.
                if (versionInfo.inheritsFrom == null && (argStr.equals("-cp") || argStr.equals("-classpath"))) {
                    skipNext = true;
                    continue;
                }
                minecraftArgs.add(argStr);
            } //TODO: implement rules-based args (?maybe?)
        }
        return JSONUtils.insertJSONValueList(minecraftArgs.toArray(new String[0]), varArgMap);
    }

    public static String[] getMinecraftClientArgs(MinecraftAccount profile, JMinecraftVersionList.Version versionInfo, File gameDir) {
        String username = profile.username.replace("Demo.", "");
        String versionName = versionInfo.id;
        if (versionInfo.inheritsFrom != null) {
            versionName = versionInfo.inheritsFrom;
        }

        String userType = "mojang";
        try {
            Date creationDate = DateUtils.getOriginalReleaseDate(versionInfo);
            // Minecraft 22w43a which adds chat reporting (and signing) was released on
            // 26th October 2022. So, if the date is not before that (meaning it is equal or higher)
            // change the userType to MSA to fix the missing signature
            if(creationDate != null && !DateUtils.dateBefore(creationDate, 2022, 9, 26)) {
                userType = "msa";
            }
        }catch (ParseException e) {
            Log.e("CheckForProfileKey", "Failed to determine profile creation date, using \"mojang\"", e);
        }

        String launchProfileId = profile.getProfileIdForLaunch();
        if (!isValidString(launchProfileId)) {
            launchProfileId = "00000000-0000-0000-0000-000000000000";
        }
        if (profile.isBattly()) {
            Logger.appendToLog("Info: Battly launch UUID: " + launchProfileId);
        }

        Map<String, String> varArgMap = new ArrayMap<>();
        varArgMap.put("auth_session", profile.accessToken); // For legacy versions of MC
        varArgMap.put("auth_access_token", profile.accessToken);
        varArgMap.put("auth_player_name", username);
        varArgMap.put("auth_uuid", launchProfileId.replace("-", ""));
        varArgMap.put("auth_xuid", profile.xuid);
        varArgMap.put("assets_root", Tools.ASSETS_PATH);
        varArgMap.put("assets_index_name", versionInfo.assets);
        varArgMap.put("game_assets", Tools.ASSETS_PATH);
        varArgMap.put("game_directory", gameDir.getAbsolutePath());
        varArgMap.put("user_properties", "{}");
        varArgMap.put("user_type", userType);
        varArgMap.put("version_name", versionName);
        varArgMap.put("version_type", isValidString(versionInfo.type) ? versionInfo.type : "release");

        List<String> minecraftArgs = new ArrayList<>();
        if (versionInfo.arguments != null) {
            // Support Minecraft 1.13+
            for (Object arg : versionInfo.arguments.game) {
                if (arg instanceof String) {
                    minecraftArgs.add((String) arg);
                } //TODO: implement else clause
            }
        }

        String mcArguments = versionInfo.minecraftArguments == null ?
                fromStringArray(minecraftArgs.toArray(new String[0])):
                versionInfo.minecraftArguments;

        if(profile.isDemo()) mcArguments += " --demo";

        String[] resolvedArgs = JSONUtils.insertJSONValueList(splitAndFilterEmpty(mcArguments), varArgMap);
        return net.kdt.pojavlaunch.battlysocial.BattlySocialManager
                .appendPendingServerArgs(resolvedArgs);
    }

    private static void clearMinecraftSkinCache() {
        File minecraftSkins = new File(ASSETS_PATH, "skins");
        clearDirectory(minecraftSkins);

        // Older Battly Mobile builds temporarily used a root-level skin cache.
        // Keep cleaning it so users upgrading from those builds do not retain stale skins.
        clearDirectory(new File(DIR_GAME_HOME, "assets/skins"));
    }

    private static void clearDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        try {
            org.apache.commons.io.FileUtils.cleanDirectory(directory);
            Logger.appendToLog("Info: Cleared cache directory: " + directory.getAbsolutePath());
        } catch (IOException e) {
            Log.w(APP_NAME, "Failed to clear cache directory " + directory.getAbsolutePath(), e);
            Logger.appendToLog("Warning: Could not clear cache directory: " + directory.getAbsolutePath());
        }
    }

    private static void applyLowEndMinecraftOptions(File gameDir) {
        if (LauncherPreferences.PREF_RAM_ALLOCATION > 1280) {
            return;
        }
        try {
            MCOptionUtils.load(gameDir.getAbsolutePath());
            capIntegerOption("renderDistance", 6);
            capIntegerOption("renderDistanceChunks", 6);
            setIfMissing("mipmapLevels", "0");
            setIfMissing("particles", "1");
            setIfMissing("clouds", "false");
            setIfMissing("entityShadows", "false");
            capIntegerOption("maxFps", 60);
            MCOptionUtils.save();
            Logger.appendToLog("Info: Applied low-end Minecraft option defaults");
        } catch (Throwable throwable) {
            Log.w(APP_NAME, "Could not apply low-end Minecraft options", throwable);
        }
    }

    private static void capIntegerOption(String key, int maxValue) {
        String value = MCOptionUtils.get(key);
        if (!isValidString(value)) {
            MCOptionUtils.set(key, String.valueOf(maxValue));
            return;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed > maxValue) {
                MCOptionUtils.set(key, String.valueOf(maxValue));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static void setIfMissing(String key, String value) {
        if (!isValidString(MCOptionUtils.get(key))) {
            MCOptionUtils.set(key, value);
        }
    }

    private static void augmentCustomClientLaunchArgs(JMinecraftVersionList.Version versionInfo, List<String> launchArgs) {
        if (!isLabyModLaunchWrapperVersion(versionInfo)) {
            return;
        }

        String dummyMinecraftClass = getDummyMinecraftMainClass(versionInfo);
        ensureLaunchArgValue(launchArgs, "--dummyMinecraftClass", dummyMinecraftClass);
        addLaunchArgValueIfMissing(launchArgs, "--tweakClass", "net.kdt.patch.LabyModPatchTweaker");
        Log.i(APP_NAME, "Applied LabyMod launch compatibility with dummy main class " + dummyMinecraftClass);
    }

    private static boolean isLabyModLaunchWrapperVersion(JMinecraftVersionList.Version versionInfo) {
        if (versionInfo == null || versionInfo.libraries == null) {
            return false;
        }
        if (!"net.minecraft.launchwrapper.Launch".equals(versionInfo.mainClass)) {
            return false;
        }
        for (DependentLibrary library : versionInfo.libraries) {
            if (library == null || library.name == null) {
                continue;
            }
            if (library.name.toLowerCase(Locale.ROOT).contains("labymod")) {
                return true;
            }
        }
        return false;
    }

    private static String getDummyMinecraftMainClass(JMinecraftVersionList.Version versionInfo) {
        String defaultMainClass = "net.minecraft.client.main.Main";
        if (versionInfo == null || !isValidString(versionInfo.inheritsFrom)) {
            return defaultMainClass;
        }
        try {
            JMinecraftVersionList.Version inheritedVersion = getVersionInfo(versionInfo.inheritsFrom, true);
            if (inheritedVersion != null && isValidString(inheritedVersion.mainClass)) {
                return inheritedVersion.mainClass;
            }
        } catch (Throwable throwable) {
            Log.w(APP_NAME, "Failed to resolve inherited main class for " + versionInfo.id, throwable);
        }
        return defaultMainClass;
    }

    private static void ensureLaunchArgValue(List<String> launchArgs, String argName, String argValue) {
        if (!isValidString(argValue)) {
            return;
        }
        int argIndex = launchArgs.indexOf(argName);
        if (argIndex >= 0) {
            if (argIndex + 1 < launchArgs.size()) {
                launchArgs.set(argIndex + 1, argValue);
            } else {
                launchArgs.add(argValue);
            }
            return;
        }
        launchArgs.add(argName);
        launchArgs.add(argValue);
    }

    private static void addLaunchArgValueIfMissing(List<String> launchArgs, String argName, String argValue) {
        if (!isValidString(argValue)) {
            return;
        }
        for (int i = 0; i + 1 < launchArgs.size(); i++) {
            if (argName.equals(launchArgs.get(i)) && argValue.equals(launchArgs.get(i + 1))) {
                return;
            }
        }
        launchArgs.add(argName);
        launchArgs.add(argValue);
    }

    private static String appendLegacyMixinClasspathIfNeeded(File gameDir, JMinecraftVersionList.Version versionInfo,
                                                             String launchClassPath) {
        if (gameDir == null || versionInfo == null
                || !"net.minecraft.launchwrapper.Launch".equals(versionInfo.mainClass)
                || !legacyModsRequestSpongeMixin(gameDir)) {
            return launchClassPath;
        }
        File provider = findLegacyMixinProvider(gameDir);
        if (provider == null) {
            Logger.appendToLog("Warning: A legacy Forge mod requests org.spongepowered.asm.launch.MixinTweaker, "
                    + "but no Mixin/UniMixins provider jar was found in mods or libraries.");
            return launchClassPath;
        }
        Logger.appendToLog("Info: Added legacy Mixin provider to classpath: " + provider.getName());
        return provider.getAbsolutePath() + ":" + launchClassPath;
    }

    private static void ensureLegacyMixinProvider(Activity activity, File gameDir,
                                                  JMinecraftVersionList.Version versionInfo) {
        if (gameDir == null || versionInfo == null
                || !"net.minecraft.launchwrapper.Launch".equals(versionInfo.mainClass)
                || !legacyModsRequestSpongeMixin(gameDir)
                || findLegacyMixinProvider(gameDir) != null) {
            return;
        }
        throw new IllegalStateException(activity.getString(R.string.legacy_mixin_missing_message));
    }

    private static boolean legacyModsRequestSpongeMixin(File gameDir) {
        File modsDir = new File(gameDir, "mods");
        File[] mods = modsDir.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (mods == null) {
            return false;
        }
        for (File mod : mods) {
            try (JarFile jarFile = new JarFile(mod)) {
                Manifest manifest = jarFile.getManifest();
                String tweakClass = manifest == null ? null : manifest.getMainAttributes().getValue("TweakClass");
                if (tweakClass != null && tweakClass.contains("org.spongepowered.asm.launch.MixinTweaker")
                        && jarFile.getEntry("org/spongepowered/asm/launch/MixinTweaker.class") == null) {
                    Logger.appendToLog("Info: Legacy Mixin tweaker requested by " + mod.getName());
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static File findLegacyMixinProvider(File gameDir) {
        File inMods = findJarContainingClass(new File(gameDir, "mods"),
                "org/spongepowered/asm/launch/MixinTweaker.class");
        if (inMods != null) {
            return inMods;
        }
        File inLibraries = findJarContainingClass(new File(DIR_HOME_LIBRARY),
                "org/spongepowered/asm/launch/MixinTweaker.class");
        if (inLibraries != null) {
            return inLibraries;
        }
        File modsDir = new File(gameDir, "mods");
        File[] candidates = modsDir.listFiles(file -> {
            String name = file.getName().toLowerCase(Locale.ROOT);
            return file.isFile() && name.endsWith(".jar")
                    && (name.contains("unimixins") || name.contains("mixin") || name.contains("sponge"));
        });
        return candidates == null || candidates.length == 0 ? null : candidates[0];
    }

    private static File findJarContainingClass(File root, String classPath) {
        if (root == null || !root.exists()) {
            return null;
        }
        File[] files = root.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File nested = findJarContainingClass(file, classPath);
                if (nested != null) {
                    return nested;
                }
                continue;
            }
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            try (JarFile jarFile = new JarFile(file)) {
                if (jarFile.getEntry(classPath) != null) {
                    return file;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static String fromStringArray(String[] strArr) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < strArr.length; i++) {
            if (i > 0) builder.append(" ");
            builder.append(strArr[i]);
        }

        return builder.toString();
    }

    private static String[] splitAndFilterEmpty(String argStr) {
        List<String> strList = new ArrayList<>();
        for (String arg : argStr.split(" ")) {
            if (!arg.isEmpty()) {
                strList.add(arg);
            }
        }
        //strList.add("--fullscreen");
        return strList.toArray(new String[0]);
    }

    public static String artifactToPath(DependentLibrary library) {
        if (library.downloads != null &&
            library.downloads.artifact != null &&
            library.downloads.artifact.path != null)
            return library.downloads.artifact.path;
        String[] libInfos = library.name.split(":");
        return libInfos[0].replaceAll("\\.", "/") + "/" + libInfos[1] + "/" + libInfos[2] + "/" + libInfos[1] + "-" + libInfos[2] + (libInfos.length == 4 ? "-" + libInfos[3] : "") + ".jar";
    }

    public static String getClientClasspath(String version) {
        return DIR_HOME_VERSION + "/" + version + "/" + version + ".jar";
    }

    /**
     * Creates or updates lwjgl-android-natives.jar in the lwjgl3 directory.
     * This JAR places Android-compatible LWJGL natives at the paths LWJGL 3 expects
     * (linux/x64/org/lwjgl/<module>/<lib>.so). Because getLWJGL3ClassPath() is
     * prepended to the JVM -cp before LabyMod's tweaker adds its native JARs via
     * addURL(), Library.findResource() will find these Android-compatible natives first,
     * preventing the libdl.so.2 UnsatisfiedLinkError that desktop Linux natives cause.
     *
     * @return true if the JAR was newly created or updated (stale LWJGL cache should be cleared)
     */
    private static boolean ensureAndroidNativesJar(File lwjgl3Folder) {
        // Map: path inside JAR  →  filename in NATIVE_LIB_DIR
        String[][] libEntries = {
            {"linux/x64/org/lwjgl/liblwjgl.so",               "liblwjgl.so"},
            {"linux/x64/org/lwjgl/opengl/liblwjgl_opengl.so", "liblwjgl_opengl.so"},
            {"linux/x64/org/lwjgl/stb/liblwjgl_stb.so",       "liblwjgl_stb.so"},
            {"linux/x64/org/lwjgl/nanovg/liblwjgl_nanovg.so", "liblwjgl_nanovg.so"},
            {"linux/x64/org/lwjgl/tinyfd/liblwjgl_tinyfd.so", "liblwjgl_tinyfd.so"},
            {"linux/x64/org/lwjgl/openal/libopenal.so",        "libopenal.so"},
            {"linux/x64/org/lwjgl/shaderc/libshaderc.so",      "libshaderc.so"},
            {"linux/x64/org/lwjgl/vma/liblwjgl_vma.so",        "liblwjgl_vma.so"},
        };
        File outJar = new File(lwjgl3Folder, "lwjgl-android-natives.jar");
        File selectedNativeDir = new File(resolveLwjglNativesDir(sLwjglVersion));
        File refLib = new File(selectedNativeDir, "liblwjgl.so");
        if (!refLib.exists()) return false;
        if (isAndroidNativesJarCurrent(outJar, libEntries, refLib)) return false;

        lwjgl3Folder.mkdirs();
        File tmpJar = new File(lwjgl3Folder, "lwjgl-android-natives.jar.tmp");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(tmpJar))) {
            for (String[] entry : libEntries) {
                File selectedFile = new File(selectedNativeDir, entry[1]);
                File soFile = selectedFile.exists() ? selectedFile : new File(NATIVE_LIB_DIR, entry[1]);
                if (!soFile.exists()) {
                    Log.w(APP_NAME, "Android LWJGL native not found, skipping: " + entry[1]);
                    continue;
                }
                jos.putNextEntry(new JarEntry(entry[0]));
                try (FileInputStream fis = new FileInputStream(soFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = fis.read(buf)) != -1) jos.write(buf, 0, len);
                }
                jos.closeEntry();
            }
        } catch (IOException e) {
            Log.e(APP_NAME, "Failed to create Android LWJGL natives JAR", e);
            tmpJar.delete();
            return false;
        }
        outJar.delete();
        if (!tmpJar.renameTo(outJar)) {
            Log.e(APP_NAME, "Failed to rename Android LWJGL natives JAR");
            tmpJar.delete();
            return false;
        }
        Log.d(APP_NAME, "Created Android LWJGL natives JAR: " + outJar);
        // Clear stale LWJGL cache so desktop-extracted libs are not reused.
        // LWJGL will re-extract from our Android natives JAR on next launch.
        File[] cacheDirs = DIR_CACHE.listFiles(f -> f.isDirectory() && f.getName().startsWith("lwjgl_"));
        if (cacheDirs != null) {
            for (File d : cacheDirs) {
                try { org.apache.commons.io.FileUtils.deleteDirectory(d); }
                catch (IOException ignored) {}
            }
        }
        return true;
    }

    private static boolean isAndroidNativesJarCurrent(File jarFile, String[][] requiredEntries, File refLib) {
        if (!jarFile.exists() || jarFile.lastModified() < refLib.lastModified()) {
            return false;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            for (String[] entry : requiredEntries) {
                File soFile = new File(NATIVE_LIB_DIR, entry[1]);
                if (soFile.exists() && jar.getJarEntry(entry[0]) == null) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            Log.w(APP_NAME, "Invalid Android LWJGL natives JAR, regenerating: " + jarFile, exception);
            return false;
        }
    }

    private static String getLWJGL3ClassPath(Activity activity,
                                             String launchClassPath,
                                             boolean useAndroidNarratorAdapter) {
        StringBuilder libStr = new StringBuilder();
        String internalLwjglVersion = getInternalLwjglVersion(iLwjglVersion);
        sLwjglVersion = internalLwjglVersion;
        lwjglNativesDir = resolveLwjglNativesDir(sLwjglVersion);

        File lwjgl3Folder = new File(Tools.DIR_GAME_HOME, "lwjgl3/" + internalLwjglVersion);
        ensureAndroidNativesJar(lwjgl3Folder);
        appendClasspathFile(libStr, new File(lwjgl3Folder, "lwjgl.jar"));
        appendClasspathFile(libStr, new File(lwjgl3Folder,
                "lwjgl-" + internalLwjglVersion + "-merged-modules.jar"));
        File lwjglSdl = new File(lwjgl3Folder, "lwjgl-sdl.jar");
        if (iLwjglVersion >= 341 && !lwjglSdl.isFile()) {
            throw new IllegalStateException("Required LWJGL SDL module is missing: " + lwjglSdl);
        }
        if (iLwjglVersion >= 341) appendClasspathFile(libStr, lwjglSdl);
        appendClasspathFile(libStr, new File(lwjgl3Folder, "lwjgl-android-natives.jar"));
        if (useAndroidNarratorAdapter) {
            appendClasspathFile(libStr,
                    new File(Tools.DIR_GAME_HOME, "lwjgl3/android-text2speech-stub.jar"));
        }

        File[] lwjgl3Files = lwjgl3Folder.listFiles();
        if (lwjgl3Files != null) {
            for (File file: lwjgl3Files) {
                String fileName = file.getName();
                if (fileName.endsWith(".jar")
                        && !fileName.equals("lwjgl.jar")
                        && !fileName.equals("lwjgl-" + internalLwjglVersion + "-merged-modules.jar")
                        && !fileName.equals("lwjgl-sdl.jar")
                        && !fileName.equals("lwjgl-android-natives.jar")
                        && !fileName.endsWith("lwjglx.jar")) {
                    appendClasspathFile(libStr, file);
                }
            }
        }
        if (iLwjglVersion <= 299 || requiresLwjgl2Compatibility(launchClassPath)) {
            File lwjgl2Compatibility = new File(lwjgl3Folder, "lwjgl-lwjglx.jar");
            if (!lwjgl2Compatibility.isFile()) {
                Logger.appendToLog("Info: Repairing the missing LWJGL 2 compatibility bridge");
                AsyncAssetManager.unpackComponents(activity);
            }
            if (!lwjgl2Compatibility.isFile()) {
                throw new IllegalStateException(
                        "The LWJGL 2 compatibility bridge could not be installed: "
                                + lwjgl2Compatibility);
            }
            appendClasspathFile(libStr, lwjgl2Compatibility);
        }
        // Remove the ':' at the end (guard against empty folder)
        if (libStr.length() > 0) libStr.setLength(libStr.length() - 1);
        return libStr.toString();
    }

    private static boolean requiresLwjgl2Compatibility(String launchClassPath) {
        if (launchClassPath == null || launchClassPath.isEmpty()) return false;
        for (String entry : launchClassPath.split(Pattern.quote(File.pathSeparator))) {
            String normalized = entry.replace('\\', '/').toLowerCase(Locale.ROOT);
            String name = new File(entry).getName().toLowerCase(Locale.ROOT);
            if (normalized.contains("/org/lwjgl/lwjgl/")
                    || name.matches("lwjgl-2(?:\\.[0-9]+)+\\.jar")) {
                Logger.appendToLog("Info: LWJGL 2 compatibility API enabled for " + name);
                return true;
            }
        }
        return false;
    }

    private static String stripMojangTextToSpeech(String classPath) {
        if (classPath == null || classPath.isEmpty()) return classPath;
        StringBuilder filtered = new StringBuilder();
        for (String entry : classPath.split(Pattern.quote(File.pathSeparator))) {
            String name = new File(entry).getName().toLowerCase(Locale.ROOT);
            if (name.equals("text2speech.jar") || name.startsWith("text2speech-")) {
                Logger.appendToLog("Info: Replaced desktop text2speech library with the Android narrator adapter: "
                        + name);
                continue;
            }
            if (filtered.length() > 0) filtered.append(File.pathSeparator);
            filtered.append(entry);
        }
        return filtered.toString();
    }

    static List<String> stripMojangTextToSpeechFromJvmArgs(List<String> arguments) {
        List<String> filtered = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if ("-p".equals(argument) || "--module-path".equals(argument)) {
                if (i + 1 >= arguments.size()) {
                    continue;
                }
                String modulePath = stripMojangTextToSpeech(arguments.get(++i));
                if (!modulePath.isEmpty()) {
                    filtered.add(argument);
                    filtered.add(modulePath);
                }
                continue;
            }
            if (argument.startsWith("--module-path=")) {
                String modulePath = stripMojangTextToSpeech(
                        argument.substring("--module-path=".length()));
                if (!modulePath.isEmpty()) {
                    filtered.add("--module-path=" + modulePath);
                }
                continue;
            }
            if (isMojangTextToSpeechPath(argument)) {
                Logger.appendToLog(
                        "Info: Removed desktop text2speech JVM path: "
                                + new File(argument).getName());
                continue;
            }
            filtered.add(argument);
        }
        return filtered;
    }

    private static boolean isMojangTextToSpeechPath(String value) {
        if (value == null || value.isEmpty()) return false;
        String name = new File(value).getName().toLowerCase(Locale.ROOT);
        return name.equals("text2speech.jar") || name.startsWith("text2speech-");
    }

    private static void appendClasspathFile(StringBuilder classPath, File file) {
        if (file.exists()) {
            classPath.append(file.getAbsolutePath()).append(":");
        } else {
            Log.w(APP_NAME, "LWJGL classpath file missing: " + file.getAbsolutePath());
        }
    }

    private final static boolean isClientFirst = false;
    public static String generateLaunchClassPath(JMinecraftVersionList.Version info, String actualname) {
        StringBuilder finalClasspath = new StringBuilder(); //versnDir + "/" + version + "/" + version + ".jar:";

        String[] classpath = generateLibClasspath(info);

        if (isClientFirst) {
            finalClasspath.append(getClientClasspath(actualname));
        }
        for (String jarFile : classpath) {
            if (!FileUtils.exists(jarFile)) {
                Log.d(APP_NAME, "Ignored non-exists file: " + jarFile);
                continue;
            }
            finalClasspath.append((isClientFirst ? ":" : "")).append(jarFile).append(!isClientFirst ? ":" : "");
        }
        if (!isClientFirst) {
            finalClasspath.append(getClientClasspath(actualname));
        }

        return finalClasspath.toString();
    }





    public static DisplayMetrics getDisplayMetrics(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();

        if(SDK_INT >= Build.VERSION_CODES.N && (activity.isInMultiWindowMode() || activity.isInPictureInPictureMode())){
            //For devices with free form/split screen, we need window size, not screen size.
            displayMetrics = activity.getResources().getDisplayMetrics();
        }else{
            if (SDK_INT >= Build.VERSION_CODES.R) {
                activity.getDisplay().getRealMetrics(displayMetrics);
            } else { // Removed the clause for devices with unofficial notch support, since it also ruins all devices with virtual nav bars before P
                activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
            }
            if(!PREF_IGNORE_NOTCH){
                //Remove notch width when it isn't ignored.
                if(activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                    displayMetrics.heightPixels -= PREF_NOTCH_SIZE;
                else
                    displayMetrics.widthPixels -= PREF_NOTCH_SIZE;
            }
        }
        currentDisplayMetrics = displayMetrics;
        return displayMetrics;
    }

    public static void setFullscreen(Activity activity, boolean fullscreen) {
        final View decorView = activity.getWindow().getDecorView();
        View.OnSystemUiVisibilityChangeListener visibilityChangeListener = visibility -> {
            boolean multiWindowMode = SDK_INT >= 24 && activity.isInMultiWindowMode();
            // When in multi-window mode, asking for fullscreen makes no sense (cause the launcher runs in a window)
            // So, ignore the fullscreen setting when activity is in multi window mode
            if(fullscreen && !multiWindowMode){
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                }
            }else{
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }

        };
        decorView.setOnSystemUiVisibilityChangeListener(visibilityChangeListener);
        visibilityChangeListener.onSystemUiVisibilityChange(decorView.getSystemUiVisibility()); //call it once since the UI state may not change after the call, so the activity wont become fullscreen
    }

    public static DisplayMetrics currentDisplayMetrics;

    public static void updateWindowSize(Activity activity) {
        currentDisplayMetrics = getDisplayMetrics(activity);

        View dimensionView = activity.findViewById(R.id.dimension_tracker);

        if(dimensionView != null) {
            int width = dimensionView.getWidth();
            int height = dimensionView.getHeight();
            if(width != 0 && height != 0) {
                if (width < height) {
                    int tmp = width;
                    width = height;
                    height = tmp;
                }
                Log.i("Tools", "Using dimension_tracker for display dimensions; W="+width+" H="+height);
                CallbackBridge.physicalWidth = width;
                CallbackBridge.physicalHeight = height;
                return;
            }else{
                Log.e("Tools","Dimension tracker detected but dimensions out of date. Please check usage.", new Exception());
            }
        }

        CallbackBridge.physicalWidth = Math.max(currentDisplayMetrics.widthPixels, currentDisplayMetrics.heightPixels);
        CallbackBridge.physicalHeight = Math.min(currentDisplayMetrics.widthPixels, currentDisplayMetrics.heightPixels);
    }

    public static float dpToPx(float dp) {
        //Better hope for the currentDisplayMetrics to be good
        return dp * currentDisplayMetrics.density;
    }

    public static float pxToDp(float px){
        //Better hope for the currentDisplayMetrics to be good
        return px / currentDisplayMetrics.density;
    }

    public static void copyAssetFile(Context ctx, String fileName, String output, boolean overwrite) throws IOException {
        copyAssetFile(ctx, fileName, output, new File(fileName).getName(), overwrite);
    }

    public static void copyAssetFile(Context ctx, String fileName, String output, String outputName, boolean overwrite) throws IOException {
        File parentFolder = new File(output);
        FileUtils.ensureDirectory(parentFolder);
        File destinationFile = new File(output, outputName);
        if(!destinationFile.exists() || overwrite){
            try(InputStream inputStream = ctx.getAssets().open(fileName)) {
                try (OutputStream outputStream = new FileOutputStream(destinationFile)){
                    IOUtils.copy(inputStream, outputStream);
                }
            }
        }
    }

    public static String printToString(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        printWriter.close();
        return stringWriter.toString();
    }

    public static void showError(Context ctx, Throwable e) {
        showError(ctx, e, false);
    }

    public static void showError(final Context ctx, final Throwable e, final boolean exitIfOk) {
        showError(ctx, R.string.global_error, null ,e, exitIfOk, false);
    }
    public static void showError(final Context ctx, final int rolledMessage, final Throwable e) {
        showError(ctx, R.string.global_error, ctx.getString(rolledMessage), e, false, false);
    }
    public static void showError(final Context ctx, final String rolledMessage, final Throwable e) {
        showError(ctx, R.string.global_error, rolledMessage, e, false, false);
    }
    public static void showError(final Context ctx, final String rolledMessage, final Throwable e, boolean exitIfOk) {
        showError(ctx, R.string.global_error, rolledMessage, e, exitIfOk, false);
    }
    public static void showError(final Context ctx, final int titleId, final Throwable e, final boolean exitIfOk) {
        showError(ctx, titleId, null, e, exitIfOk, false);
    }

    private static void showError(final Context ctx, final int titleId, final String rolledMessage, final Throwable e, final boolean exitIfOk, final boolean showMore) {
        if(e instanceof ContextExecutorTask) {
            ContextExecutor.execute((ContextExecutorTask) e);
            return;
        }
        e.printStackTrace();

        Runnable runnable = () -> {
            final String errMsg = showMore ? printToString(e) : rolledMessage != null ? rolledMessage : e.getMessage();
            AlertDialog.Builder builder = createStyledDialogBuilder(ctx)
                    .setTitle(titleId)
                    .setMessage(errMsg)
                    .setPositiveButton(android.R.string.ok, (p1, p2) -> {
                        if(exitIfOk) {
                            if (ctx instanceof MainActivity) {
                                fullyExit();
                            } else if (ctx instanceof Activity) {
                                ((Activity) ctx).finish();
                            }
                        }
                    })
                    .setNegativeButton(showMore ? R.string.error_show_less : R.string.error_show_more, (p1, p2) -> showError(ctx, titleId, rolledMessage, e, exitIfOk, !showMore))
                    .setNeutralButton(android.R.string.copy, (p1, p2) -> {
                        ClipboardManager mgr = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        mgr.setPrimaryClip(ClipData.newPlainText("error", printToString(e)));
                        if(exitIfOk) {
                            if (ctx instanceof MainActivity) {
                                fullyExit();
                            } else {
                                ((Activity) ctx).finish();
                            }
                        }
                    })
                    .setCancelable(!exitIfOk);
            try {
                showStyledDialog(builder);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        };

        if (ctx instanceof Activity) {
            ((Activity) ctx).runOnUiThread(runnable);
        } else {
            runnable.run();
        }
    }

    /**
     * Show the error remotely in a context-aware fashion. Has generally the same behaviour as
     * Tools.showError when in an activity, but when not in one, sends a notification that opens an
     * activity and calls Tools.showError().
     * NOTE: If the Throwable is a ContextExecutorTask and when not in an activity,
     * its executeWithApplication() method will never be called.
     * @param e the error (throwable)
     */
    public static void showErrorRemote(Throwable e) {
        showErrorRemote(null, e);
    }
    public static void showErrorRemote(Context context, int rolledMessage, Throwable e) {
        showErrorRemote(context.getString(rolledMessage), e);
    }
    public static void showErrorRemote(String rolledMessage, Throwable e) {
        // I WILL embrace layer violations because Android's concept of layers is STUPID
        // We live in the same process anyway, why make it any more harder with this needless
        // abstraction?

        // Add your Context-related rage here
        ContextExecutor.execute(new ShowErrorActivity.RemoteErrorTask(e, rolledMessage));
    }



    public static void dialogOnUiThread(final Activity activity, final CharSequence title, final CharSequence message) {
        activity.runOnUiThread(()->dialog(activity, title, message));
    }
    public static void dialogOnUiThread(final Activity activity, final int title, final int message) {
        dialogOnUiThread(activity, activity.getString(title), activity.getString(message));
    }

    public static void dialog(final Context context, final CharSequence title, final CharSequence message) {
        showStyledDialog(createStyledDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null));
    }

    public static AlertDialog.Builder createStyledDialogBuilder(Context context) {
        return new AlertDialog.Builder(context, R.style.BattlyDialog);
    }

    public static AlertDialog showStyledDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.show();
        styleDialog(dialog);
        return dialog;
    }

    public static void styleDialog(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_battly_popup);
        }
        Runnable colorizeButtons = () -> {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFFFFFF);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFC7D4DF);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(0xFF8FD4B3);
            }
        };
        dialog.setOnShowListener(d -> colorizeButtons.run());
        if (dialog.isShowing()) {
            colorizeButtons.run();
        }
    }

    public static void openURL(Activity act, String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        act.startActivity(browserIntent);
    }

    private static boolean checkRules(JMinecraftVersionList.Arguments.ArgValue.ArgRules[] rules) {
        if(rules == null) return true; // always allow
        for (JMinecraftVersionList.Arguments.ArgValue.ArgRules rule : rules) {
            if (rule.action.equals("allow") && rule.os != null && rule.os.name.equals("osx")) {
                return false; //disallow
            }
        }
        return true; // allow if none match
    }

    public static void preProcessLibraries(DependentLibrary[] libraries) {
        for (int i = 0; i < libraries.length; i++) {
            DependentLibrary libItem = libraries[i];
            String[] version = libItem.name.split(":")[2].split("\\.");
            if (libItem.name.startsWith("net.java.dev.jna:jna:")) {
                // Special handling for LabyMod 1.8.9, Forge 1.12.2(?) and oshi
                // we have libjnidispatch 5.13.0 in jniLibs directory
                if (Integer.parseInt(version[0]) >= 5 && Integer.parseInt(version[1]) >= 13) continue;
                Log.d(APP_NAME, "Library " + libItem.name + " has been changed to version 5.13.0");
                createLibraryInfo(libItem);
                libItem.name = "net.java.dev.jna:jna:5.13.0";
                libItem.downloads.artifact.path = "net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar";
                libItem.downloads.artifact.sha1 = "1200e7ebeedbe0d10062093f32925a912020e747";
                libItem.downloads.artifact.url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar";
            } else if (libItem.name.startsWith("com.github.oshi:oshi-core:")) {
                //if (Integer.parseInt(version[0]) >= 6 && Integer.parseInt(version[1]) >= 3) return;
                // FIXME: ensure compatibility

                if (Integer.parseInt(version[0]) != 6 || Integer.parseInt(version[1]) != 2) continue;
                Log.d(APP_NAME, "Library " + libItem.name + " has been changed to version 6.3.0");
                createLibraryInfo(libItem);
                libItem.name = "com.github.oshi:oshi-core:6.3.0";
                libItem.downloads.artifact.path = "com/github/oshi/oshi-core/6.3.0/oshi-core-6.3.0.jar";
                libItem.downloads.artifact.sha1 = "9e98cf55be371cafdb9c70c35d04ec2a8c2b42ac";
                libItem.downloads.artifact.url = "https://repo1.maven.org/maven2/com/github/oshi/oshi-core/6.3.0/oshi-core-6.3.0.jar";
            } else if (libItem.name.startsWith("org.ow2.asm:asm-all:")) {
                // Early versions of the ASM library get repalced with 5.0.4 because Pojav's LWJGL is compiled for
                // Java 8, which is not supported by old ASM versions. Mod loaders like Forge, which depend on this
                // library, often include lwjgl in their class transformations, which causes errors with old ASM versions.
                if(Integer.parseInt(version[0]) >= 5) continue;
                Log.d(APP_NAME, "Library " + libItem.name + " has been changed to version 5.0.4");
                createLibraryInfo(libItem);
                libItem.name = "org.ow2.asm:asm-all:5.0.4";
                libItem.url = null;
                libItem.downloads.artifact.path = "org/ow2/asm/asm-all/5.0.4/asm-all-5.0.4.jar";
                libItem.downloads.artifact.sha1 = "e6244859997b3d4237a552669279780876228909";
                libItem.downloads.artifact.url = "https://repo1.maven.org/maven2/org/ow2/asm/asm-all/5.0.4/asm-all-5.0.4.jar";
            }
        }
    }

    private static void createLibraryInfo(DependentLibrary library) {
        if(library.downloads == null || library.downloads.artifact == null)
            library.downloads = new DependentLibrary.LibraryDownloads(new MinecraftLibraryArtifact());
    }

    public static String[] generateLibClasspath(JMinecraftVersionList.Version info) {
        List<String> libDir = new ArrayList<>();
        iLwjglVersion = 0;
        sLwjglVersion = null;
        lwjglNativesDir = null;
        for (DependentLibrary libItem: info.libraries) {
            if(!checkRules(libItem.rules)) continue;
            if (libItem.name != null && libItem.name.toLowerCase(Locale.ROOT).contains(":thin-lwjgl:")) {
                Log.d(APP_NAME, "Ignored incompatible LabyMod thin-lwjgl library on Android: " + libItem.name);
                continue;
            }
            detectLwjglVersion(libItem);
            String libPath = Tools.DIR_HOME_LIBRARY + "/" + artifactToPath(libItem);
            if (!FileUtils.exists(libPath)) {
                Log.d(APP_NAME, "Ignored non-exists file: " + libPath);
                continue;
            }
            libDir.add(libPath);
            // Mitigation: Babric doesn't use asm-all for some reason so it does a classpath conflict
            if (libItem.name.startsWith("org.ow2.asm:asm") && !libItem.name.startsWith("org.ow2.asm:asm-all:")){
                libDir.remove(Tools.DIR_HOME_LIBRARY + "/" + artifactToPath(new DependentLibrary(){{
                    name = "org.ow2.asm:asm-all:5.0.4";
                }} ));
            }
        }
        if (iLwjglVersion < 200 || iLwjglVersion > 999) {
            Log.w(APP_NAME, "Unable to determine LWJGL version from JSON, falling back to LWJGL 3.3.3");
            iLwjglVersion = 333;
        }
        sLwjglVersion = getInternalLwjglVersion(iLwjglVersion);
        lwjglNativesDir = resolveLwjglNativesDir(sLwjglVersion);
        return libDir.toArray(new String[0]);
    }

    private static String resolveLwjglNativesDir(String lwjglVersion) {
        // LWJGL 3.3.3 uses the JNI libraries packaged in the APK. Older Battly
        // releases extracted a separate directory with a different build, which
        // can survive an app update and cause Java/native version mismatches.
        if ("3.3.3".equals(lwjglVersion)) {
            return NATIVE_LIB_DIR;
        }
        File versionedDir = new File(DIR_DATA, "lwjgl-" + lwjglVersion + "-natives/"
                + archAsStringAndroid(getDeviceArchitecture()));
        File coreLibrary = new File(versionedDir, "liblwjgl.so");
        File versionMarker = new File(versionedDir, ".version");
        if (coreLibrary.isFile() && coreLibrary.length() > 0 && versionMarker.isFile()) {
            return versionedDir.getAbsolutePath();
        }
        Log.w(APP_NAME, "LWJGL " + lwjglVersion
                + " native component is unavailable or incomplete; using APK natives");
        return NATIVE_LIB_DIR;
    }

    private static String getInternalLwjglVersion(int version) {
        if (version >= 342) return "3.4.2";
        if (version >= 341) return "3.4.1";
        return "3.3.3";
    }

    private static void detectLwjglVersion(DependentLibrary libItem) {
        if (libItem == null || libItem.name == null) {
            return;
        }
        int versionOffset = 0;
        if (libItem.name.startsWith("org.lwjgl.lwjgl:lwjgl:")) {
            versionOffset = "org.lwjgl.lwjgl:lwjgl:".length();
        } else if (libItem.name.startsWith("org.lwjgl:lwjgl:")) {
            versionOffset = "org.lwjgl:lwjgl:".length();
        }
        int detectedVersion = 0;
        while (versionOffset > 0 && versionOffset < libItem.name.length()) {
            char c = libItem.name.charAt(versionOffset);
            if (c >= '0' && c <= '9') {
                detectedVersion = detectedVersion * 10 + (c - '0');
            } else if (c != '.') {
                break;
            }
            versionOffset++;
        }
        // Modern manifests may temporarily mix module revisions. Select the newest
        // declared LWJGL revision so compatibility modules such as lwjgl-sdl are enabled.
        if (detectedVersion >= 200 && detectedVersion <= 999) {
            iLwjglVersion = Math.max(iLwjglVersion, detectedVersion);
        }
    }

    public static JMinecraftVersionList.Version getVersionInfo(String versionName) {
        return getVersionInfo(versionName, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static JMinecraftVersionList.Version getVersionInfo(String versionName, boolean skipInheriting) {
        try {
            JMinecraftVersionList.Version customVer = Tools.GLOBAL_GSON.fromJson(read(DIR_HOME_VERSION + "/" + versionName + "/" + versionName + ".json"), JMinecraftVersionList.Version.class);
            if (skipInheriting || customVer.inheritsFrom == null || customVer.inheritsFrom.equals(customVer.id)) {
                preProcessLibraries(customVer.libraries);
            } else {
                JMinecraftVersionList.Version inheritsVer;
                //If it won't download, just search for it
                try{
                    inheritsVer = Tools.GLOBAL_GSON.fromJson(read(DIR_HOME_VERSION + "/" + customVer.inheritsFrom + "/" + customVer.inheritsFrom + ".json"), JMinecraftVersionList.Version.class);
                }catch(IOException e) {
                    throw new RuntimeException("Can't find the source version for "+ versionName +" (req version="+customVer.inheritsFrom+")");
                }
                //inheritsVer.inheritsFrom = inheritsVer.id;
                insertSafety(inheritsVer, customVer,
                        "assetIndex", "assets", "id",
                        "mainClass", "minecraftArguments",
                        "releaseTime", "time", "type", "inheritsFrom"
                );

                // Go through the libraries, remove the ones overridden by the custom version
                List<DependentLibrary> inheritLibraryList = new ArrayList<>(Arrays.asList(inheritsVer.libraries));
                outer_loop:
                for(DependentLibrary library : customVer.libraries){
                    // Clean libraries overridden by the custom version
                    String libName = library.name.substring(0, library.name.lastIndexOf(":"));

                    for(DependentLibrary inheritLibrary : inheritLibraryList) {
                        String inheritLibName = inheritLibrary.name.substring(0, inheritLibrary.name.lastIndexOf(":"));

                        if(libName.equals(inheritLibName)){
                            Log.d(APP_NAME, "Library " + libName + ": Replaced version " +
                                    libName.substring(libName.lastIndexOf(":") + 1) + " with " +
                                    inheritLibName.substring(inheritLibName.lastIndexOf(":") + 1));

                            // Remove the library , superseded by the overriding libs
                            inheritLibraryList.remove(inheritLibrary);
                            continue outer_loop;
                        }
                    }
                }

                // Fuse libraries
                inheritLibraryList.addAll(Arrays.asList(customVer.libraries));
                inheritsVer.libraries = inheritLibraryList.toArray(new DependentLibrary[0]);
                preProcessLibraries(inheritsVer.libraries);


                // Inheriting Minecraft 1.13+ with append custom args
                if (inheritsVer.arguments != null && customVer.arguments != null) {
                    List totalArgList = new ArrayList(Arrays.asList(inheritsVer.arguments.game));

                    int nskip = 0;
                    for (int i = 0; i < customVer.arguments.game.length; i++) {
                        if (nskip > 0) {
                            nskip--;
                            continue;
                        }

                        Object perCustomArg = customVer.arguments.game[i];
                        if (perCustomArg instanceof String) {
                            String perCustomArgStr = (String) perCustomArg;
                            // Check if there is a duplicate argument on combine
                            if (perCustomArgStr.startsWith("--") && totalArgList.contains(perCustomArgStr)) {
                                perCustomArg = customVer.arguments.game[i + 1];
                                if (perCustomArg instanceof String) {
                                    perCustomArgStr = (String) perCustomArg;
                                    // If the next is argument value, skip it
                                    if (!perCustomArgStr.startsWith("--")) {
                                        nskip++;
                                    }
                                }
                            } else {
                                totalArgList.add(perCustomArgStr);
                            }
                        } else if (!totalArgList.contains(perCustomArg)) {
                            totalArgList.add(perCustomArg);
                        }
                    }

                    inheritsVer.arguments.game = totalArgList.toArray(new Object[0]);
                }

                customVer = inheritsVer;
            }

            // LabyMod 4 sets version instead of majorVersion
            if (customVer.javaVersion != null && customVer.javaVersion.majorVersion == 0) {
                customVer.javaVersion.majorVersion = customVer.javaVersion.version;
            }
            return customVer;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Prevent NullPointerException
    private static void insertSafety(JMinecraftVersionList.Version targetVer, JMinecraftVersionList.Version fromVer, String... keyArr) {
        for (String key : keyArr) {
            Object value = null;
            try {
                Field fieldA = fromVer.getClass().getDeclaredField(key);
                value = fieldA.get(fromVer);
                if (((value instanceof String) && !((String) value).isEmpty()) || value != null) {
                    Field fieldB = targetVer.getClass().getDeclaredField(key);
                    fieldB.set(targetVer, value);
                }
            } catch (Throwable th) {
                Log.w(Tools.APP_NAME, "Unable to insert " + key + "=" + value, th);
            }
        }
    }

    public static String read(InputStream is) throws IOException {
        String readResult = IOUtils.toString(is, StandardCharsets.UTF_8);
        is.close();
        return readResult;
    }

    public static String read(String path) throws IOException {
        return read(new FileInputStream(path));
    }

    public static String read(File path) throws IOException {
        return read(new FileInputStream(path));
    }

    public static void write(String path, String content) throws IOException {
        File file = new File(path);
        FileUtils.ensureParentDirectory(file);
        try(FileOutputStream outStream = new FileOutputStream(file)) {
            IOUtils.write(content, outStream);
        }
    }

    public static void downloadFile(String urlInput, String nameOutput) throws IOException {
        File file = new File(nameOutput);
        DownloadUtils.downloadFile(urlInput, file);
    }

    public static boolean isAndroid8OrHigher() {
        return SDK_INT >= 26;
    }

    public static void fullyExit() {
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public static void printLauncherInfo(String gameVersion, String javaArguments) {
        Logger.appendToLog("Info: Launcher version: " + BuildConfig.VERSION_NAME);
        Logger.appendToLog("Info: Architecture: " + Architecture.archAsString(DEVICE_ARCHITECTURE));
        Logger.appendToLog("Info: Device model: " + Build.MANUFACTURER + " " +Build.MODEL);
        Logger.appendToLog("Info: API version: " + SDK_INT);
        Logger.appendToLog("Info: Selected Minecraft version: " + gameVersion);
        Logger.appendToLog("Info: Custom Java arguments: \"" + javaArguments + "\"");
        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        Logger.appendToLog("Info: Graphics device: "+info.vendor+ " "+info.renderer+" (OpenGL ES "+info.glesMajorVersion+")");
    }

    public interface DownloaderFeedback {
        void updateProgress(int curr, int max);
    }


    public static boolean compareSHA1(File f, String sourceSHA) {
        try {
            String sha1_dst;
            try (InputStream is = new FileInputStream(f)) {
                sha1_dst = new String(Hex.encodeHex(org.apache.commons.codec.digest.DigestUtils.sha1(is)));
            }
            if(sourceSHA != null) {
                return sha1_dst.equalsIgnoreCase(sourceSHA);
            } else{
                return true; // fake match
            }
        }catch (IOException e) {
            Log.i("SHA1","Fake-matching a hash due to a read error",e);
            return true;
        }
    }

    public static void ignoreNotch(boolean shouldIgnore, Activity ctx){
        if (SDK_INT >= P) {
            if (shouldIgnore) {
                ctx.getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            } else {
                ctx.getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            }
            ctx.getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            Tools.updateWindowSize(ctx);
        }
    }

    public static int getTotalDeviceMemory(Context ctx){
        ActivityManager actManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return (int) (memInfo.totalMem / 1048576L);
    }

    public static int getFreeDeviceMemory(Context ctx){
        ActivityManager actManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return (int) (memInfo.availMem / 1048576L);
    }

    private static int internalGetMaxContinuousAddressSpaceSize() throws Exception{
        MemoryHoleFinder memoryHoleFinder = new MemoryHoleFinder();
        new SelfMapsParser(memoryHoleFinder).run();
        long largestHole = memoryHoleFinder.getLargestHole();
        if(largestHole == -1) return -1;
        else return (int)(largestHole / 1048576L);
    }

    public static int getMaxContinuousAddressSpaceSize() {
        try {
            return internalGetMaxContinuousAddressSpaceSize();
        }catch (Exception e){
            Log.w("Tools", "Failed to find the largest uninterrupted address space");
            return -1;
        }
    }

    public static int getDisplayFriendlyRes(int displaySideRes, float scaling){
        displaySideRes *= scaling;
        if(displaySideRes % 2 != 0) displaySideRes --;
        return displaySideRes;
    }

    public static String getFileName(Context ctx, Uri uri) {
        Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
        if(c == null) return uri.getLastPathSegment(); // idk myself but it happens on asus file manager
        c.moveToFirst();
        int columnIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if(columnIndex == -1) return uri.getLastPathSegment();
        String fileName = c.getString(columnIndex);
        c.close();
        return fileName;
    }

    /** Swap the main fragment with another */
    public static void swapFragment(FragmentActivity fragmentActivity , Class<? extends Fragment> fragmentClass,
                                    @Nullable String fragmentTag, @Nullable Bundle bundle) {
        // When people tab out, it might happen
        //TODO handle custom animations
        fragmentActivity.getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .addToBackStack(fragmentClass.getName())
                .replace(R.id.container_fragment, fragmentClass, bundle, fragmentTag).commit();
    }

    public static void backToMainMenu(FragmentActivity fragmentActivity) {
        fragmentActivity.getSupportFragmentManager()
                .popBackStack("ROOT", 0);
    }

    /** Remove the current fragment */
    public static void removeCurrentFragment(FragmentActivity fragmentActivity){
        fragmentActivity.getSupportFragmentManager().popBackStack();
    }

    public static void installMod(Activity activity, boolean customJavaArgs) {
        if (MultiRTUtils.getExactJreName(8) == null) {
            Toast.makeText(activity, R.string.multirt_nojava8rt, Toast.LENGTH_LONG).show();
            return;
        }

        if(!customJavaArgs){ // Launch the intent to get the jar file
            if(!(activity instanceof LauncherActivity))
                throw new IllegalStateException("Cannot start Mod Installer without LauncherActivity");
            LauncherActivity launcherActivity = (LauncherActivity)activity;
            launcherActivity.modInstallerLauncher.launch(null);
            return;
        }

        // install mods with custom arguments
        final EditText editText = new EditText(activity);
        editText.setSingleLine();
        editText.setHint(R.string.jar_installer_arguments_hint);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setTitle(R.string.alerttitle_installmod)
                .setNegativeButton(android.R.string.cancel, null)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (di, i) -> {
                    Intent intent = new Intent(activity, JavaGUILauncherActivity.class);
                    intent.putExtra("javaArgs", editText.getText().toString());
                    activity.startActivity(intent);
                });
        showStyledDialog(builder);
    }

    /** Display and return a progress dialog, instructing to wait */
    public static ProgressDialog getWaitingDialog(Context ctx, int message){
        final ProgressDialog barrier = new ProgressDialog(ctx);
        barrier.setMessage(ctx.getString(message));
        barrier.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        barrier.setCancelable(false);
        barrier.show();

        return barrier;
    }

    /** Launch the mod installer activity. The Uri must be from our own content provider or
     * from ACTION_OPEN_DOCUMENT
     */
    public static void launchModInstaller(Activity activity, @NonNull Uri uri){
        Intent intent = new Intent(activity, JavaGUILauncherActivity.class);
        intent.putExtra("modUri", uri);
        activity.startActivity(intent);
    }


    public static void installRuntimeFromUri(Context context, Uri uri){
        sExecutorService.execute(() -> {
            try {
                String name = getFileName(context, uri);
                MultiRTUtils.installRuntimeNamed(
                        NATIVE_LIB_DIR,
                        context.getContentResolver().openInputStream(uri),
                        name);

                MultiRTUtils.postPrepare(name);
            } catch (IOException e) {
                Tools.showError(context, e);
            }
        });
    }

    public static String extractUntilCharacter(String input, String whatFor, char terminator) {
        int whatForStart = input.indexOf(whatFor);
        if(whatForStart == -1) return null;
        whatForStart += whatFor.length();
        int terminatorIndex = input.indexOf(terminator, whatForStart);
        if(terminatorIndex == -1) return null;
        return input.substring(whatForStart, terminatorIndex);
    }

    public static boolean isValidString(String string) {
        return string != null && !string.isEmpty();
    }

    public static String getRuntimeName(String prefixedName) {
        if(prefixedName == null) return prefixedName;
        return stripLauncherProfilePrefix(prefixedName);
    }

    public static String stripLauncherProfilePrefix(String prefixedName) {
        if(prefixedName == null) return null;
        if(prefixedName.startsWith(Tools.LAUNCHERPROFILES_RTPREFIX)) {
            return prefixedName.substring(Tools.LAUNCHERPROFILES_RTPREFIX.length());
        }
        if(prefixedName.startsWith(LEGACY_LAUNCHERPROFILES_RTPREFIX)) {
            return prefixedName.substring(LEGACY_LAUNCHERPROFILES_RTPREFIX.length());
        }
        return null;
    }

    public static String getSelectedRuntime(MinecraftProfile minecraftProfile) {
        String runtime = LauncherPreferences.PREF_DEFAULT_RUNTIME;
        String profileRuntime = getRuntimeName(minecraftProfile.javaDir);
        if(profileRuntime != null) {
            if(MultiRTUtils.forceReread(profileRuntime).versionString != null) {
                runtime = profileRuntime;
            }
        }
        return runtime;
    }

    public static void runOnUiThread(Runnable runnable) {
        MAIN_HANDLER.post(runnable);
    }

    public static @NonNull String pickRuntime(MinecraftProfile minecraftProfile, int targetJavaVersion) {
        String runtime = getSelectedRuntime(minecraftProfile);
        String profileRuntime = getRuntimeName(minecraftProfile.javaDir);
        Runtime pickedRuntime = MultiRTUtils.read(runtime);
        if(runtime == null || pickedRuntime.javaVersion == 0 || pickedRuntime.javaVersion < targetJavaVersion) {
            String preferredRuntime = MultiRTUtils.getNearestJreName(targetJavaVersion);
            if(preferredRuntime == null) throw new RuntimeException("Failed to autopick runtime!");
            if(profileRuntime != null) minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX+preferredRuntime;
            runtime = preferredRuntime;
        }
        return runtime;
    }

    /** Triggers the share intent chooser, with the latestlog file attached to it */
    public static void shareLog(Context context){
        openPath(context, new File(Tools.DIR_GAME_HOME, "latestlog.txt"), true);
    }

    /**
     * Determine the MIME type of a File.
     * @param file The file to determine the type of
     * @return the type, or the default value *slash* if cannot be determined
     */
    public static String getMimeType(File file) {
        if(file.isDirectory()) return DocumentsContract.Document.MIME_TYPE_DIR;
        String mimeType = null;
        try (FileInputStream fileInputStream = new FileInputStream(file)){
            // Theoretically we don't even need the buffer since we don't care about the
            // contents of the file after the guess, but mark-supported streams
            // are a requirement of URLConnection.guessContentTypeFromStream()
            try(BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
                mimeType = URLConnection.guessContentTypeFromStream(bufferedInputStream);
            }
        }catch (IOException e) {
            Log.w("FileMimeType", "Failed to determine MIME type by stream", e);
        }
        if(mimeType != null) return mimeType;
        mimeType = URLConnection.guessContentTypeFromName(file.getName());
        if(mimeType != null) return mimeType;
        return "*/*";
    }

    /**
     * Open the path specified by a File in a file explorer or in a relevant application.
     * @param context the current Context
     * @param file the File to open
     * @param share whether to open a "Share" or an "Open" dialog.
     */
    public static void openPath(Context context, File file, boolean share) {
        Uri contentUri = DocumentsContract.buildDocumentUri(context.getString(R.string.storageProviderAuthorities), file.getAbsolutePath());
        String mimeType = getMimeType(file);
        Intent intent = new Intent();
        if(share) {
            intent.setAction(Intent.ACTION_SEND);
            intent.setType(getMimeType(file));
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
        }else {
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, mimeType);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent chooserIntent = Intent.createChooser(intent, file.getName());
        context.startActivity(chooserIntent);
    }

    /** Mesure the textview height, given its current parameters */
    public static int mesureTextviewHeight(TextView t) {
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(t.getWidth(), View.MeasureSpec.AT_MOST);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        t.measure(widthMeasureSpec, heightMeasureSpec);
        return t.getMeasuredHeight();
    }

    /**
     * Check if the device is one of the devices that may be affected by the hanging linker issue.
     * The device is affected if the linker causes the process to lock up when dlopen() is called within
     * dl_iterate_phdr().
     * For now, the only affected firmware that I know of is Android 5.1, EMUI 3.1 on MTK-based Huawei
     * devices.
     * @return if the device is affected by the hanging linker issue.
     */
    public static boolean deviceHasHangingLinker() {
        // Android Oreo and onwards have GSIs and most phone firmwares at that point were not modified
        // *that* intrusively. So assume that we are not affected.
        if(SDK_INT >= Build.VERSION_CODES.O) return false;
        // Since the affected function in LWJGL is rarely used (and when used, it's mainly for debug prints)
        // we can make the search scope a bit more broad and check if we are running on a Huawei device.
        return Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("huawei");
    }

    public static boolean isAndroidEmulator() {
        String fingerprint = safeBuildValue(Build.FINGERPRINT);
        String model = safeBuildValue(Build.MODEL);
        String manufacturer = safeBuildValue(Build.MANUFACTURER);
        String brand = safeBuildValue(Build.BRAND);
        String device = safeBuildValue(Build.DEVICE);
        String product = safeBuildValue(Build.PRODUCT);
        String hardware = safeBuildValue(Build.HARDWARE);

        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("android sdk built for")
                || model.contains("sdk_gphone")
                || manufacturer.contains("genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || product.contains("sdk")
                || product.contains("emulator")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu");
    }

    private static String safeBuildValue(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public static class RenderersList {
        public final List<String> rendererIds;
        public final String[] rendererDisplayNames;

        public RenderersList(List<String> rendererIds, String[] rendererDisplayNames) {
            this.rendererIds = rendererIds;
            this.rendererDisplayNames = rendererDisplayNames;
        }
    }

    public static boolean checkVulkanSupport(PackageManager packageManager) {
        if(SDK_INT >= Build.VERSION_CODES.N) {
            return packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
        }
        return false;
    }

    public static <T> T getWeakReference(WeakReference<T> weakReference) {
        if(weakReference == null) return null;
        return weakReference.get();
    }

    /** Return the renderers that are compatible with this device */
    public static RenderersList getCompatibleRenderers(Context context) {
        if(sCompatibleRenderers != null) return sCompatibleRenderers;
        Resources resources = context.getResources();
        String[] defaultRenderers = resources.getStringArray(R.array.renderer_values);
        String[] defaultRendererNames = resources.getStringArray(R.array.renderer);
        boolean deviceHasVulkan = checkVulkanSupport(context.getPackageManager());
        boolean deviceCompatibleMesa = SDK_INT >= Build.VERSION_CODES.Q;
        boolean deviceHasOSMesaZinkBinary = hasNativeLibrary(context, "libOSMesa.so");
        boolean deviceHasKopperShim = hasNativeLibrary(context, "libglxshim.so");
        boolean deviceHasMesaEgl = hasNativeLibrary(context, "libEGL_mesa.so");
        boolean deviceHasZinkDri = hasNativeLibrary(context, "libzink_dri.so");
        boolean deviceHasFreedreno = hasNativeLibrary(context, "libvulkan_freedreno.so");
        boolean deviceIsAdreno = GLInfoUtils.getGlInfo().isAdreno();
        boolean deviceIsArm64 = getDeviceArchitecture() == Architecture.ARCH_ARM64;
        boolean appHasGl4es = hasNativeLibrary(context, "libgl4es_114.so");
        boolean appHasMobileGlues = hasNativeLibrary(context, "libmobileglues.so");
        boolean deviceHasOpenGLES3 = JREUtils.getDetectedVersion() >= 3;
        // LTW is an optional proprietary dependency
        boolean appHasLtw = hasNativeLibrary(context, "libltw.so");
        List<String> rendererIds = new ArrayList<>(defaultRenderers.length);
        List<String> rendererNames = new ArrayList<>(defaultRendererNames.length);
        for(int i = 0; i < defaultRenderers.length; i++) {
            String rendererId = defaultRenderers[i];
            if(rendererId.startsWith("opengles") && !appHasGl4es && !rendererId.contains("mobileglues") && !rendererId.contains("ltw")) continue;
            if(rendererId.contains("vulkan") && !deviceHasVulkan) continue;
            if("vulkan_zink".equals(rendererId) && (!deviceCompatibleMesa || !deviceHasOSMesaZinkBinary)) continue;
            if("opengles3_desktopgl_zink_kopper".equals(rendererId)
                    && (!deviceHasVulkan || !deviceCompatibleMesa || !deviceHasMesaEgl
                    || !deviceHasKopperShim || !deviceHasZinkDri)) continue;
            if(rendererId.contains("freedreno") && (!deviceHasVulkan || !deviceCompatibleMesa
                    || !deviceIsAdreno || !deviceIsArm64 || !deviceHasFreedreno
                    || !deviceHasMesaEgl || !deviceHasKopperShim || !deviceHasZinkDri)) continue;
            if(rendererId.contains("mobileglues") && !appHasMobileGlues) continue;
            if(rendererId.contains("ltw") && (!deviceHasOpenGLES3 || !appHasLtw)) continue;
            rendererIds.add(rendererId);
            rendererNames.add(defaultRendererNames[i]);
        }
        for(RendererPluginRegistry.Entry entry : RendererPluginRegistry.compatible(context)) {
            if(!rendererIds.contains(entry.id) && !rendererIds.contains(entry.runtimeRenderer)) {
                rendererIds.add(entry.id);
                rendererNames.add(entry.name);
            }
        }
        sCompatibleRenderers = new RenderersList(rendererIds,
                rendererNames.toArray(new String[0]));

        return sCompatibleRenderers;
    }

    private static boolean hasNativeLibrary(Context context, String libraryName) {
        if (Tools.isValidString(Tools.NATIVE_LIB_DIR)
                && new File(Tools.NATIVE_LIB_DIR, libraryName).isFile()) {
            return true;
        }
        try {
            String nativeDir = context.getApplicationInfo().nativeLibraryDir;
            return Tools.isValidString(nativeDir) && new File(nativeDir, libraryName).isFile();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Checks if the renderer Id is compatible with the current device */
    public static boolean checkRendererCompatible(Context context, String rendererName) {
         return getCompatibleRenderers(context).rendererIds.contains(rendererName);
    }

    /** Releases the cache of compatible renderers. */
    public static void releaseRenderersCache() {
        sCompatibleRenderers = null;
        System.gc();
    }

    public static boolean deviceSupportsGyro(@NonNull Context context) {
        return ((SensorManager)context.getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;

    }

    public static void dialogForceClose(Context ctx) {
        new AlertDialog.Builder(ctx, R.style.BattlyDialog)
                .setMessage(R.string.mcn_exit_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (p1, p2) -> {
                    try {
                        Tools.fullyExit();
                    } catch (Throwable th) {
                        Log.w(Tools.APP_NAME, "Could not enable System.exit() method!", th);
                    }
                }).show();
    }

    public static void switchDemo(boolean isDemo){
        if(isDemo) {
            DIR_GAME_NEW = DIR_DATA + "/demo/.minecraft";
        } else {
            DIR_GAME_NEW = DIR_GAME_HOME + "/.minecraft";
        }
        DIR_HOME_VERSION = DIR_GAME_NEW + "/versions";
        DIR_HOME_LIBRARY = DIR_GAME_NEW + "/libraries";
        ASSETS_PATH = DIR_GAME_NEW + "/assets";
        OBSOLETE_RESOURCES_PATH = DIR_GAME_NEW + "/resources";
    }

    private static NetworkInfo getActiveNetworkInfo(Context ctx) {
        ConnectivityManager connMgr = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo; // This can return null when there is no wifi or data connected
    }

    public static boolean isOnline(Context ctx) {
        NetworkInfo info = getActiveNetworkInfo(ctx);
        if(info == null) return false;
        return (info.isConnected());
    }

    public static boolean isDemoProfile(Context ctx){
        MinecraftAccount currentProfile = PojavProfile.getCurrentProfileContent(ctx, null);
        return currentProfile != null && currentProfile.isDemo();
    }

    public static boolean isLocalProfile(Context ctx){
        MinecraftAccount currentProfile = PojavProfile.getCurrentProfileContent(ctx, null);
        return currentProfile == null || currentProfile.isLocal();
    }
    public static boolean hasOnlineProfile(){
        for (MinecraftAccount accountToCheck : getAllProfiles()) {
            if (!accountToCheck.isLocal() && !accountToCheck.isDemo()) {
                return true;
            }
        }
        return false;
    }

    public static void hasNoOnlineProfileDialog(Activity activity, @Nullable Runnable run, @Nullable String customTitle, @Nullable String customMessage){
        if (hasOnlineProfile() && !Tools.isDemoProfile(activity)){
            if (run != null) { // Demo profile handling should be using customTitle and customMessage
                run.run();
            }
        } else { // If there is no online profile, show a dialog
            customTitle = customTitle == null ? activity.getString(R.string.no_minecraft_account_found) : customTitle;
            customMessage = customMessage == null ? activity.getString(R.string.feature_requires_java_account) : customMessage;
            dialogOnUiThread(activity, customTitle, customMessage);
        }
    }

    // Some boilerplate to reduce boilerplate elsewhere
    public static void hasNoOnlineProfileDialog(Activity activity){
        hasNoOnlineProfileDialog(activity, null, null, null);
    }
    public static void hasNoOnlineProfileDialog(Activity activity, Runnable run){
        hasNoOnlineProfileDialog(activity, run, null, null);
    }
    public static void hasNoOnlineProfileDialog(Activity activity, String customTitle, String customMessage){
        hasNoOnlineProfileDialog(activity, null, customTitle, customMessage);
    }

    public static String getSelectedVanillaMcVer(){
        String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile selected = LauncherProfiles.mainProfileJson.profiles.get(selectedProfile);
        if (selected == null) { // This should NEVER happen.
            throw new RuntimeException("No profile selected, how did you reach this? Go ask in the discord or github");
        }
        String currentMCVersion = selected.lastVersionId;
        String vanillaVersion = currentMCVersion;
        File providedJsonFile = new File(Tools.DIR_HOME_VERSION + "/" + currentMCVersion + "/" + currentMCVersion + ".json");
        JMinecraftVersionList.Version providedJsonVersion = null;
        try {
            providedJsonVersion = Tools.GLOBAL_GSON.fromJson(Tools.read(providedJsonFile.getAbsolutePath()), JMinecraftVersionList.Version.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            vanillaVersion = providedJsonVersion.inheritsFrom != null ? providedJsonVersion.inheritsFrom : vanillaVersion;
        } catch (NullPointerException e) {
            throw new RuntimeException(e);
        }
        return vanillaVersion;
    }

    public static Integer mcVersiontoInt(String mcVersion){
        String[] sVersionArray = mcVersion.split("\\.");
        String[] iVersionArray = new String[3];
        // Make sure this is actually a version string
        for (int i = 0; i < iVersionArray.length; i++) {
            try {
                // Ensure there's padding
                sVersionArray[i] =  String.format("%3s", sVersionArray[i]).replace(' ', '0');
                // Grab only the last 3, MCJE 999.999.999 isnt coming soon anyway
                sVersionArray[i] = sVersionArray[i].substring(sVersionArray[i].length() - 3);
            } catch (ArrayIndexOutOfBoundsException ignored){
                // If we don't get 3 a third array, pad with 0s because it's probably 1.21 or something
                iVersionArray[i] = "000";
                continue;
            }
            try {
                // Verify its a real deal, legit number
                Integer.parseInt(sVersionArray[i]);
                iVersionArray[i] = sVersionArray[i];
            } catch (NumberFormatException e) {
                throw new RuntimeException("Tools(mcVersiontoInt): Invalid version string");
            }
        }
        return Integer.parseInt(iVersionArray[0] + iVersionArray[1] + iVersionArray[2]);
    }

    public static boolean isPointerDeviceConnected() {
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int id : deviceIds) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                    || (sources & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
                    || (sources & InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL) {
                return true;
            }
        }
        return false;
    }

    public static Object runMethodbyReflection(String className, String methodName) throws ReflectiveOperationException{
        Class<?> clazz = Class.forName(className);
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        Object motionListener = method.invoke(null);
        assert motionListener != null;
        return motionListener;
    }

    static class SDL {
        /**
         * Initializes gamepad, joystick, and event subsystems.
         * This triggers {@link SDLControllerManager#pollInputDevices()} and subsequently disables
         * the emulated gamepad implementation.
         */
        public static native void initializeControllerSubsystems();
    }
}
