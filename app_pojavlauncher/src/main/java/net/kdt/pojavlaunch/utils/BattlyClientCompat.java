package net.kdt.pojavlaunch.utils;

import android.util.Log;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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

public final class BattlyClientCompat {
    private static final String TAG = "BattlyClientCompat";
    private static final String MINECRAFT_CLIENT_MIXIN_ENTRY = "io/github/solclient/client/mod/impl/core/mixins/client/MinecraftClientMixin.class";
    private static final String SCREEN_ANIMATION_ENTRY = "io/github/solclient/client/ui/ScreenAnimation.class";
    private static final String PANORAMA_BACKGROUND_SCREEN_ENTRY = "io/github/solclient/client/ui/screen/PanoramaBackgroundScreen.class";
    private static final String MENU_BLUR_MOD_ENTRY = "io/github/solclient/client/mod/impl/MenuBlurMod.class";
    private static final String GAME_RENDERER_MIXIN_ENTRY = "io/github/solclient/client/mod/impl/core/mixins/client/GameRendererMixin.class";
    private static final String MINECRAFT_UTILS_ENTRY = "io/github/solclient/client/util/MinecraftUtils.class";
    private static final String TESSELLATOR_ENTRY = "bfe.class";
    private static final String INTERMEDIARY_TESSELLATOR_ENTRY = "net/minecraft/class_2395.class";
    private static final String FRAMEBUFFER_ENTRY = "bfw.class";
    private static final String INTERMEDIARY_FRAMEBUFFER_ENTRY = "net/minecraft/class_1862.class";
    private static final String CORE_MOD_OWNER = "io/github/solclient/client/mod/impl/core/CoreMod";

    private BattlyClientCompat() {
    }

    public static void patchForAndroid(String versionId) {
        if (versionId == null || !versionId.toLowerCase().contains("battly client")) return;

        normalizeAssetIndex(versionId);
        patchGameJar(versionId);

        Set<String> patched = new HashSet<>();
        File wrapperRoot = new File(Tools.DIR_GAME_HOME, ".minecraft/libraries/io/github/solclient/wrapper");
        patchWrapperRoot(wrapperRoot, patched);

        File solClientLaunch = new File(Tools.DIR_GAME_HOME, ".minecraft/.sol-client-launch");
        File[] solClientJars = solClientLaunch.listFiles((dir, name) -> name.endsWith(".jar"));
        if (solClientJars != null) {
            for (File jar : solClientJars) {
                patchWrapperJar(jar, patched);
            }
        }
    }

    private static void normalizeAssetIndex(String versionId) {
        File versionJson = new File(Tools.DIR_GAME_HOME, ".minecraft/versions/" + versionId + "/" + versionId + ".json");
        if (!versionJson.isFile()) return;
        try {
            String content = new String(readFile(versionJson), StandardCharsets.UTF_8);
            String normalized = content
                    .replace("\"assets\":\"1.8.9\"", "\"assets\":\"1.8\"")
                    .replace("\"assets\": \"1.8.9\"", "\"assets\": \"1.8\"");
            if (!content.equals(normalized)) {
                writeFile(versionJson, normalized.getBytes(StandardCharsets.UTF_8));
                Logger.appendToLog("Info: Battly Client version metadata asset index normalized on disk");
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to normalize Battly Client asset index", throwable);
        }
    }

    private static void patchWrapperRoot(File root, Set<String> patched) {
        File[] versions = root.listFiles(File::isDirectory);
        if (versions == null) return;
        for (File versionDir : versions) {
            File[] jars = versionDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jars == null) continue;
            for (File jar : jars) {
                patchWrapperJar(jar, patched);
            }
        }
    }

