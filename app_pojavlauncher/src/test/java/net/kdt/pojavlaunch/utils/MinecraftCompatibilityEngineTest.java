package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.value.DependentLibrary;

public class MinecraftCompatibilityEngineTest {
    @Test
    public void modernMinecraftRejectsLegacyAutomaticRenderer() {
        MinecraftCompatibilityEngine.VersionFamily modern =
                MinecraftCompatibilityEngine.VersionFamily.parse("1.21.11", null);
        MinecraftCompatibilityEngine.VersionFamily yearVersion =
                MinecraftCompatibilityEngine.VersionFamily.parse("26.2", null);

        assertTrue(modern.requiresModernRenderer);
        assertTrue(yearVersion.requiresModernRenderer);
    }

    @Test
    public void legacyMinecraftCanStillUseGl4es() {
        MinecraftCompatibilityEngine.VersionFamily legacy =
                MinecraftCompatibilityEngine.VersionFamily.parse("1.16.5-forge-36.2.42", "1.16.5");
        MinecraftCompatibilityEngine.VersionFamily supportedModern =
                MinecraftCompatibilityEngine.VersionFamily.parse("1.21.4", null);

        assertFalse(legacy.requiresModernRenderer);
        assertFalse(supportedModern.requiresModernRenderer);
    }

    @Test
    public void fabricProfilesUseMinecraftVersionInsteadOfLoaderVersion() {
        MinecraftCompatibilityEngine.VersionFamily snapshot =
                MinecraftCompatibilityEngine.VersionFamily.parse(
                        "fabric-loader-0.19.3-26.2", null);
        MinecraftCompatibilityEngine.VersionFamily release =
                MinecraftCompatibilityEngine.VersionFamily.parse(
                        "fabric-loader-0.18.4-1.21.1", null);

        assertEquals(26, snapshot.major);
        assertEquals(2, snapshot.minor);
        assertTrue(snapshot.requiresModernRenderer);
        assertEquals(1, release.major);
        assertEquals(21, release.minor);
        assertTrue(release.modern);
    }

    @Test
    public void forgeProfilesIgnoreForgeBuildNumber() {
        MinecraftCompatibilityEngine.VersionFamily forge =
                MinecraftCompatibilityEngine.VersionFamily.parse(
                        "1.20.1-forge-47.4.10", null);

        assertEquals(1, forge.major);
        assertEquals(20, forge.minor);
        assertEquals(1, forge.patch);
    }

    @Test
    public void yearVersionsKeepMatchingLwjglJavaAndNativeBundles() {
        JMinecraftVersionList.Version metadata = new JMinecraftVersionList.Version();
        DependentLibrary lwjgl = new DependentLibrary();
        lwjgl.name = "org.lwjgl:lwjgl:3.4.1";
        metadata.libraries = new DependentLibrary[] { lwjgl };

        MinecraftCompatibilityEngine.VersionFamily family =
                MinecraftCompatibilityEngine.VersionFamily.parse("26.1.2", null);

        assertEquals("3.4.1",
                MinecraftCompatibilityEngine.inferLwjglForTesting(metadata, family));
    }

    @Test
    public void snapshotSixAndSevenSelectTheDeclaredLwjgl342Bundle() {
        JMinecraftVersionList.Version metadata = new JMinecraftVersionList.Version();
        DependentLibrary lwjgl = new DependentLibrary();
        lwjgl.name = "org.lwjgl:lwjgl:3.4.2";
        metadata.libraries = new DependentLibrary[] { lwjgl };

        assertEquals("3.4.2", MinecraftCompatibilityEngine.resolveLwjglChannel(
                "26.3-snapshot-6", metadata));
        assertEquals("3.4.2", MinecraftCompatibilityEngine.resolveLwjglChannel(
                "26.3-snapshot-7", metadata));
    }

    @Test
    public void inputBridgeKeepsMinecraft262OnGlfwAndNewSnapshotsOnSdl() {
        assertFalse(MinecraftCompatibilityEngine.requiresSdlInputBridge("3.4.1"));
        assertTrue(MinecraftCompatibilityEngine.requiresSdlInputBridge("3.4.2"));
        assertTrue(MinecraftCompatibilityEngine.requiresSdlInputBridge("3.4.3"));
    }
}
