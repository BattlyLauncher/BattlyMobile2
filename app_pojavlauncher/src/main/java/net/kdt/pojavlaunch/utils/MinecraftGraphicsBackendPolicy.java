package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Logger;

/** Keeps Mojang's graphics backend aligned with Battly's Android renderer. */
public final class MinecraftGraphicsBackendPolicy {
    static final String OPENGL = "\"opengl\"";

    private MinecraftGraphicsBackendPolicy() {
    }

    public static boolean requiresExplicitOpenGl(
            @NonNull MinecraftCompatibilityEngine.VersionFamily family,
            @Nullable String renderer) {
        if (family.yearVersion < 26 || renderer == null) {
            return false;
        }
        // Every renderer currently exposed by Battly presents an OpenGL context.
        // Zink and Freedreno use Vulkan internally, but Minecraft must still select
        // its OpenGL backend or it bypasses the translation layer entirely.
        return !"minecraft_vulkan".equals(renderer);
    }

    public static void apply(@NonNull MinecraftCompatibilityEngine.VersionFamily family,
                             @Nullable String renderer) {
        if (!requiresExplicitOpenGl(family, renderer)) {
            return;
        }
        String previous = MCOptionUtils.get("preferredGraphicsBackend");
        if (OPENGL.equalsIgnoreCase(previous)) {
            return;
        }
        MCOptionUtils.set("preferredGraphicsBackend", OPENGL);
        MCOptionUtils.save();
        Logger.appendToLog("Info: Minecraft graphics backend forced to OpenGL for renderer "
                + renderer + " (previous=" + previous + ")");
    }

    static String openGlOptionValue() {
        return OPENGL;
    }
}