    private static void patchWrapperJar(File jarFile, Set<String> patched) {
        if (!jarFile.isFile()) return;
        String path = jarFile.getAbsolutePath();
        if (!patched.add(path)) return;

        File marker = new File(jarFile.getParentFile(), jarFile.getName() + ".battly-android-real-ui-fix11");
        if (marker.isFile() && marker.lastModified() >= jarFile.lastModified()) return;

        File tempJar = new File(jarFile.getParentFile(), jarFile.getName() + ".battly-tmp");
        boolean changed = false;
        try (JarFile source = new JarFile(jarFile);
             JarOutputStream target = new JarOutputStream(new FileOutputStream(tempJar))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                JarEntry newEntry = new JarEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                target.putNextEntry(newEntry);

                byte[] data = readEntry(source, entry);
                if (MINECRAFT_CLIENT_MIXIN_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchMinecraftClientMixin(data);
                    changed |= patchedData != data;
                    data = patchedData;
                } else if (SCREEN_ANIMATION_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchScreenAnimation(data);
                    changed |= patchedData != data;
                    data = patchedData;
                } else if (PANORAMA_BACKGROUND_SCREEN_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchPanoramaBackgroundScreen(data);
                    changed |= patchedData != data;
                    data = patchedData;
                } else if (MENU_BLUR_MOD_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchMenuBlurMod(data);
                    changed |= patchedData != data;
                    data = patchedData;
                } else if (GAME_RENDERER_MIXIN_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchGameRendererMixin(data);
                    changed |= patchedData != data;
                    data = patchedData;
                } else if (MINECRAFT_UTILS_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchMinecraftUtils(data);
                    changed |= patchedData != data;
                    data = patchedData;
                } else if (TESSELLATOR_ENTRY.equals(entry.getName()) || INTERMEDIARY_TESSELLATOR_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchTessellatorDraw(data);
                    changed |= patchedData != data;
                    data = patchedData;
                } else if (FRAMEBUFFER_ENTRY.equals(entry.getName()) || INTERMEDIARY_FRAMEBUFFER_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchFramebufferDraw(data);
                    changed |= patchedData != data;
                    data = patchedData;
                }

                target.write(data);
                target.closeEntry();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to patch Battly Client wrapper " + jarFile.getAbsolutePath(), throwable);
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
            Logger.appendToLog("Info: Applied Battly Client Android compatibility patch to " + jarFile.getName());
        } catch (IOException exception) {
            Log.w(TAG, "Unable to replace patched Battly Client wrapper " + jarFile.getAbsolutePath(), exception);
            tempJar.delete();
        }
    }

    private static void patchGameJar(String versionId) {
        File gameJar = new File(Tools.DIR_GAME_HOME, ".minecraft/versions/" + versionId + "/" + versionId + ".jar");
        if (!gameJar.isFile()) return;

        File marker = new File(gameJar.getParentFile(), gameJar.getName() + ".battly-android-real-ui-fix11");
        if (marker.isFile() && marker.lastModified() >= gameJar.lastModified()) return;

        File tempJar = new File(gameJar.getParentFile(), gameJar.getName() + ".battly-tmp");
        boolean changed = false;
        try (JarFile source = new JarFile(gameJar);
             JarOutputStream target = new JarOutputStream(new FileOutputStream(tempJar))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                JarEntry newEntry = new JarEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                target.putNextEntry(newEntry);

                byte[] data = readEntry(source, entry);
                if (TESSELLATOR_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchTessellatorDraw(data);
                    changed |= patchedData != data;
                    data = patchedData;
                } else if (FRAMEBUFFER_ENTRY.equals(entry.getName())) {
                    byte[] patchedData = patchFramebufferDraw(data);
                    changed |= patchedData != data;
                    data = patchedData;
                }

                target.write(data);
                target.closeEntry();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to patch Battly Client game jar " + gameJar.getAbsolutePath(), throwable);
            tempJar.delete();
            return;
        }

        if (!changed) {
            tempJar.delete();
            touch(marker, gameJar.lastModified());
            return;
        }

        try {
            replaceFile(tempJar, gameJar);
            touch(marker, gameJar.lastModified());
            Logger.appendToLog("Info: Applied Battly Client Minecraft render compatibility patch");
        } catch (IOException exception) {
            Log.w(TAG, "Unable to replace patched Battly Client game jar " + gameJar.getAbsolutePath(), exception);
            tempJar.delete();
        }
    }

