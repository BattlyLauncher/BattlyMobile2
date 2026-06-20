package net.kdt.patch;

import net.kdt.patch.asm.ClassReader;
import net.kdt.patch.asm.ClassVisitor;
import net.kdt.patch.asm.ClassWriter;
import net.kdt.patch.asm.MethodVisitor;
import net.kdt.patch.asm.Opcodes;
import net.minecraft.launchwrapper.IClassTransformer;

public class LabyModScreenTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !containsAscii(basicClass, "labyMod$fireScreenOpenEvent")) {
            return basicClass;
        }

        try {
            byte[] patched = patchScreenOpenHook(basicClass);
            return patched == null ? basicClass : patched;
        } catch (Throwable throwable) {
            System.err.println("[LwjglPatchAgent] Failed to patch LabyMod screen hook in " + name + ": " + throwable);
            throwable.printStackTrace();
            return basicClass;
        }
    }

    private static byte[] patchScreenOpenHook(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        final boolean[] patched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("labyMod$fireScreenOpenEvent".equals(name) && "(Laxu;)Laxu;".equals(descriptor)) {
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(1, 2);
                    mv.visitEnd();
                    patched[0] = true;
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        if (patched[0]) {
            System.out.println("[LwjglPatchAgent] Patched LabyMod 1.8.9 screen hook after mixin application.");
        }
        return patched[0] ? writer.toByteArray() : null;
    }

    private static boolean containsAscii(byte[] bytes, String value) {
        byte[] needle = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
