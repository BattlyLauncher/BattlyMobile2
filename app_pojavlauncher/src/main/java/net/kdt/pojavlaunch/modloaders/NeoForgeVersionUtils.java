package net.kdt.pojavlaunch.modloaders;

public final class NeoForgeVersionUtils {
    private NeoForgeVersionUtils() {}

    public static String toMinecraftVersion(String neoForgeVersion) {
        if (neoForgeVersion == null) return null;

        String[] parts = neoForgeVersion.split("\\.");
        if (parts.length < 2) return null;

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major >= 25
                    ? major + "." + minor
                    : "1." + major + "." + minor;
        } catch (NumberFormatException ignored) {
            // Keep non-standard versions visible instead of inventing a 1.x mapping.
            return parts[0] + "." + parts[1];
        }
    }
}
