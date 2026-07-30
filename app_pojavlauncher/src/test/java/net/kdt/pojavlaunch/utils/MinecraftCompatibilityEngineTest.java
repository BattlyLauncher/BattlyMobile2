package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
