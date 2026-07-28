package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic local crash diagnosis. Uploading is handled separately and only on user action. */
public final class CrashAnalysisEngine {
    private static final int MAX_LOG_CHARS = 2_000_000;
    private static final Pattern MOD_JAR = Pattern.compile("(?:from mod |from )([^\\s/\\\\]+\\.jar)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIXIN_CONFIG = Pattern.compile(
            "(?:mixin apply failed |mixin apply for mod )([^\\s.:]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGE_LANGUAGE_MISMATCH = Pattern.compile(
            "Mod File ([^\\r\\n]+?\\.jar) needs language provider javafml:([0-9]+) or above.*?found ([0-9.]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private CrashAnalysisEngine() {
    }

    public static Report analyze(MinecraftProfile profile, int exitCode, String details) {
        StringBuilder log = new StringBuilder();
        appendFile(log, new File(Tools.DIR_GAME_HOME, "latestlog.txt"));
        File gameDir = profile == null ? new File(Tools.DIR_GAME_HOME) : Tools.getGameDirPath(profile);
        File crashDir = new File(gameDir, "crash-reports");
        File latestCrash = newest(crashDir);
        appendFile(log, latestCrash);
        if (details != null) log.append('\n').append(details);
        String source = log.toString();
        String lower = source.toLowerCase(Locale.ROOT);
        List<Finding> findings = new ArrayList<>();
        ModCompatibilityAnalyzer.Analysis modCompatibility =
                ModCompatibilityAnalyzer.analyze(source);

        if (modCompatibility.detected) {
            findings.add(new Finding(Severity.HIGH, "mod_dependencies",
                    modCompatibility.summary,
                    Tools.isValidString(modCompatibility.solution)
                            ? modCompatibility.solution
                            : "Review the incompatible mods and install versions made for this Minecraft and loader version.",
                    Action.OPEN_LOG, null));
        }

        if (containsAny(lower, "org/lwjgl/sdl/sdlplatform", "org/lwjgl/sdl/sdl",
                "required lwjgl sdl module is missing")) {
            findings.add(new Finding(Severity.HIGH, "lwjgl_sdl",
                    "The LWJGL SDL module required by this Minecraft version is missing",
                    "Restart Battly so it can repair the LWJGL 3.4.1 component, then launch the game again.",
                    Action.OPEN_LOG, null));
        }
        if (containsAny(lower,
                "modules text2speech and android.text2speech.stub export package",
                "classnotfoundexception: com.mojang.text2speech.narratorlinux",
                "nosuchmethoderror: com.mojang.text2speech.narrator$initializeexception")) {
            findings.add(new Finding(Severity.HIGH, "text_to_speech",
                    "Battly's Android narrator component is inconsistent",
                    "Restart Battly to reinstall the narrator adapter. Desktop text2speech libraries are now removed automatically.",
                    Action.OPEN_LOG, null));
        }
        if (lower.contains("signer information does not match signer information of other classes in the same package")) {
            findings.add(new Finding(Severity.HIGH, "corrupt_version",
                    "This Minecraft installation contains mixed or corrupted classes",
                    "Repair or reinstall this Minecraft version. Its client JAR contains classes with incompatible signatures.",
                    Action.OPEN_INSTANCE, null));
        }

        Matcher forgeMismatch = FORGE_LANGUAGE_MISMATCH.matcher(source);
        if (forgeMismatch.find()) {
            String modFile = new File(forgeMismatch.group(1).trim()).getName();
            findings.add(new Finding(Severity.HIGH, "forge_version",
                    modFile + " is for a newer Forge version",
                    "This mod requires Forge " + forgeMismatch.group(2)
                            + " or newer, but this instance uses Forge " + forgeMismatch.group(3)
                            + ". Install its Minecraft 1.16.5 build or disable it.",
                    Action.DISABLE_SUSPECT_MOD, modFile));
        }
        if (containsAny(lower, "outofmemoryerror", "could not reserve enough space", "java heap space")) {
            findings.add(new Finding(Severity.HIGH, "memory", "Minecraft ran out of memory",
                    "Lower the allocated RAM or close background apps.", Action.LOWER_MEMORY, null));
        }
        if (containsAny(lower, "mixintweaker", "mixinapplyerror", "mixintransformererror",
                "invalidmixinexception", "invalidinjectionexception", "mixin apply failed")) {
            String suspectMod = findSuspectMod(gameDir, source);
            findings.add(new Finding(Severity.HIGH, "mixin", "A mod or its Mixin dependency is incompatible",
                    suspectMod == null
                            ? "Disable the mod named immediately before the first Mixin error, or install its build for this exact Minecraft version."
                            : "Disable " + suspectMod + " or install its build for this exact Minecraft and loader version.",
                    Action.DISABLE_SUSPECT_MOD, suspectMod));
        }
        if (!modCompatibility.detected && !hasFinding(findings, "lwjgl_sdl") && containsAny(lower,
                "failed to locate library", "unsatisfiedlinkerror", "loading native libraries",
                "liblwjgl", "libglfw")) {
            findings.add(new Finding(Severity.HIGH, "native", "Native graphics libraries could not be loaded",
                    "Switch renderer and clear the LWJGL native cache.", Action.RESET_RENDERER, null));
        }
        if (containsAny(lower, "wrong elf class", "unexpected e_machine", "bad elf magic",
                "is for em_386 instead of em_aarch64")) {
            findings.add(new Finding(Severity.HIGH, "native_architecture",
                    "A native library was built for a different CPU architecture",
                    "Repair the instance and renderer files. Do not copy native libraries from another device.",
                    Action.RESET_RENDERER, null));
        }
        if (containsAny(lower, "glfw error 65542", "failed to create opengl context",
                "could not create gl context", "failed to create window", "egl_bad_match",
                "egl_bad_attribute", "no supported graphics backend was found")) {
            findings.add(new Finding(Severity.HIGH, "graphics_context",
                    "The renderer could not create a compatible graphics context",
                    "Reset the renderer to Automatic. If it repeats, select Holy GL4ES for old versions or Zink/MobileGlues for modern versions.",
                    Action.RESET_RENDERER, null));
        }
        if (!modCompatibility.detected && containsAny(lower, "incompatible mod set", "incompatible mods found",
                "mod resolution encountered", "requires version", "depends on version")
                && !hasFinding(findings, "forge_version")) {
            findings.add(new Finding(Severity.HIGH, "mod_dependencies",
                    "One or more mods have incompatible or missing dependencies",
                    "Open the full log, then install the required dependency version or disable the conflicting mod.",
                    Action.DISABLE_SUSPECT_MOD, findSuspectJar(source)));
        }
        if (lower.contains("pose stack not empty")) {
            findings.add(new Finding(Severity.HIGH, "mod_render_state",
                    "A rendering mod left Minecraft in an invalid render state",
                    "Update or disable the rendering, animation or HUD mod listed immediately before this error.",
                    Action.OPEN_LOG, null));
        }
        if (!modCompatibility.detected && lower.contains("java.awt.insets.initids")) {
            findings.add(new Finding(Severity.MEDIUM, "mod_error_screen",
                    "A mod tried to open a desktop Java error window on Android",
                    "The real failure appears earlier in the log. Resolve the incompatible mods listed before this AWT error.",
                    Action.OPEN_LOG, null));
        }
        if (containsAny(lower, "sslhandshakeexception", "unknownhostexception", "connection timed out",
                "failed to download file")) {
            findings.add(new Finding(Severity.MEDIUM, "network",
                    "A required file could not be downloaded",
                    "Check the connection, disable DNS or VPN filters temporarily, and repair the instance.",
                    Action.OPEN_LOG, null));
        }
        if (containsAny(lower, "shader compilation", "couldn't compile", "glsl", "texture2d is removed")) {
            findings.add(new Finding(Severity.MEDIUM, "shader", "The selected shader is incompatible with this renderer",
                    "Disable the shader pack or use a desktop OpenGL renderer.", Action.DISABLE_SHADERS, null));
        }
        if (containsAny(lower, "unsupportedclassversionerror", "class file version")) {
            findings.add(new Finding(Severity.HIGH, "java", "The selected Java runtime is incompatible",
                    "Use the Java version recommended for this Minecraft version.", Action.RESET_JAVA, null));
        }
        if (containsAny(lower, "accessdeniedexception", "permission denied", "read-only file system")) {
            findings.add(new Finding(Severity.HIGH, "storage", "Minecraft could not write to its instance directory",
                    "Repair the instance path and check free storage.", Action.OPEN_INSTANCE, null));
        }
        if (exitCode != 0 && findings.isEmpty()) {
            findings.add(new Finding(Severity.MEDIUM, "unknown", "Minecraft exited unexpectedly (" + exitCode + ")",
                    "Open the complete log for the first Caused by section.", Action.OPEN_LOG, null));
        }
        return new Report(exitCode, latestCrash, Collections.unmodifiableList(findings),
                modCompatibility);
    }

