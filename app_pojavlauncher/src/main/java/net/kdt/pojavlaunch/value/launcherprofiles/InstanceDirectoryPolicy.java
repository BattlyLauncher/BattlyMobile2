package net.kdt.pojavlaunch.value.launcherprofiles;

import java.util.Locale;

/** Assigns stable, isolated game directories to newly-created launcher profiles. */
public final class InstanceDirectoryPolicy {
    private InstanceDirectoryPolicy() {
    }

    public static String isolatedGameDir(String profileName, String profileKey) {
        String name = profileName == null ? "" : profileName.trim();
        String cleanName = name.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "")
                .toLowerCase(Locale.ROOT);
        if (cleanName.isEmpty()) cleanName = "instance";

        String key = profileKey == null ? "" : profileKey.replaceAll("[^A-Za-z0-9]", "");
        String suffix = key.isEmpty() ? "new" : key.substring(0, Math.min(8, key.length()));
        return "./custom_instances/" + cleanName + "-" + suffix;
    }

    public static void applyToNewProfile(MinecraftProfile profile, String profileKey) {
        if (profile == null) return;
        if (profile.gameDir == null || profile.gameDir.trim().isEmpty()) {
            profile.gameDir = isolatedGameDir(profile.name, profileKey);
        }
        if (profile.battlyInstanceId == null || profile.battlyInstanceId.trim().isEmpty()) {
            profile.battlyInstanceId = profileKey;
        }
        if (profile.battlySchemaVersion < 1) profile.battlySchemaVersion = 1;
        long now = System.currentTimeMillis();
        if (profile.battlyCreatedAt <= 0) profile.battlyCreatedAt = now;
        profile.battlyUpdatedAt = now;
    }
}
