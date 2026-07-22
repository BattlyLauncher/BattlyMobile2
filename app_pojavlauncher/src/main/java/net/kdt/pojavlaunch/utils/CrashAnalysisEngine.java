package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic local crash diagnosis; no log is uploaded. */
public final class CrashAnalysisEngine {
    private static final int MAX_LOG_CHARS = 2_000_000;
    private static final Pattern MOD_JAR = Pattern.compile("(?:from mod |from )([^\\s/\\\\]+\\.jar)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGE_LANGUAGE_MISMATCH = Pattern.compile(
            "Mod File ([^\\r\\n]+?\\.jar) needs language provider javafml:([0-9]+) or above.*?found ([0-9.]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private CrashAnalysisEngine() {
    }

    public static Report analyze(MinecraftProfile profile, int exitCode, String details) {
        StringBuilder log = new StringBuilder();
        appendFile(log, new File(Tools.DIR_GAME_HOME, "latestlog.txt"));
        File crashDir = new File(Tools.getGameDirPath(profile), "crash-reports");
        File latestCrash = newest(crashDir);
        appendFile(log, latestCrash);
        if (details != null) log.append('\n').append(details);
        String source = log.toString();
        String lower = source.toLowerCase(Locale.ROOT);
        List<Finding> findings = new ArrayList<>();

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
        if (containsAny(lower, "mixintweaker", "mixinapplyerror", "mixintransformererror", "invalidmixinexception")) {
            findings.add(new Finding(Severity.HIGH, "mixin", "A mod or its Mixin dependency is incompatible",
                    "Disable the last installed mod or install its required Mixin provider.", Action.DISABLE_SUSPECT_MOD,
                    findSuspectJar(source)));
        }
        if (containsAny(lower, "failed to locate library", "unsatisfiedlinkerror", "loading native libraries",
                "liblwjgl", "libglfw")) {
            findings.add(new Finding(Severity.HIGH, "native", "Native graphics libraries could not be loaded",
                    "Switch renderer and clear the LWJGL native cache.", Action.RESET_RENDERER, null));
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
        return new Report(exitCode, latestCrash, Collections.unmodifiableList(findings));
    }

    public static boolean applyRecovery(@NonNull MinecraftProfile profile, @NonNull Finding finding) throws IOException {
        File gameDir = Tools.getGameDirPath(profile);
        switch (finding.action) {
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

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) if (source.contains(needle)) return true;
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

        Report(int exitCode, File crashReport, List<Finding> findings) {
            this.exitCode = exitCode;
            this.crashReport = crashReport;
            this.findings = findings;
        }
    }
}