    public static boolean applyRecovery(@NonNull MinecraftProfile profile, @NonNull Finding finding) throws IOException {
        File gameDir = Tools.getGameDirPath(profile);
        switch (finding.action) {
            case LOWER_MEMORY:
                if (LauncherPreferences.DEFAULT_PREF == null) return false;
                int loweredMemory = Math.max(512, LauncherPreferences.PREF_RAM_ALLOCATION - 256);
                LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", loweredMemory).apply();
                LauncherPreferences.PREF_RAM_ALLOCATION = loweredMemory;
                return true;
            case RESET_RENDERER:
                profile.pojavRendererName = null;
                deleteMatching(new File(Tools.DIR_CACHE.getAbsolutePath()), "lwjgl_");
                return true;
            case DISABLE_SHADERS:
                File options = new File(gameDir, "options.txt");
                if (!options.isFile()) return false;
                String content = Tools.read(options.getAbsolutePath())
                        .replaceAll("(?m)^shaderPack=.*$", "shaderPack=OFF");
                Tools.write(options.getAbsolutePath(), content);
                return true;
            case DISABLE_SUSPECT_MOD:
                if (!Tools.isValidString(finding.relatedFile)) return false;
                File mod = new File(new File(gameDir, "mods"), finding.relatedFile);
                if (!mod.isFile()) return false;
                return mod.renameTo(new File(mod.getParentFile(), mod.getName() + ".disabledmod"));
            case RESET_JAVA:
                profile.javaDir = null;
                return true;
            default:
                return false;
        }
    }

