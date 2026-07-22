package net.kdt.pojavlaunch.utils;

import android.util.Log;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class VanillaPostShaderCompat {
    private static final String TAG = "VanillaShaderCompat";
    private static final String ENTITY_OUTLINE_ENTRY = "assets/minecraft/shaders/post/entity_outline.json";
    private static final String ENTITY_SOBEL_ENTRY = "assets/minecraft/shaders/program/entity_sobel.fsh";
    private static final String OIT_TRANSMITTANCE_ENTRY = "assets/minecraft/shaders/include/oit_add_transmittance.glsl";
    private static final String OIT_DYNAMIC_OUTPUT_LOOP =
            "    for (int attachmentIndex = 0; attachmentIndex < COEFF_ATTACHMENT_COUNT; attachmentIndex++) {\n" +
            "        for (int i = 0; i < 4; i++) {\n" +
            "            coeff[attachmentIndex][i] = coefficients[attachmentIndex * 4 + i];\n" +
            "        }\n" +
            "    }";
    private static final String OIT_CONSTANT_OUTPUT_ASSIGNMENTS =
            "    // Battly Mobile: ANGLE requires constant indexes for fragment outputs.\n" +
            "    coeff[0] = vec4(coefficients[0], coefficients[1], coefficients[2], coefficients[3]);\n" +
            "    #if COEFF_ATTACHMENT_COUNT > 1\n" +
            "    coeff[1] = vec4(coefficients[4], coefficients[5], coefficients[6], coefficients[7]);\n" +
            "    #endif\n" +
            "    #if COEFF_ATTACHMENT_COUNT > 2\n" +
            "    coeff[2] = vec4(coefficients[8], coefficients[9], coefficients[10], coefficients[11]);\n" +
            "    #endif";
    private static final byte[] DISABLED_ENTITY_OUTLINE = (
            "{\n" +
            "  \"battly_android_disabled\": true,\n" +
            "  \"targets\": [],\n" +
            "  \"passes\": []\n" +
            "}\n"
    ).getBytes(StandardCharsets.UTF_8);
    private static final byte[] DISABLED_ENTITY_SOBEL = (
            "#version 120\n\n" +
            "// Disabled by Battly Mobile on Android GL4ES.\n" +
            "// The original optional entity outline shader is translated incorrectly\n" +
            "// on some GLES 3 drivers and can crash the JVM native process.\n" +
            "void main(){\n" +
            "    gl_FragColor = vec4(0.0);\n" +
            "}\n"
    ).getBytes(StandardCharsets.UTF_8);

    private VanillaPostShaderCompat() {
    }

    public static void patchForAndroid(MinecraftProfile profile, String versionId) {
        if (versionId == null || versionId.trim().isEmpty()) return;
        if (Tools.LOCAL_RENDERER == null || !Tools.LOCAL_RENDERER.startsWith("opengles")) return;

        Set<String> patched = new HashSet<>();
        patchCandidate(new File(Tools.DIR_GAME_HOME, ".minecraft/versions/" + versionId + "/" + versionId + ".jar"), patched);
        if (profile != null && profile.lastVersionId != null) {
            patchCandidate(new File(Tools.DIR_GAME_HOME, ".minecraft/versions/" + profile.lastVersionId + "/" + profile.lastVersionId + ".jar"), patched);
        }

        File solClientLaunch = new File(Tools.DIR_GAME_HOME, ".minecraft/.sol-client-launch");
        File[] solClientJars = solClientLaunch.listFiles((dir, name) -> name.endsWith(".jar"));
        if (solClientJars != null) {
            for (File jar : solClientJars) {
                patchCandidate(jar, patched);
            }
        }
    }

    private static void patchCandidate(File jarFile, Set<String> patched) {
        if (!jarFile.isFile()) return;
        String path = jarFile.getAbsolutePath();
        if (!patched.add(path)) return;

        File marker = new File(jarFile.getParentFile(), jarFile.getName() + ".battly-android-postshader-fix4");
        if (marker.isFile() && marker.lastModified() >= jarFile.lastModified()) return;

        File tempJar = new File(jarFile.getParentFile(), jarFile.getName() + ".battly-tmp");
        boolean changed = false;
        try (JarFile source = new JarFile(jarFile);
             JarOutputStream target = new JarOutputStream(new FileOutputStream(tempJar))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (isSignatureEntry(entry.getName())) {
                    changed = true;
                    continue;
                }
                JarEntry newEntry = new JarEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                target.putNextEntry(newEntry);

                byte[] data = readEntry(source, entry);
                if (ENTITY_OUTLINE_ENTRY.equals(entry.getName()) && !isDisabledPostShader(data)) {
                    data = DISABLED_ENTITY_OUTLINE;
                    changed = true;
                } else if (ENTITY_SOBEL_ENTRY.equals(entry.getName()) && !isAlreadyPatched(data)) {
                    data = DISABLED_ENTITY_SOBEL;
                    changed = true;
                } else if (OIT_TRANSMITTANCE_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchOitTransmittance(data);
                    if (patchedData != data) {
                        data = patchedData;
                        changed = true;
                    }
                }

                target.write(data);
                target.closeEntry();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to patch vanilla post shader in " + jarFile.getAbsolutePath(), throwable);
            tempJar.delete();
            return;
        }

        if (!changed) {
            tempJar.delete();
            touch(marker, jarFile.lastModified());
            return;
        }

        try {
            replaceFile(tempJar, jarFile);
            touch(marker, jarFile.lastModified());
            Logger.appendToLog("Info: Applied Android shader compatibility fixes to " + jarFile.getName());
        } catch (IOException exception) {
            Log.w(TAG, "Unable to replace patched jar " + jarFile.getAbsolutePath(), exception);
            tempJar.delete();
        }
    }

    private static boolean isSignatureEntry(String name) {
        if (name == null) return false;
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        if (!upper.startsWith("META-INF/")) return false;
        String fileName = upper.substring("META-INF/".length());
        return fileName.endsWith(".SF")
                || fileName.endsWith(".RSA")
                || fileName.endsWith(".DSA")
                || fileName.endsWith(".EC")
                || fileName.startsWith("SIG-");
    }

    private static boolean isAlreadyPatched(byte[] data) {
        String shader = new String(data, StandardCharsets.UTF_8);
        return shader.contains("Disabled by Battly Mobile on Android GL4ES");
    }

    private static boolean isDisabledPostShader(byte[] data) {
        String shader = new String(data, StandardCharsets.UTF_8);
        return shader.contains("\"battly_android_disabled\"");
    }

    private static byte[] patchOitTransmittance(byte[] data) {
        String shader = new String(data, StandardCharsets.UTF_8);
        if (shader.contains("ANGLE requires constant indexes for fragment outputs")) return data;

        String normalized = shader.replace("\r\n", "\n");
        if (!normalized.contains(OIT_DYNAMIC_OUTPUT_LOOP)) return data;
        return normalized.replace(OIT_DYNAMIC_OUTPUT_LOOP, OIT_CONSTANT_OUTPUT_ASSIGNMENTS)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void touch(File file, long timestamp) {
        try {
            if (!file.exists()) file.createNewFile();
            file.setLastModified(timestamp);
        } catch (IOException ignored) {
        }
    }

    private static byte[] readEntry(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(entry);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static void replaceFile(File source, File target) throws IOException {
        if (target.exists() && !target.delete()) {
            throw new IOException("Unable to replace " + target.getAbsolutePath());
        }
        if (source.renameTo(target)) return;
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        if (!source.delete()) source.deleteOnExit();
    }
}
