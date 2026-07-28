package net.kdt.pojavlaunch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArchitectureTest {
    @Test
    public void detectsArm64ProcessDirectory() {
        assertEquals(Architecture.ARCH_ARM64,
                Architecture.architectureFromNativeLibraryDir(
                        "/data/app/example/lib/arm64"));
        assertEquals(Architecture.ARCH_ARM64,
                Architecture.architectureFromNativeLibraryDir(
                        "C:\\package\\lib\\arm64-v8a"));
        assertEquals(Architecture.ARCH_ARM64,
                Architecture.architectureFromNativeLibraryDir(
                        "/data/app/example/lib/arm64/"));
    }

    @Test
    public void detectsX86ProcessDirectory() {
        assertEquals(Architecture.ARCH_X86_64,
                Architecture.architectureFromNativeLibraryDir(
                        "/data/app/example/lib/x86_64"));
        assertEquals(Architecture.ARCH_X86,
                Architecture.architectureFromNativeLibraryDir(
                        "/data/app/example/lib/x86"));
    }

    @Test
    public void ignoresArchitectureWordsOutsideFinalDirectory() {
        assertEquals(Architecture.UNSUPPORTED_ARCH,
                Architecture.architectureFromNativeLibraryDir(
                        "/data/app/x86-emulator-translated/base.apk/lib"));
        assertEquals(Architecture.UNSUPPORTED_ARCH,
                Architecture.architectureFromNativeLibraryDir(null));
    }
}