    public static boolean canApplyRecovery(Finding finding) {
        if (finding == null) return false;
        switch (finding.action) {
            case LOWER_MEMORY:
            case RESET_RENDERER:
            case DISABLE_SHADERS:
            case RESET_JAVA:
                return true;
            case DISABLE_SUSPECT_MOD:
                return Tools.isValidString(finding.relatedFile);
            default:
                return false;
        }
    }

    private static void appendFile(StringBuilder destination, File file) {
        if (file == null || !file.isFile() || destination.length() >= MAX_LOG_CHARS) return;
        try {
            String value = Tools.read(file.getAbsolutePath());
            int remaining = MAX_LOG_CHARS - destination.length();
            destination.append('\n').append(value, 0, Math.min(value.length(), remaining));
        } catch (IOException ignored) {
        }
    }

    private static File newest(File directory) {
        File[] files = directory == null ? null : directory.listFiles(File::isFile);
        File newest = null;
        if (files != null) for (File file : files) {
            if (newest == null || file.lastModified() > newest.lastModified()) newest = file;
        }
        return newest;
    }

    private static String findSuspectJar(String source) {
        Matcher matcher = MOD_JAR.matcher(source);
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        return last;
    }

    private static String findSuspectMod(File gameDir, String source) {
        String jar = findSuspectJar(source);
        if (Tools.isValidString(jar)) return jar;
        Matcher matcher = MIXIN_CONFIG.matcher(source);
        String modId = null;
        while (matcher.find()) modId = matcher.group(1);
        if (!Tools.isValidString(modId)) return null;
        final String normalizedModId = modId.toLowerCase(Locale.ROOT);
        File modsDir = new File(gameDir, "mods");
        File[] candidates = modsDir.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).startsWith(normalizedModId)
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        return candidates != null && candidates.length == 1 ? candidates[0].getName() : null;
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) if (source.contains(needle)) return true;
        return false;
    }

    private static boolean hasFinding(List<Finding> findings, String code) {
        for (Finding finding : findings) if (code.equals(finding.code)) return true;
        return false;
    }

    private static void deleteMatching(File directory, String prefix) throws IOException {
        File[] files = directory.listFiles(file -> file.getName().startsWith(prefix));
        if (files != null) for (File file : files) {
            if (file.isDirectory()) org.apache.commons.io.FileUtils.deleteDirectory(file);
            else file.delete();
        }
    }

    public enum Severity { LOW, MEDIUM, HIGH }
    public enum Action { LOWER_MEMORY, DISABLE_SUSPECT_MOD, RESET_RENDERER, DISABLE_SHADERS,
        RESET_JAVA, OPEN_INSTANCE, OPEN_LOG }

    public static final class Finding {
        public final Severity severity;
        public final String code;
        public final String title;
        public final String recommendation;
        public final Action action;
        public final String relatedFile;

        Finding(Severity severity, String code, String title, String recommendation,
                Action action, String relatedFile) {
            this.severity = severity;
            this.code = code;
            this.title = title;
            this.recommendation = recommendation;
            this.action = action;
            this.relatedFile = relatedFile;
        }
    }

    public static final class Report {
        public final int exitCode;
        public final File crashReport;
        public final List<Finding> findings;
        public final ModCompatibilityAnalyzer.Analysis modCompatibility;

        Report(int exitCode, File crashReport, List<Finding> findings,
               ModCompatibilityAnalyzer.Analysis modCompatibility) {
            this.exitCode = exitCode;
            this.crashReport = crashReport;
            this.findings = findings;
            this.modCompatibility = modCompatibility;
        }
    }
}
