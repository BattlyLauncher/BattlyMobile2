package net.kdt.patch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

final class LabyModShaderPatcher {
    private static final String SHADER_ENTRY = "assets/labymod/shaders/core/gui.fsh";

    private LabyModShaderPatcher() {
    }

    static void patchShaderJar() {
        File shaderJar = findShaderJar();
        if (shaderJar == null || !shaderJar.isFile()) {
            return;
        }

        File patchedMarker = new File(shaderJar.getParentFile(), shaderJar.getName() + ".android-gui-shader-fix2");
        if (patchedMarker.isFile() && patchedMarker.lastModified() >= shaderJar.lastModified()) {
            return;
        }

        File tempJar = new File(shaderJar.getParentFile(), shaderJar.getName() + ".tmp");
        boolean changed = false;
        try (JarFile source = new JarFile(shaderJar);
             JarOutputStream target = new JarOutputStream(Files.newOutputStream(tempJar.toPath()))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                JarEntry newEntry = new JarEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                target.putNextEntry(newEntry);
                byte[] data = readEntry(source, entry);
                if (SHADER_ENTRY.equals(entry.getName())) {
                    String original = new String(data, StandardCharsets.UTF_8);
                    String patched = patchGuiShader(original);
                    if (!original.equals(patched)) {
                        data = patched.getBytes(StandardCharsets.UTF_8);
                        changed = true;
                    }
                }
                target.write(data);
                target.closeEntry();
            }
        } catch (Throwable throwable) {
            System.err.println("[LwjglPatchAgent] Failed to patch LabyMod shader jar: " + throwable);
            tempJar.delete();
            return;
        }

        if (!changed) {
            tempJar.delete();
            try {
                patchedMarker.createNewFile();
            } catch (IOException ignored) {
            }
            return;
        }

        try {
            Files.move(tempJar.toPath(), shaderJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            patchedMarker.createNewFile();
            patchedMarker.setLastModified(shaderJar.lastModified());
            System.out.println("[LwjglPatchAgent] Patched LabyMod GUI shader for OpenGL ES.");
        } catch (IOException exception) {
            System.err.println("[LwjglPatchAgent] Failed to replace LabyMod shader jar: " + exception);
            tempJar.delete();
        }
    }

    private static File findShaderJar() {
        String minecraftPath = System.getProperty("pojav.path.minecraft");
        if (minecraftPath == null || minecraftPath.trim().isEmpty()) {
            String home = System.getProperty("user.home");
            if (home == null || home.trim().isEmpty()) {
                return null;
            }
            minecraftPath = new File(home, ".minecraft").getAbsolutePath();
        }
        return new File(minecraftPath, "labymod-neo/assets/shader.jar");
    }

    private static String patchGuiShader(String shader) {
        return shader
                .replace("#l3d_import <labymod:shaders/include/projection.glsl>\n", "")
                .replace("#l3d_import <labymod:shaders/include/globals.glsl>\n", "")
                .replace("#l3d_import <labymod:shaders/include/dynamic_transforms.glsl>\n", "")
                .replace("2.0f", "2.0")
                .replace("texCoord.x != 0 || texCoord.y != 0", "texCoord.x != 0.0 || texCoord.y != 0.0")
                .replace("RectangleDimensions / 2,", "RectangleDimensions / 2.0,");
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
}
