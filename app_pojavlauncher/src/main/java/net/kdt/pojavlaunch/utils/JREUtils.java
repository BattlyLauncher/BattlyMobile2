package net.kdt.pojavlaunch.utils;

import static net.kdt.pojavlaunch.Architecture.ARCH_X86;
import static net.kdt.pojavlaunch.Architecture.is64BitsDevice;
import static net.kdt.pojavlaunch.Tools.LOCAL_RENDERER;
import static net.kdt.pojavlaunch.Tools.NATIVE_LIB_DIR;
import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.Tools.shareLog;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_DUMP_SHADERS;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VSYNC_IN_ZINK;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.system.*;
import android.util.*;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.oracle.dalvik.*;
import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.lifecycle.LifecycleAwareAlertDialog;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.plugins.FFmpegPlugin;
import net.kdt.pojavlaunch.prefs.*;
import net.kdt.pojavlaunch.utils.FileUtils;

import org.lwjgl.glfw.*;

public class JREUtils {
    private JREUtils() {}

    public static String LD_LIBRARY_PATH;
    public static String jvmLibraryPath;

    public static String findInLdLibPath(String libName) {
        if(Os.getenv("LD_LIBRARY_PATH")==null) {
            try {
                if (LD_LIBRARY_PATH != null) {
                    Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true);
                }
            }catch (ErrnoException e) {
                e.printStackTrace();
            }
            return libName;
        }
        for (String libPath : Os.getenv("LD_LIBRARY_PATH").split(":")) {
            File f = new File(libPath, libName);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return libName;
    }

    public static ArrayList<File> locateLibs(File path) {
        ArrayList<File> returnValue = new ArrayList<>();
        File[] list = path.listFiles();
        if(list != null) {
            for(File f : list) {
                if(f.isFile() && f.getName().endsWith(".so")) {
                    returnValue.add(f);
                }else if(f.isDirectory()) {
                    returnValue.addAll(locateLibs(f));
                }
            }
        }
        return returnValue;
    }

    public static void initJavaRuntime(String jreHome) {
        dlopen(findInLdLibPath("libjli.so"));
        if(!dlopen("libjvm.so")){
            Log.w("DynamicLoader","Failed to load with no path, trying with full path");
            dlopen(jvmLibraryPath+"/libjvm.so");
        }
        dlopen(findInLdLibPath("libverify.so"));
        dlopen(findInLdLibPath("libjava.so"));
        // dlopen(findInLdLibPath("libjsig.so"));
        dlopen(findInLdLibPath("libnet.so"));
        dlopen(findInLdLibPath("libnio.so"));
        dlopen(findInLdLibPath("libawt.so"));
        dlopen(findInLdLibPath("libawt_headless.so"));
        dlopen(findInLdLibPath("libfreetype.so"));
        dlopen(findInLdLibPath("libfontmanager.so"));
        for(File f : locateLibs(new File(jreHome, Tools.DIRNAME_HOME_JRE))) {
            dlopen(f.getAbsolutePath());
        }
        dlopen(NATIVE_LIB_DIR + "/libopenal.so");
    }

