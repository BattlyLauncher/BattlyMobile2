package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MinecraftGraphicsBackendPolicyTest {
    @Test
    public void minecraft262UsesOpenGlForMobileGlues() {
        MinecraftCompatibilityEngine.VersionFamily family =
                MinecraftCompatibilityEngine.VersionFamily.parse("fabric-loader-0.19.3-26.2", null);

        assertTrue(MinecraftGraphicsBackendPolicy.requiresExplicitOpenGl(
                family, "opengles_mobileglues"));
    }

    @Test
    public void zinkStillPresentsOpenGlToMinecraft() {
        MinecraftCompatibilityEngine.VersionFamily family =
                MinecraftCompatibilityEngine.VersionFamily.parse("26.3-snapshot-9", null);

        assertTrue(MinecraftGraphicsBackendPolicy.requiresExplicitOpenGl(
                family, "opengles3_desktopgl_zink_kopper"));
    }

    @Test
    public void olderVersionsKeepTheirExistingOption() {
        MinecraftCompatibilityEngine.VersionFamily family =
                MinecraftCompatibilityEngine.VersionFamily.parse("1.21.4", null);

        assertFalse(MinecraftGraphicsBackendPolicy.requiresExplicitOpenGl(
                family, "opengles_mobileglues"));
    }

    @Test
    public void backendOptionUsesMinecraftJsonStringSyntax() {
        assertEquals("\"opengl\"", MinecraftGraphicsBackendPolicy.openGlOptionValue());
    }
}