    private static byte[] patchMinecraftClientMixin(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"setScreen".equals(name)
                        || !"(Lnet/minecraft/class_388;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V".equals(descriptor)) {
                    return delegate;
                }
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String descriptor) {
                        if (opcode == Opcodes.GETFIELD
                                && CORE_MOD_OWNER.equals(owner)
                                && "fancyMainMenu".equals(fieldName)
                                && "Z".equals(descriptor)) {
                            super.visitInsn(Opcodes.POP);
                            super.visitInsn(Opcodes.ICONST_0);
                            changed[0] = true;
                            return;
                        }
                        super.visitFieldInsn(opcode, owner, fieldName, descriptor);
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : classData;
    }

    private static byte[] patchScreenAnimation(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("isActive".equals(name) && "()Z".equals(descriptor)) {
                    changed[0] = true;
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                MethodVisitor method = super.visitMethod(Opcodes.ACC_PRIVATE, "isActive", "()Z", null, null);
                method.visitCode();
                method.visitInsn(Opcodes.ICONST_0);
                method.visitInsn(Opcodes.IRETURN);
                method.visitMaxs(1, 1);
                method.visitEnd();
                super.visitEnd();
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : classData;
    }

    private static byte[] patchPanoramaBackgroundScreen(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("drawPanorama".equals(name) && "(IIF)V".equals(descriptor)) {
                    changed[0] = true;
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    method.visitCode();
                    method.visitInsn(Opcodes.RETURN);
                    method.visitMaxs(0, 4);
                    method.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : classData;
    }

    private static byte[] patchMenuBlurMod(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (("onPostProcessing".equals(name) && "(Lio/github/solclient/client/event/impl/PostProcessingEvent;)V".equals(descriptor))
                        || ("onRenderGuiBackground".equals(name) && "(Lio/github/solclient/client/event/impl/RenderGuiBackgroundEvent;)V".equals(descriptor))) {
                    changed[0] = true;
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    method.visitCode();
                    method.visitInsn(Opcodes.RETURN);
                    method.visitMaxs(0, 2);
                    method.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : classData;
    }

    private static byte[] patchGameRendererMixin(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (("addShaders".equals(name) && "(FJLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V".equals(descriptor))
                        || ("updateShaders".equals(name) && "(IILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V".equals(descriptor))) {
                    changed[0] = true;
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    method.visitCode();
                    method.visitInsn(Opcodes.RETURN);
                    method.visitMaxs(0, 4);
                    method.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : classData;
    }

    private static byte[] patchMinecraftUtils(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("withNvg".equals(name) && "(Ljava/lang/Runnable;Z)V".equals(descriptor)) {
                    changed[0] = true;
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    method.visitCode();

                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "io/github/solclient/client/util/NanoVGManager", "getNvg", "()J", false);
                    method.visitVarInsn(Opcodes.LSTORE, 2);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/class_1600", "method_2965", "()Lnet/minecraft/class_1600;", false);
                    method.visitVarInsn(Opcodes.ASTORE, 4);
                    method.visitTypeInsn(Opcodes.NEW, "net/minecraft/class_389");
                    method.visitInsn(Opcodes.DUP);
                    method.visitVarInsn(Opcodes.ALOAD, 4);
                    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/class_389", "<init>", "(Lnet/minecraft/class_1600;)V", false);
                    method.visitVarInsn(Opcodes.ASTORE, 5);

                    method.visitVarInsn(Opcodes.LLOAD, 2);
                    method.visitVarInsn(Opcodes.ALOAD, 4);
                    method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/class_1600", "field_3801", "I");
                    method.visitInsn(Opcodes.I2F);
                    method.visitVarInsn(Opcodes.ALOAD, 4);
                    method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/class_1600", "field_3802", "I");
                    method.visitInsn(Opcodes.I2F);
                    method.visitInsn(Opcodes.FCONST_1);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/nanovg/NanoVG", "nvgBeginFrame", "(JFFF)V", false);

                    method.visitVarInsn(Opcodes.LLOAD, 2);
                    method.visitVarInsn(Opcodes.ALOAD, 5);
                    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_389", "method_1049", "()I", false);
                    method.visitInsn(Opcodes.I2F);
                    method.visitVarInsn(Opcodes.ALOAD, 5);
                    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_389", "method_1049", "()I", false);
                    method.visitInsn(Opcodes.I2F);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/nanovg/NanoVG", "nvgScale", "(JFF)V", false);

                    method.visitVarInsn(Opcodes.ALOAD, 0);
                    method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true);
                    method.visitLdcInsn(3008);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDisable", "(I)V", false);
                    method.visitVarInsn(Opcodes.LLOAD, 2);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/nanovg/NanoVG", "nvgEndFrame", "(J)V", false);

                    resetGlState(method);
                    method.visitInsn(Opcodes.RETURN);
                    method.visitMaxs(0, 0);
                    method.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : classData;
    }

    private static byte[] patchTessellatorDraw(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] changed = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean obfuscatedDraw = "a".equals(name) && "(Lbfd;)V".equals(descriptor);
                boolean intermediaryDraw = "method_9763".equals(name) && "(Lnet/minecraft/class_520;)V".equals(descriptor);
                if (!obfuscatedDraw && !intermediaryDraw) {
                    return delegate;
                }
                changed[0] = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        methodResetVertexArrays(this);
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : classData;
    }

    private static byte[] patchFramebufferDraw(byte[] classData) {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] changed = {false};
        final String owner = reader.getClassName();
        final boolean intermediary = INTERMEDIARY_FRAMEBUFFER_ENTRY.equals(owner + ".class");
        final String texWidthField = intermediary ? "field_7981" : "a";
        final String texHeightField = intermediary ? "field_7982" : "b";
        final String widthField = intermediary ? "field_7983" : "c";
        final String heightField = intermediary ? "field_7984" : "d";
        final String textureField = intermediary ? "field_7987" : "g";
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                boolean obfuscatedDraw = "a".equals(name) && "(IIZ)V".equals(descriptor);
                boolean intermediaryDraw = "method_9925".equals(name) && "(IIZ)V".equals(descriptor);
                if (!obfuscatedDraw && !intermediaryDraw) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }

                changed[0] = true;
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                method.visitCode();
                emitImmediateFramebufferDraw(method, owner, texWidthField, texHeightField, widthField, heightField, textureField);
                method.visitInsn(Opcodes.RETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
                return null;
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : classData;
    }

    private static void emitImmediateFramebufferDraw(MethodVisitor method, String owner, String texWidthField, String texHeightField,
                                                     String widthField, String heightField, String textureField) {
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glColorMask", "(ZZZZ)V", false);
        method.visitLdcInsn(2929);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDisable", "(I)V", false);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDepthMask", "(Z)V", false);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glViewport", "(IIII)V", false);

        method.visitLdcInsn(5889);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glMatrixMode", "(I)V", false);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glLoadIdentity", "()V", false);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(Opcodes.I2D);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitInsn(Opcodes.I2D);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitLdcInsn(1000.0d);
        method.visitLdcInsn(3000.0d);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glOrtho", "(DDDDDD)V", false);
        method.visitLdcInsn(5888);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glMatrixMode", "(I)V", false);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glLoadIdentity", "()V", false);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitLdcInsn(-2000.0f);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glTranslatef", "(FFF)V", false);

        method.visitLdcInsn(3553);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glEnable", "(I)V", false);
        method.visitLdcInsn(3008);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDisable", "(I)V", false);
        method.visitLdcInsn(3042);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDisable", "(I)V", false);
        method.visitVarInsn(Opcodes.ILOAD, 3);
        org.objectweb.asm.Label noBlend = new org.objectweb.asm.Label();
        method.visitJumpInsn(Opcodes.IFEQ, noBlend);
        method.visitLdcInsn(3042);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glEnable", "(I)V", false);
        method.visitLdcInsn(770);
        method.visitLdcInsn(771);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glBlendFunc", "(II)V", false);
        method.visitLabel(noBlend);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glColor4f", "(FFFF)V", false);
        method.visitLdcInsn(3553);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, owner, textureField, "I");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glBindTexture", "(II)V", false);

        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(Opcodes.I2F);
        method.visitVarInsn(Opcodes.FSTORE, 4);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitInsn(Opcodes.I2F);
        method.visitVarInsn(Opcodes.FSTORE, 5);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, owner, widthField, "I");
        method.visitInsn(Opcodes.I2F);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, owner, texWidthField, "I");
        method.visitInsn(Opcodes.I2F);
        method.visitInsn(Opcodes.FDIV);
        method.visitVarInsn(Opcodes.FSTORE, 6);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, owner, heightField, "I");
        method.visitInsn(Opcodes.I2F);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, owner, texHeightField, "I");
        method.visitInsn(Opcodes.I2F);
        method.visitInsn(Opcodes.FDIV);
        method.visitVarInsn(Opcodes.FSTORE, 7);

        method.visitLdcInsn(7);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glBegin", "(I)V", false);
        emitTexVertex(method, 0.0f, 0.0f, 0.0f, 5, 0.0f);
        emitTexVertex(method, 6, 0.0f, 4, 5, 0.0f);
        emitTexVertex(method, 6, 7, 4, 0.0f, 0.0f);
        emitTexVertex(method, 0.0f, 7, 0.0f, 0.0f, 0.0f);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glEnd", "()V", false);

        method.visitLdcInsn(3553);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glBindTexture", "(II)V", false);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDepthMask", "(Z)V", false);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glColorMask", "(ZZZZ)V", false);
    }

    private static void emitTexVertex(MethodVisitor method, float u, float v, float x, int yVar, float z) {
        method.visitLdcInsn(u);
        method.visitLdcInsn(v);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glTexCoord2f", "(FF)V", false);
        method.visitLdcInsn(x);
        method.visitVarInsn(Opcodes.FLOAD, yVar);
        method.visitLdcInsn(z);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glVertex3f", "(FFF)V", false);
    }

    private static void emitTexVertex(MethodVisitor method, int uVar, float v, int xVar, int yVar, float z) {
        method.visitVarInsn(Opcodes.FLOAD, uVar);
        method.visitLdcInsn(v);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glTexCoord2f", "(FF)V", false);
        method.visitVarInsn(Opcodes.FLOAD, xVar);
        method.visitVarInsn(Opcodes.FLOAD, yVar);
        method.visitLdcInsn(z);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glVertex3f", "(FFF)V", false);
    }

    private static void emitTexVertex(MethodVisitor method, int uVar, int vVar, int xVar, float y, float z) {
        method.visitVarInsn(Opcodes.FLOAD, uVar);
        method.visitVarInsn(Opcodes.FLOAD, vVar);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glTexCoord2f", "(FF)V", false);
        method.visitVarInsn(Opcodes.FLOAD, xVar);
        method.visitLdcInsn(y);
        method.visitLdcInsn(z);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glVertex3f", "(FFF)V", false);
    }

    private static void emitTexVertex(MethodVisitor method, float u, int vVar, float x, float y, float z) {
        method.visitLdcInsn(u);
        method.visitVarInsn(Opcodes.FLOAD, vVar);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glTexCoord2f", "(FF)V", false);
        method.visitLdcInsn(x);
        method.visitLdcInsn(y);
        method.visitLdcInsn(z);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glVertex3f", "(FFF)V", false);
    }

    private static void resetGlState(MethodVisitor method) {
        method.visitLdcInsn(3089);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDisable", "(I)V", false);
        method.visitLdcInsn(2960);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDisable", "(I)V", false);
        method.visitLdcInsn(3042);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glDisable", "(I)V", false);
        method.visitLdcInsn(3553);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glEnable", "(I)V", false);
        method.visitLdcInsn(34962);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL15", "glBindBuffer", "(II)V", false);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitInsn(Opcodes.FCONST_1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glColor4f", "(FFFF)V", false);
    }

    private static void methodResetVertexArrays(MethodVisitor method) {
        method.visitLdcInsn(34962);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL15", "glBindBuffer", "(II)V", false);
        for (int i = 0; i < 16; i++) {
            pushInt(method, i);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL20", "glDisableVertexAttribArray", "(I)V", false);
        }
    }

    private static void pushInt(MethodVisitor method, int value) {
        if (value >= -1 && value <= 5) {
            method.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value <= Byte.MAX_VALUE) {
            method.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value <= Short.MAX_VALUE) {
            method.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            method.visitLdcInsn(value);
        }
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

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output);
            return output.toByteArray();
        }
    }

    private static void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
    }

    private static void replaceFile(File source, File target) throws IOException {
        if (target.exists() && !target.delete()) {
            throw new IOException("Unable to replace " + target.getAbsolutePath());
        }
        if (source.renameTo(target)) return;
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            copy(input, output);
        }
        if (!source.delete()) source.deleteOnExit();
    }

    private static void copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    }
}