    public static void redirectAndPrintJRELog() {

        Log.v("jrelog","Log starts here");
        new Thread(new Runnable(){
            int failTime = 0;
            ProcessBuilder logcatPb;
            @Override
            public void run() {
                try {
                    if (logcatPb == null) {
                        // No filtering by tag anymore as that relied on incorrect log levels set in log.h
                        logcatPb = new ProcessBuilder().command("logcat", /* "-G", "1mb", */ "-v", "brief", "-s", "jrelog", "LIBGL", "NativeInput").redirectErrorStream(true);
                    }

                    Log.i("jrelog-logcat","Clearing logcat");
                    new ProcessBuilder().command("logcat", "-c").redirectErrorStream(true).start();
                    Log.i("jrelog-logcat","Starting logcat");
                    java.lang.Process p = logcatPb.start();

                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = p.getInputStream().read(buf)) != -1) {
                        String currStr = new String(buf, 0, len);
                        Logger.appendToLog(currStr);
                    }

                    if (p.waitFor() != 0) {
                        Log.e("jrelog-logcat", "Logcat exited with code " + p.exitValue());
                        failTime++;
                        Log.i("jrelog-logcat", (failTime <= 10 ? "Restarting logcat" : "Too many restart fails") + " (attempt " + failTime + "/10");
                        if (failTime <= 10) {
                            run();
                        } else {
                            Logger.appendToLog("ERROR: Unable to get more log.");
                        }
                    }
                } catch (Throwable e) {
                    Log.e("jrelog-logcat", "Exception on logging thread", e);
                    Logger.appendToLog("Exception on logging thread:\n" + Log.getStackTraceString(e));
                }
            }
        }).start();
        Log.i("jrelog-logcat","Logcat thread started");

    }

    public static void relocateLibPath(Runtime runtime, String jreHome) {
        String JRE_ARCHITECTURE = runtime.arch;
        if (Architecture.archAsInt(JRE_ARCHITECTURE) == ARCH_X86){
            JRE_ARCHITECTURE = "i386/i486/i586";
        }

        for (String arch : JRE_ARCHITECTURE.split("/")) {
            File f = new File(jreHome, "lib/" + arch);
            if (f.exists() && f.isDirectory()) {
                Tools.DIRNAME_HOME_JRE = "lib/" + arch;
            }
        }

        String libName = is64BitsDevice() ? "lib64" : "lib";
        StringBuilder ldLibraryPath = new StringBuilder();
        if(FFmpegPlugin.isAvailable) {
            ldLibraryPath.append(FFmpegPlugin.libraryPath).append(":");
        }
        ldLibraryPath.append(jreHome)
                .append("/").append(Tools.DIRNAME_HOME_JRE)
                .append("/jli:").append(jreHome).append("/").append(Tools.DIRNAME_HOME_JRE)
                .append(":");
        ldLibraryPath.append("/system/").append(libName).append(":")
                .append("/vendor/").append(libName).append(":")
                .append("/vendor/").append(libName).append("/hw:");
        if (Tools.isValidString(Tools.lwjglNativesDir)) {
            ldLibraryPath.append(Tools.lwjglNativesDir).append(":");
        }
        ldLibraryPath.append(NATIVE_LIB_DIR);
        LD_LIBRARY_PATH = ldLibraryPath.toString();
    }

    public static void setJavaEnvironment(Activity activity, String jreHome) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("POJAV_NATIVEDIR", NATIVE_LIB_DIR);
        envMap.put("JAVA_HOME", jreHome);
        envMap.put("HOME", Tools.DIR_GAME_HOME);
        envMap.put("TMPDIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("LIBGL_MIPMAP", "3");

        // Prevent OptiFine (and other error-reporting stuff in Minecraft) from balooning the log
        envMap.put("LIBGL_NOERROR", "1");

        // Keep GL4ES' default shader compatibility hacks enabled. Old Minecraft shaders
        // still use texture2D(), and forcing LIBGL_NOINTOVLHACK makes GL4ES emit invalid
        // GLES 3 shaders on modern emulator/desktop drivers.

        // Fix white color on banner and sheep, since GL4ES 1.1.5
        envMap.put("LIBGL_NORMALIZE", "1");

        if(PREF_DUMP_SHADERS && !"opengles2".equals(LOCAL_RENDERER))
            envMap.put("LIBGL_VGPU_DUMP", "1");
        if(PREF_VSYNC_IN_ZINK)
            envMap.put("POJAV_VSYNC_IN_ZINK", "1");
        if(Tools.deviceHasHangingLinker())
            envMap.put("POJAV_EMUI_ITERATOR_MITIGATE", "1");


        // The OPEN GL version is changed according
        envMap.put("LIBGL_ES", (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION));

        envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));

        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("force_glsl_extensions_warn", "true");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
        envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        envMap.put("VTEST_SOCKET_NAME", new File(Tools.DIR_CACHE, ".virgl_test").getAbsolutePath());
        if (Tools.iLwjglVersion >= 341) {
            // Minecraft 26.3+ uses LWJGL's SDL3 window backend. SDL receives Android
            // touch events directly. Do not let SDL synthesize mouse clicks from
            // camera swipes; Battly's control layer owns mouse button gestures.
            envMap.put("SDL_TOUCH_MOUSE_EVENTS", "0");
            envMap.put("SDL_MOUSE_TOUCH_EVENTS", "0");
            // MobileGlues already presents Minecraft's SDR output in sRGB space.
            // Enabling SDL's framebuffer conversion applies gamma a second time
            // and makes the image look washed out and excessively bright.
            envMap.put("SDL_OPENGL_FORCE_SRGB_FRAMEBUFFER", "0");
        }

        envMap.put("LD_LIBRARY_PATH", LD_LIBRARY_PATH);
        envMap.put("PATH", jreHome + "/bin:" + Os.getenv("PATH"));
        if(FFmpegPlugin.isAvailable) {
            envMap.put("POJAV_FFMPEG_PATH", FFmpegPlugin.executablePath);
        }

        if(LOCAL_RENDERER != null) {
            String runtimeRenderer = RendererPluginRegistry.runtimeRendererFor(activity, LOCAL_RENDERER);
            envMap.put("BATTLY_RENDERER", runtimeRenderer);
            if(runtimeRenderer.equals("opengles3_ltw")) {
                envMap.put("LIBGL_ES", "3");
                envMap.put("POJAVEXEC_EGL","libltw.so"); // Use ANGLE EGL
            }
            if(runtimeRenderer.equals("opengles2") || runtimeRenderer.equals("opengles2_5")) {
                envMap.put("LIBGL_ES", "2");
            }
            if(runtimeRenderer.equals("opengles3")) {
                envMap.put("LIBGL_ES", "3");
            }
            if(runtimeRenderer.equals("opengles_mobileglues")){
                envMap.put("MG_DIR_PATH", Tools.DIR_DATA + "/MobileGlues");
                envMap.put("POJAVEXEC_EGL","libmobileglues.so");
                // LWJGL 3.4.1 creates its window through SDL3 instead of the Pojav GLFW
                // bridge. Point SDL at the same translation layer and let the Android SDL
                // backend request the GLES context MobileGlues renders through.
                envMap.put("SDL_OPENGL_LIBRARY", "libmobileglues.so");
                envMap.put("SDL_EGL_LIBRARY", "libmobileglues.so");
                envMap.put("BATTLY_SDL_FORCE_GLES", "1");
            }
            if (runtimeRenderer.equals("opengles3_desktopgl_zink_kopper")){
                envMap.put("POJAVEXEC_EGL","libEGL_mesa.so"); // Use Mesa EGL
                if (Tools.shouldUseUBWC()) envMap.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1"); // Turnip fix for OneUI rendering issues
            }
            if (runtimeRenderer.equals("opengles3_desktopgl_freedreno")){
                envMap.put("POJAVEXEC_EGL","libEGL_mesa.so"); // Use Mesa EGL
                envMap.put("POJAV_LOAD_TURNIP", "1");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                if (Tools.shouldUseUBWC()) envMap.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
            }
            if (runtimeRenderer.toLowerCase().contains("zink")){
                // This is sketch but it fixes a lot of things, if it causes problems we can just undo it.
                envMap.put("MESA_GL_VERSION_OVERRIDE","4.6COMPAT");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE","460");
            }
            if (runtimeRenderer.toLowerCase().contains("freedreno")){
                envMap.put("MESA_GL_VERSION_OVERRIDE","4.6COMPAT");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE","460");
            }
        }
        if(LauncherPreferences.PREF_BIG_CORE_AFFINITY) envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
        envMap.put("AWTSTUB_WIDTH", Integer.toString(CallbackBridge.windowWidth > 0 ? CallbackBridge.windowWidth : CallbackBridge.physicalWidth));
        envMap.put("AWTSTUB_HEIGHT", Integer.toString(CallbackBridge.windowHeight > 0 ? CallbackBridge.windowHeight : CallbackBridge.physicalHeight));

        GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
        if(!envMap.containsKey("LIBGL_ES") && LOCAL_RENDERER != null) {
            int glesMajor = info.glesMajorVersion;
            Log.i("glesDetect","GLES version detected: "+glesMajor);

            if (glesMajor < 3) {
                //fallback to 2 since it's the minimum for the entire app
                envMap.put("LIBGL_ES","2");
            } else if (LOCAL_RENDERER.startsWith("opengles3_desktopgl")) {
                envMap.put("LIBGL_ES", "3");
            } else if (LOCAL_RENDERER.startsWith("opengles")) {
                envMap.put("LIBGL_ES", LOCAL_RENDERER.replace("opengles", "").replace("_5", ""));
            } else {
                // TODO if can: other backends such as Vulkan.
                // Sure, they should provide GLES 3 support.
                envMap.put("LIBGL_ES", "3");
            }
        }

        if(info.isAdreno() && !PREF_ZINK_PREFER_SYSTEM_DRIVER) {
            envMap.put("POJAV_LOAD_TURNIP", "1");
        }

        readCustomEnv(envMap); // Must be last so it overrides anything the user sets for obvious reasons.

        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try {
                Os.setenv(env.getKey(), env.getValue(), true);
            }catch (NullPointerException exception){
                Log.e("JREUtils", exception.toString());
            }
        }

        File serverFile = new File(jreHome + "/" + Tools.DIRNAME_HOME_JRE + "/server/libjvm.so");
        jvmLibraryPath = jreHome + "/" + Tools.DIRNAME_HOME_JRE + "/" + (serverFile.exists() ? "server" : "client");
        Log.d("DynamicLoader","Base LD_LIBRARY_PATH: "+LD_LIBRARY_PATH);
        Log.d("DynamicLoader","Internal LD_LIBRARY_PATH: "+jvmLibraryPath+":"+LD_LIBRARY_PATH);
        setLdLibraryPath(jvmLibraryPath+":"+LD_LIBRARY_PATH);

        // return ldLibraryPath;
    }

    private static void readCustomEnv(Map<String, String> envMap) throws IOException {
        File customEnvFile = new File(Tools.DIR_GAME_HOME, "custom_env.txt");
        if (customEnvFile.exists() && customEnvFile.isFile()) {
            BufferedReader reader = new BufferedReader(new FileReader(customEnvFile));
            String line;
            while ((line = reader.readLine()) != null) {
                // Not use split() as only split first one
                int index = line.indexOf("=");
                envMap.put(line.substring(0, index), line.substring(index + 1));
            }
            reader.close();
        }
    }
    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
        String runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name).getAbsolutePath();

        JREUtils.relocateLibPath(runtime, runtimeHome);

        setJavaEnvironment(activity, runtimeHome);

        final String graphicsLib = loadGraphicsLibrary();
        List<String> userArgs = getJavaArgs(activity, runtimeHome, userArgsString);

        //Remove arguments that can interfere with the good working of the launcher
        purgeArg(userArgs,"-Xms");
        purgeArg(userArgs,"-Xmx");
        purgeArg(userArgs,"-d32");
        purgeArg(userArgs,"-d64");
        purgeArg(userArgs, "-Xint");
        purgeArg(userArgs, "-XX:+UseTransparentHugePages");
        purgeArg(userArgs, "-XX:+UseLargePagesInMetaspace");
        purgeArg(userArgs, "-XX:+UseLargePages");
        purgeArg(userArgs, "-Dorg.lwjgl.opengl.libname");
        purgeArg(userArgs, "-Dorg.lwjgl.spvc.libname");
        purgeArg(userArgs, "-Dorg.lwjgl.shaderc.libname");
        purgeArg(userArgs, "-Dorg.lwjgl.vma.libname");
        // Don't let the user specify a custom Freetype library (as the user is unlikely to specify a version compiled for Android)
        purgeArg(userArgs, "-Dorg.lwjgl.freetype.libname");
        // Overridden by us to specify the exact number of cores that the android system has
        purgeArg(userArgs, "-XX:ActiveProcessorCount");

        //Add automatically generated args
        userArgs.add("-Xms" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");
        userArgs.add("-Xmx" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");
        if (LauncherPreferences.PREF_RAM_ALLOCATION <= 1280) {
            userArgs.add("-XX:+UseSerialGC");
            userArgs.add("-XX:ReservedCodeCacheSize=48M");
        }
        if(LOCAL_RENDERER != null) userArgs.add("-Dorg.lwjgl.opengl.libname=" + graphicsLib);

        // LWJGL's Android builds do not publish a separate Freetype native JAR.
        // Use the Android native bundled with Battly, matching Mojo's launch path.
        userArgs.add("-Dorg.lwjgl.freetype.libname="+ NATIVE_LIB_DIR +"/libfreetype.so");
        userArgs.add("-Dorg.lwjgl.spvc.libname="+ NATIVE_LIB_DIR +"/libspirv-cross-c-shared.so");
        if (Tools.isValidString(Tools.lwjglNativesDir)) {
            userArgs.add("-Dorg.lwjgl.shaderc.libname="
                    + new File(Tools.lwjglNativesDir, "libshaderc.so").getAbsolutePath());
            userArgs.add("-Dorg.lwjgl.vma.libname="
                    + new File(Tools.lwjglNativesDir, "liblwjgl_vma.so").getAbsolutePath());
        }

        // Some phones are not using the right number of cores, fix that
        userArgs.add("-XX:ActiveProcessorCount=" + java.lang.Runtime.getRuntime().availableProcessors());
        // The injector patches LWJGL 2 and is compiled for Java 8. Attaching it
        // to modern Java/LWJGL 3 launches can abort the VM before Minecraft starts.
        File lwjgl2Injector = new File(
                Tools.DIR_DATA,
                "lwjgl2_methods_injector/lwjgl2_methods_injector.jar");
        if (runtime.javaVersion == 8 && hasJarEntry(
                lwjgl2Injector,
                "org/angelauramc/lwjgl2_methods_injector/startInjectors.class")) {
            userArgs.add("-javaagent:" + lwjgl2Injector.getAbsolutePath());
        } else if (runtime.javaVersion == 8) {
            Logger.appendToLog("Warning: LWJGL2 injector is missing or invalid; launching without it");
        }

        userArgs.addAll(JVMArgs);
        if (usesDesktopErrorWindowLoader(JVMArgs)) {
            // Cacio may have added its own headless flag to JVMArgs. Apply this last so
            // Fabric and Forge cannot replace the native Battly error screen with AWT.
            purgeArg(userArgs, "-Djava.awt.headless");
            userArgs.add("-Djava.awt.headless=true");
            Logger.appendToLog("Battly: desktop mod-loader error windows disabled on Android");
        }
        activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.autoram_info_msg,LauncherPreferences.PREF_RAM_ALLOCATION), Toast.LENGTH_SHORT).show());
        System.out.println(JVMArgs);

        initJavaRuntime(runtimeHome);
        JREUtils.setupExitMethod(activity.getApplication());
        JREUtils.initializeHooks();
        chdir(gameDirectory == null ? Tools.DIR_GAME_NEW : gameDirectory.getAbsolutePath());
        userArgs.add(0,"java"); //argv[0] is the program name according to C standard.

        final int exitCode = VMLauncher.launchJVM(userArgs.toArray(new String[0]));
        Logger.appendToLog("Java Exit code: " + exitCode);
        Telemetry.logGameExit(exitCode);
        net.kdt.pojavlaunch.LauncherActivity.openAfterGameExit(activity, exitCode, null);
        activity.finish();
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> android.os.Process.killProcess(android.os.Process.myPid()),
                450
        );
    }

    private static boolean usesDesktopErrorWindowLoader(List<String> gameArguments) {
        if (gameArguments == null) return false;
        for (String argument : gameArguments) {
            if (argument == null) continue;
            String lower = argument.toLowerCase(Locale.ROOT);
            if (lower.contains("net.fabricmc.loader")
                    || lower.contains("fabric-loader")
                    || lower.contains("org.quiltmc.loader")
                    || lower.contains("quilt-loader")
                    || lower.contains("net.minecraftforge")
                    || lower.contains("modlauncher")
                    || lower.contains("net.neoforged")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasJarEntry(File jarFile, String entryName) {
        if (jarFile == null || !jarFile.isFile()) return false;
        try (JarFile jar = new JarFile(jarFile)) {
            return jar.getJarEntry(entryName) != null;
        } catch (IOException e) {
            Logger.appendToLog("Warning: unable to inspect Java agent "
                    + jarFile.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    public static int launchApiInstaller(Context context, Runtime runtime, File workDirectory, ArrayList<String> args) {
        try {
            String runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name).getAbsolutePath();
            redirectAndPrintJRELog();
            relocateLibPath(runtime, runtimeHome);
            setHeadlessJavaEnvironment(runtimeHome);
            File serverFile = new File(runtimeHome + "/" + Tools.DIRNAME_HOME_JRE + "/server/libjvm.so");
            jvmLibraryPath = runtimeHome + "/" + Tools.DIRNAME_HOME_JRE + "/" + (serverFile.exists() ? "server" : "client");
            Log.d("DynamicLoader", "Base LD_LIBRARY_PATH: " + LD_LIBRARY_PATH);
            Log.d("DynamicLoader", "Internal LD_LIBRARY_PATH: " + jvmLibraryPath + ":" + LD_LIBRARY_PATH);
            setLdLibraryPath(jvmLibraryPath + ":" + LD_LIBRARY_PATH);
            initJavaRuntime(runtimeHome);
            setupExitMethod(context);
            FileUtils.ensureDirectory(workDirectory);
            chdir(workDirectory.getAbsolutePath());

            ArrayList<String> userArgs = new ArrayList<>(args);
            addHeadlessJvmArg(userArgs, "-Djava.home=", runtimeHome);
            addHeadlessJvmArg(userArgs, "-Djava.io.tmpdir=", Tools.DIR_CACHE.getAbsolutePath());
            File nativeExtractDir = getNativeExtractDir();
            addHeadlessJvmArg(userArgs, "-Djna.tmpdir=", nativeExtractDir.getAbsolutePath());
            addHeadlessJvmArg(userArgs, "-Dorg.lwjgl.system.SharedLibraryExtractPath=", nativeExtractDir.getAbsolutePath());
            addHeadlessJvmArg(userArgs, "-Dorg.lwjgl.system.SharedLibraryExtractDirectory=", "lwjgl");
            addHeadlessJvmArg(userArgs, "-Dio.netty.native.workdir=", nativeExtractDir.getAbsolutePath());
            addHeadlessJvmArg(userArgs, "-Duser.home=", Tools.DIR_GAME_HOME);
            addHeadlessJvmArg(userArgs, "-Dos.name=", "Linux");
            addHeadlessJvmArg(userArgs, "-Dos.version=", "Android-" + Build.VERSION.RELEASE);
            addHeadlessJvmArg(userArgs, "-Duser.language=", System.getProperty("user.language"));
            addHeadlessJvmArg(userArgs, "-Duser.timezone=", TimeZone.getDefault().getID());
            addHeadlessJvmArg(userArgs, "-Djdk.lang.Process.launchMechanism=", "FORK");
            Logger.appendToLog("Launching headless installer in " + workDirectory.getAbsolutePath());
            Logger.appendToLog("Headless installer args: " + userArgs);
            userArgs.add(0, "java");
            int exitCode = VMLauncher.launchJVM(userArgs.toArray(new String[0]));
            Logger.appendToLog("Java Exit code: " + exitCode);
            return exitCode;
        } catch (Throwable throwable) {
            Log.e("JREUtils", "Failed to launch headless installer", throwable);
            Logger.appendToLog("Headless installer failed:\n" + Log.getStackTraceString(throwable));
            return -1;
        }
    }

    private static void addHeadlessJvmArg(List<String> args, String prefix, String value) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return;
            }
        }
        args.add(0, prefix + value);
    }

    /**
     *  Gives an argument list filled with both the user args
     *  and the auto-generated ones (eg. the window resolution).
     * @param ctx The application context
     * @return A list filled with args.
     */
    public static List<String> getJavaArgs(Context ctx, String runtimeHome, String userArgumentsString) {
        List<String> userArguments = parseJavaArguments(userArgumentsString);
        String resolvFile;
        resolvFile = new File(Tools.DIR_DATA,"resolv.conf").getAbsolutePath();
        File nativeExtractDir = getNativeExtractDir();

        ArrayList<String> overridableArguments = new ArrayList<>(Arrays.asList(
                "-Djava.home=" + runtimeHome,
                "-Djava.io.tmpdir=" + Tools.DIR_CACHE.getAbsolutePath(),
                "-Djna.tmpdir=" + nativeExtractDir.getAbsolutePath(),
                "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + nativeExtractDir.getAbsolutePath(),
                "-Dorg.lwjgl.system.SharedLibraryExtractDirectory=lwjgl",
                "-Dio.netty.native.workdir=" + nativeExtractDir.getAbsolutePath(),
                "-Djna.boot.library.path=" + NATIVE_LIB_DIR,
                "-Duser.home=" + Tools.DIR_GAME_HOME,
                "-Duser.language=" + System.getProperty("user.language"),
                "-Dos.name=Linux",
                "-Dos.version=Android-" + Build.VERSION.RELEASE,
                "-Dpojav.path.minecraft=" + Tools.DIR_GAME_NEW,
                "-Dpojav.path.private.account=" + Tools.DIR_ACCOUNT_NEW,
                "-Duser.timezone=" + TimeZone.getDefault().getID(),

                "-Dorg.lwjgl.vulkan.libname=libvulkan.so",
                //LWJGL 3 DEBUG FLAGS
                //"-Dorg.lwjgl.util.Debug=true",
                //"-Dorg.lwjgl.util.DebugFunctions=true",
                //"-Dorg.lwjgl.util.DebugLoader=true",
                // GLFW Stub width height
                "-Dglfwstub.windowWidth=" + resolveBridgeDimension(
                        org.lwjgl.glfw.CallbackBridge.windowWidth,
                        Math.max(currentDisplayMetrics.widthPixels, currentDisplayMetrics.heightPixels)),
                "-Dglfwstub.windowHeight=" + resolveBridgeDimension(
                        org.lwjgl.glfw.CallbackBridge.windowHeight,
                        Math.min(currentDisplayMetrics.widthPixels, currentDisplayMetrics.heightPixels)),
                "-Dglfwstub.initEgl=false",
                "-Dext.net.resolvPath=" +resolvFile,
                "-Dlog4j2.formatMsgNoLookups=true", //Log4j RCE mitigation

                "-Dnet.minecraft.clientmodname=" + Tools.APP_NAME,
                "-Dfml.earlyprogresswindow=false", //Forge 1.14+ workaround
                "-Dloader.disable_forked_guis=true",
                "-Djdk.lang.Process.launchMechanism=FORK" // Default is POSIX_SPAWN which requires starting jspawnhelper, which doesn't work on Android
        ));
        if(LauncherPreferences.PREF_ARC_CAPES) {
            overridableArguments.add("-javaagent:"+new File(Tools.DIR_DATA,"arc_dns_injector/arc_dns_injector.jar").getAbsolutePath()+"=23.95.137.176");
        }
        List<String> additionalArguments = new ArrayList<>();
        for(String arg : overridableArguments) {
            String strippedArg = arg.substring(0,arg.indexOf('='));
            boolean add = true;
            for(String uarg : userArguments) {
                if(uarg.startsWith(strippedArg)) {
                    add = false;
                    break;
                }
            }
            if(add)
                additionalArguments.add(arg);
            else
                Log.i("ArgProcessor","Arg skipped: "+arg);
        }

        //Add all the arguments
        userArguments.addAll(additionalArguments);
        return userArguments;
    }

    private static int resolveBridgeDimension(int bridgeDimension, int displayFallback) {
        if (bridgeDimension > 0) {
            return bridgeDimension;
        }
        return Tools.getDisplayFriendlyRes(displayFallback, LauncherPreferences.PREF_SCALE_FACTOR);
    }

    private static File getNativeExtractDir() {
        File nativeExtractDir = new File(Tools.DIR_CACHE, "native_extract");
        ensureWritableDirectory(nativeExtractDir);
        ensureWritableDirectory(new File(nativeExtractDir, "lwjgl"));
        return nativeExtractDir;
    }

    private static void ensureWritableDirectory(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Logger.appendToLog("Warning: could not create native work directory: " + directory.getAbsolutePath());
            return;
        }
        if (!directory.canWrite()) {
            Logger.appendToLog("Warning: native work directory is not writable: " + directory.getAbsolutePath());
        }
    }

    /**
     * Parse and separate java arguments in a user friendly fashion
     * It supports multi line and absence of spaces between arguments
     * The function also supports auto-removal of improper arguments, although it may miss some.
     *
     * @param args The un-parsed argument list.
     * @return Parsed args as an ArrayList
     */
    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        if (args == null) return parsedArguments;
        args = args.trim().replace(" ", "");
        if (args.isEmpty()) return parsedArguments;
        //For each prefixes, we separate args.
        String[] separators = new String[]{"-XX:-","-XX:+", "-XX:","--", "-D", "-X", "-javaagent:", "-verbose"};
        for(String prefix : separators){
            while (true){
                int start = args.indexOf(prefix);
                if(start == -1) break;
                //Get the end of the current argument by checking the nearest separator
                int end = -1;
                for(String separator: separators){
                    int tempEnd = args.indexOf(separator, start + prefix.length());
                    if(tempEnd == -1) continue;
                    if(end == -1){
                        end = tempEnd;
                        continue;
                    }
                    end = Math.min(end, tempEnd);
                }
                //Fallback
                if(end == -1) end = args.length();

                //Extract it
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");

                //Check if two args aren't bundled together by mistake
                if(parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if(arraySize > 0){
                        String lastString = parsedArguments.get(arraySize - 1);
                        // Looking for list elements
                        if(lastString.charAt(lastString.length() - 1) == ',' ||
                                parsedSubString.contains(",")){
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                }
                else Log.w("JAVA ARGS PARSER", "Removed improper arguments: " + parsedSubString);
            }
        }
        return parsedArguments;
    }

    private static void setHeadlessJavaEnvironment(String runtimeHome) throws ErrnoException {
        Os.setenv("POJAV_NATIVEDIR", NATIVE_LIB_DIR, true);
        Os.setenv("JAVA_HOME", runtimeHome, true);
        Os.setenv("HOME", Tools.DIR_GAME_HOME, true);
        Os.setenv("TMPDIR", Tools.DIR_CACHE.getAbsolutePath(), true);
        Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true);
        String currentPath = Os.getenv("PATH");
        if (currentPath == null) {
            currentPath = "";
        }
        Os.setenv("PATH", runtimeHome + "/bin:" + currentPath, true);
    }

    /**
     * Open the render library in accordance to the settings.
     * It will fallback if it fails to load the library.
     * @return The name of the loaded library
     */
    public static String loadGraphicsLibrary(){
        if(LOCAL_RENDERER == null) return null;
        String renderLibrary;
        String runtimeRenderer = runtimeRendererForGraphics(LOCAL_RENDERER);
        switch (runtimeRenderer){
            case "opengles2":
            case "opengles2_5":
            case "opengles3":
                renderLibrary = "libgl4es_114.so"; break;
            case "vulkan_zink": renderLibrary = "libOSMesa.so"; break;
            case "opengles_mobileglues": renderLibrary = "libmobileglues.so"; break;
            case "opengles3_desktopgl_zink_kopper": renderLibrary = "libglxshim.so"; break;
            case "opengles3_desktopgl_freedreno": renderLibrary = "libglxshim.so"; break;
            case "opengles3_ltw" : renderLibrary = "libltw.so"; break;
            default:
                Log.w("RENDER_LIBRARY", "No renderer selected, defaulting to opengles2");
                renderLibrary = "libgl4es_114.so";
                break;
        }

        if (!dlopen(renderLibrary) && !dlopen(findInLdLibPath(renderLibrary))) {
            Log.e("RENDER_LIBRARY","Failed to load renderer " + renderLibrary + ". Falling back to GL4ES 1.1.4");
            LOCAL_RENDERER = "opengles2";
            renderLibrary = "libgl4es_114.so";
            dlopen(NATIVE_LIB_DIR + "/libgl4es_114.so");
        }
        return renderLibrary;
    }

    private static String runtimeRendererForGraphics(String rendererId) {
        return rendererId;
    }

    /**
     * Remove the argument from the list, if it exists
     * If the argument exists multiple times, they will all be removed.
     * @param argList The argument list to purge
     * @param argStart The argument to purge from the list.
     */
    private static void purgeArg(List<String> argList, String argStart) {
        Iterator<String> args = argList.iterator();
        while(args.hasNext()) {
            String arg = args.next();
            if(arg.startsWith(argStart)) args.remove();
        }
    }
    private static final int EGL_OPENGL_ES_BIT = 0x0001;
    private static final int EGL_OPENGL_ES2_BIT = 0x0004;
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;
    @SuppressWarnings("SameParameterValue")
    private static boolean hasExtension(String extensions, String name) {
        int start = extensions.indexOf(name);
        while (start >= 0) {
            // check that we didn't find a prefix of a longer extension name
            int end = start + name.length();
            if (end == extensions.length() || extensions.charAt(end) == ' ') {
                return true;
            }
            start = extensions.indexOf(name, end);
        }
        return false;
    }

    public static int getDetectedVersion() {
        return GLInfoUtils.getGlInfo().glesMajorVersion;
    }
    public static native int chdir(String path);
    public static native boolean dlopen(String libPath);
    public static native void setLdLibraryPath(String ldLibraryPath);
    public static native void setupBridgeWindow(Object surface);
    public static native void releaseBridgeWindow();
    public static native void initializeHooks();
    public static native void setupExitMethod(Context context);
    public static native int getRendererFps();
    // Obtain AWT screen pixels to render on Android SurfaceView
    public static native int[] renderAWTScreenFrame(/* Object canvas, int width, int height */);
    static {
        System.loadLibrary("exithook");
        System.loadLibrary("pojavexec");
        System.loadLibrary("pojavexec_awt");
    }
}
