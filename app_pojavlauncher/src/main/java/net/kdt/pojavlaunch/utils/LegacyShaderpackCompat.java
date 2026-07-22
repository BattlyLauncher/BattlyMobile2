package net.kdt.pojavlaunch.utils;

import android.util.Log;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class LegacyShaderpackCompat {
    private static final String TAG = "LegacyShaderpackCompat";
    private static final String[] LOD_FUNCTIONS = {"texture2DLodEXT", "texture2DLod"};

    private LegacyShaderpackCompat() {
    }

    public static void applyIfNeeded(File gameDir, JMinecraftVersionList.Version versionInfo) {
        if (gameDir == null || versionInfo == null || !isLegacyVersion(versionInfo)) {
            return;
        }
        File shaderpack = getSelectedShaderpack(gameDir);
        if (shaderpack == null || (!shaderpack.isFile() && !shaderpack.isDirectory())) {
            return;
        }
        try {
            boolean patched = shaderpack.isDirectory()
                    ? patchDirectoryShaderpack(shaderpack)
                    : isZipShaderpack(shaderpack) && patchZipShaderpack(shaderpack);
            if (patched) {
                Logger.appendToLog("Info: Patched legacy shaderpack for Android compatibility: " + shaderpack.getName());
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to patch shaderpack " + shaderpack.getAbsolutePath(), throwable);
        }
    }

    private static boolean isLegacyVersion(JMinecraftVersionList.Version versionInfo) {
        String id = safe(versionInfo.id);
        String inheritsFrom = safe(versionInfo.inheritsFrom);
        String version = inheritsFrom.isEmpty() ? id : inheritsFrom;
        return version.startsWith("1.7")
                || version.startsWith("1.8")
                || version.startsWith("1.9")
                || version.startsWith("1.10")
                || version.startsWith("1.11")
                || version.startsWith("1.12");
    }

    private static File getSelectedShaderpack(File gameDir) {
        File options = new File(gameDir, "optionsshaders.txt");
        if (!options.isFile()) {
            return null;
        }
        String selected = null;
        try {
            String[] lines = Tools.read(options.getAbsolutePath()).split("\\r?\\n");
            for (String line : lines) {
                if (line.startsWith("shaderPack=")) {
                    selected = line.substring("shaderPack=".length()).trim();
                    break;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Unable to read optionsshaders.txt", e);
        }
        if (!Tools.isValidString(selected)
                || "OFF".equalsIgnoreCase(selected)
                || "(internal)".equalsIgnoreCase(selected)) {
            return null;
        }
        File shaderpacksDir = new File(gameDir, "shaderpacks");
        return new File(shaderpacksDir, selected);
    }

    private static boolean isZipShaderpack(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip");
    }

    private static boolean patchDirectoryShaderpack(File shaderpack) throws IOException {
        File[] files = shaderpack.listFiles();
        if (files == null) {
            return false;
        }
        boolean changed = false;
        for (File file : files) {
            if (file.isDirectory()) {
                changed |= patchDirectoryShaderpack(file);
                continue;
            }
            if (!isShaderSource(file.getName())) {
                continue;
            }
            String original = Tools.read(file.getAbsolutePath());
            String patched = patchShaderSource(original);
            if (original.equals(patched)) {
                continue;
            }
            File backup = new File(file.getParentFile(), file.getName() + ".battly-original");
            if (!backup.isFile()) {
                copyFile(file, backup);
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(patched.getBytes(StandardCharsets.UTF_8));
            }
            changed = true;
        }
        return changed;
    }

    private static boolean patchZipShaderpack(File shaderpack) throws IOException {
        File backup = new File(shaderpack.getParentFile(), shaderpack.getName() + ".battly-original");
        File temp = new File(shaderpack.getParentFile(), shaderpack.getName() + ".battly-tmp");
        boolean changed = false;

        try (ZipFile zipFile = new ZipFile(shaderpack);
             ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                ZipEntry outEntry = new ZipEntry(entry.getName());
                outEntry.setTime(entry.getTime());
                output.putNextEntry(outEntry);

                byte[] data;
                try (BufferedInputStream input = new BufferedInputStream(zipFile.getInputStream(entry))) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    IOUtils.copy(input, buffer);
                    data = buffer.toByteArray();
                }

                if (!entry.isDirectory() && isShaderSource(entry.getName())) {
                    String original = new String(data, StandardCharsets.UTF_8);
                    String patched = patchShaderSource(original);
                    if (!original.equals(patched)) {
                        changed = true;
                        data = patched.getBytes(StandardCharsets.UTF_8);
                    }
                }

                try (ByteArrayInputStream patchedInput = new ByteArrayInputStream(data)) {
                    IOUtils.copy(patchedInput, output);
                }
                output.closeEntry();
            }
        }

        if (!changed) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }

        if (!backup.isFile()) {
            copyFile(shaderpack, backup);
        }
        if (!shaderpack.delete() || !temp.renameTo(shaderpack)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Unable to replace patched shaderpack: " + shaderpack.getAbsolutePath());
        }
        Log.i(TAG, "Patched legacy shaderpack for Android: " + shaderpack.getName());
        return true;
    }

    private static boolean isShaderSource(String entryName) {
        String lowerName = entryName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".fsh")
                || lowerName.endsWith(".vsh")
                || lowerName.endsWith(".glsl");
    }

    private static String patchShaderSource(String source) {
        String result = source;
        for (String function : LOD_FUNCTIONS) {
            result = replaceZeroLodCalls(result, function);
        }
        return result;
    }

    private static String replaceZeroLodCalls(String source, String functionName) {
        StringBuilder output = new StringBuilder(source.length());
        int cursor = 0;
        while (cursor < source.length()) {
            int callIndex = source.indexOf(functionName, cursor);
            if (callIndex < 0) {
                output.append(source, cursor, source.length());
                break;
            }
            int openParen = callIndex + functionName.length();
            if (openParen >= source.length() || source.charAt(openParen) != '(') {
                output.append(source, cursor, callIndex + functionName.length());
                cursor = callIndex + functionName.length();
                continue;
            }
            int closeParen = findMatchingParen(source, openParen);
            if (closeParen < 0) {
                output.append(source, cursor, source.length());
                break;
            }
            String args = source.substring(openParen + 1, closeParen);
            String[] splitArgs = splitTopLevelArgs(args);
            if (splitArgs.length == 3 && isZeroLod(splitArgs[2])) {
                output.append(source, cursor, callIndex);
                output.append("texture2D(")
                        .append(splitArgs[0].trim())
                        .append(", ")
                        .append(splitArgs[1].trim())
                        .append(")");
                cursor = closeParen + 1;
            } else {
                output.append(source, cursor, closeParen + 1);
                cursor = closeParen + 1;
            }
        }
        return output.toString();
    }

    private static int findMatchingParen(String source, int openParen) {
        int depth = 0;
        for (int i = openParen; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String[] splitTopLevelArgs(String args) {
        String[] values = new String[3];
        int valueIndex = 0;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                if (valueIndex >= values.length) {
                    return new String[0];
                }
                values[valueIndex++] = args.substring(start, i);
                start = i + 1;
            }
        }
        if (valueIndex >= values.length) {
            return new String[0];
        }
        values[valueIndex++] = args.substring(start);
        if (valueIndex != values.length) {
            return new String[0];
        }
        return values;
    }

    private static boolean isZeroLod(String value) {
        String normalized = value.trim();
        return "0".equals(normalized)
                || "0.0".equals(normalized)
                || "0.".equals(normalized)
                || ".0".equals(normalized);
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            IOUtils.copy(input, output);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
