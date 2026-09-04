package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Detects shader loaders and an actively selected shader pack without loading mod classes. */
public final class ShaderRuntimeDetector {
    private static final int MAX_CONFIG_LINES = 2048;

    private ShaderRuntimeDetector() {
    }

    @NonNull
    public static Result detect(File gameDir) {
        if (gameDir == null || !gameDir.isDirectory()) return Result.NONE;

        boolean pipelineInstalled = hasShaderPipeline(new File(gameDir, "mods"));
        File irisConfig = new File(gameDir, "config/iris.properties");
        Selection selection = irisConfig.isFile()
                ? readSelection(irisConfig)
                : readSelection(new File(gameDir, "optionsshaders.txt"));
        // OptiFine can also be installed as the selected version instead of a mod JAR.
        if (!pipelineInstalled && new File(gameDir, "optionsshaders.txt").isFile()) {
            pipelineInstalled = true;
        }
        return new Result(pipelineInstalled, pipelineInstalled && selection.enabled,
                selection.packName);
    }

    private static boolean hasShaderPipeline(File modsDir) {
        File[] files = modsDir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".jar") || name.endsWith(".jar.disabled")) continue;
            if (name.contains("iris") || name.contains("oculus")
                    || name.contains("optifine") || name.contains("canvas")) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static Selection readSelection(File file) {
        if (!file.isFile() || file.length() > 1024L * 1024L) return Selection.NONE;
        boolean explicitlyEnabled = true;
        String packName = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lines = 0;
            while ((line = reader.readLine()) != null && lines++ < MAX_CONFIG_LINES) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int separator = trimmed.indexOf('=');
                if (separator < 0) separator = trimmed.indexOf(':');
                if (separator < 0) continue;
                String key = trimmed.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = trimmed.substring(separator + 1).trim();
                if ("enableshaders".equals(key)) {
                    explicitlyEnabled = Boolean.parseBoolean(value);
                } else if ("shaderpack".equals(key) || key.endsWith(".shaderpack")) {
                    packName = value;
                }
            }
        } catch (Exception ignored) {
            return Selection.NONE;
        }
        boolean selected = explicitlyEnabled && !packName.isEmpty()
                && !"off".equalsIgnoreCase(packName)
                && !"none".equalsIgnoreCase(packName)
                && !"false".equalsIgnoreCase(packName);
        return new Selection(selected, selected ? packName : "");
    }

    private static final class Selection {
        static final Selection NONE = new Selection(false, "");
        final boolean enabled;
        final String packName;

        Selection(boolean enabled, String packName) {
            this.enabled = enabled;
            this.packName = packName;
        }
    }

    public static final class Result {
        static final Result NONE = new Result(false, false, "");
        public final boolean pipelineInstalled;
        public final boolean shaderEnabled;
        public final String packName;

        Result(boolean pipelineInstalled, boolean shaderEnabled, String packName) {
            this.pipelineInstalled = pipelineInstalled;
            this.shaderEnabled = shaderEnabled;
            this.packName = packName == null ? "" : packName;
        }
    }
}
