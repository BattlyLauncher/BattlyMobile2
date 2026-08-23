package net.kdt.pojavlaunch.battlyworlds;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves a hosted version without ever modifying an existing launcher profile. */
final class BattlyWorldsVersionResolver {
    private static final Pattern FORGE_SUFFIX = Pattern.compile("^([0-9][^-]*)-forge-(.+)$");
    private static final Pattern FORGE_PREFIX = Pattern.compile("^forge-([0-9][^-]*)-(.+)$");
    private static final Pattern FABRIC = Pattern.compile("^fabric-loader-(.+)-([0-9][^-]*)$");
    private static final Pattern QUILT = Pattern.compile("^quilt-loader-(.+)-([0-9][^-]*)$");
    private static final Pattern MINECRAFT_VERSION = Pattern.compile("(?:^|[^0-9])(1\\.\\d+(?:\\.\\d+)?|2[0-9]\\.\\d+(?:\\.\\d+)?)(?:$|[^0-9])");

    private BattlyWorldsVersionResolver() {
    }

    @Nullable
    static String findProfileKey(Map<String, MinecraftProfile> profiles, String requestedVersion) {
        return findProfileKey(profiles, null, requestedVersion);
    }

    @Nullable
    static String findProfileKey(Map<String, MinecraftProfile> profiles,
                                 @Nullable String currentProfileKey,
                                 String requestedVersion) {
        if (profiles == null || requestedVersion == null || requestedVersion.trim().isEmpty()) return null;
        String requestedCanonical = canonical(requestedVersion);
        for (Map.Entry<String, MinecraftProfile> entry : profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile != null && requestedCanonical.equals(canonical(profile.lastVersionId))) {
                return entry.getKey();
            }
        }
        String requestedMinecraft = minecraftVersion(requestedVersion);
        String requestedLoader = loaderFamily(requestedVersion);
        if (currentProfileKey != null) {
            MinecraftProfile current = profiles.get(currentProfileKey);
            if (isCompatible(current, requestedMinecraft, requestedLoader)) return currentProfileKey;
        }
        for (Map.Entry<String, MinecraftProfile> entry : profiles.entrySet()) {
            if (isCompatible(entry.getValue(), requestedMinecraft, requestedLoader)) return entry.getKey();
        }
        if (currentProfileKey != null) {
            MinecraftProfile current = profiles.get(currentProfileKey);
            if (hasMinecraftVersion(current, requestedMinecraft)) return currentProfileKey;
        }
        for (Map.Entry<String, MinecraftProfile> entry : profiles.entrySet()) {
            if (hasMinecraftVersion(entry.getValue(), requestedMinecraft)) return entry.getKey();
        }
        return null;
    }

    /** Finds the on-disk ID for equivalent Forge/Fabric IDs emitted by another launcher. */
    static String findInstalledVersionId(File versionsDirectory, String requestedVersion) {
        if (requestedVersion == null || requestedVersion.trim().isEmpty()) return "";
        File exactJson = versionJson(versionsDirectory, requestedVersion);
        if (exactJson.isFile() && exactJson.canRead()) return requestedVersion;
        File[] directories = versionsDirectory == null ? null : versionsDirectory.listFiles(File::isDirectory);
        if (directories == null) return requestedVersion;
        String requestedCanonical = canonical(requestedVersion);
        for (File directory : directories) {
            String candidate = directory.getName();
            if (requestedCanonical.equals(canonical(candidate))
                    && versionJson(versionsDirectory, candidate).isFile()) {
                return candidate;
            }
        }
        String baseVersion = minecraftVersion(requestedVersion);
        if (!baseVersion.isEmpty()) {
            File baseJson = versionJson(versionsDirectory, baseVersion);
            if (baseJson.isFile() && baseJson.canRead()) return baseVersion;
            // Mojang can download the base version, but not launcher-specific custom IDs.
            if (!loaderFamily(requestedVersion).isEmpty()) return baseVersion;
        }
        return requestedVersion;
    }

    static String canonical(String versionId) {
        String normalized = versionId == null ? "" : versionId.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = FORGE_SUFFIX.matcher(normalized);
        if (matcher.matches()) return "forge:" + matcher.group(1) + ":" + matcher.group(2);
        matcher = FORGE_PREFIX.matcher(normalized);
        if (matcher.matches()) return "forge:" + matcher.group(1) + ":" + matcher.group(2);
        matcher = FABRIC.matcher(normalized);
        if (matcher.matches()) return "fabric:" + matcher.group(2) + ":" + matcher.group(1);
        matcher = QUILT.matcher(normalized);
        if (matcher.matches()) return "quilt:" + matcher.group(2) + ":" + matcher.group(1);
        return normalized;
    }

    private static boolean isCompatible(MinecraftProfile profile, String requestedMinecraft,
                                        String requestedLoader) {
        if (profile == null || requestedMinecraft.isEmpty()) return false;
        String candidateVersion = profile.lastVersionId;
        if (!requestedMinecraft.equals(minecraftVersion(candidateVersion))) return false;
        String candidateLoader = loaderFamily(candidateVersion);
        return requestedLoader.isEmpty() || candidateLoader.isEmpty()
                || requestedLoader.equals(candidateLoader);
    }

    private static boolean hasMinecraftVersion(MinecraftProfile profile, String requestedMinecraft) {
        return profile != null && !requestedMinecraft.isEmpty()
                && requestedMinecraft.equals(minecraftVersion(profile.lastVersionId));
    }

    private static String loaderFamily(String versionId) {
        String value = versionId == null ? "" : versionId.toLowerCase(Locale.ROOT);
        if (value.contains("neoforge")) return "neoforge";
        if (value.contains("forge")) return "forge";
        if (value.contains("fabric")) return "fabric";
        if (value.contains("quilt")) return "quilt";
        if (value.contains("optifine")) return "optifine";
        return "";
    }

    private static String minecraftVersion(String versionId) {
        String value = versionId == null ? "" : versionId.toLowerCase(Locale.ROOT);
        Matcher matcher = FORGE_SUFFIX.matcher(value);
        if (matcher.matches()) return matcher.group(1);
        matcher = FORGE_PREFIX.matcher(value);
        if (matcher.matches()) return matcher.group(1);
        matcher = FABRIC.matcher(value);
        if (matcher.matches()) return matcher.group(2);
        matcher = QUILT.matcher(value);
        if (matcher.matches()) return matcher.group(2);
        matcher = MINECRAFT_VERSION.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static File versionJson(File versionsDirectory, String versionId) {
        return new File(new File(versionsDirectory == null ? new File("") : versionsDirectory, versionId),
                versionId + ".json");
    }
}
