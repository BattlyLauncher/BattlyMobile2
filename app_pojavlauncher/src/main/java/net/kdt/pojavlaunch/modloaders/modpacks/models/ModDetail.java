package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[][] versionGameVersions;
    public String[] versionUrls;
    public String[] versionFileNames;
    public String[][] versionLoaders;
    public ModDependency[][] versionDependencies;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] versionFileNames, String[] hashes, String[][] versionLoaders, ModDependency[][] versionDependencies) {
        this(item, versionNames, mcVersionNames, expandPrimaryVersions(mcVersionNames), versionUrls,
                versionFileNames, hashes, versionLoaders, versionDependencies);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames,
                     String[][] versionGameVersions, String[] versionUrls,
                     String[] versionFileNames, String[] hashes, String[][] versionLoaders,
                     ModDependency[][] versionDependencies) {
        super(item.apiSource, item.contentType, item.id, item.title, item.description, item.imageUrl);
        this.categories = item.categories;
        this.loaders = item.loaders;
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionGameVersions = versionGameVersions == null
                ? expandPrimaryVersions(mcVersionNames)
                : versionGameVersions;
        this.versionUrls = versionUrls;
        this.versionFileNames = versionFileNames;
        this.versionHashes = hashes;
        this.versionLoaders = versionLoaders;
        this.versionDependencies = versionDependencies;

        // Add the MC version to the visible version label when the API provides one.
        // CurseForge may return dependency files without a displayName or a Minecraft
        // version, so keep every slot printable instead of crashing the dependency flow.
        if (this.versionNames == null) {
            this.versionNames = new String[0];
        }
        for (int i = 0; i < this.versionNames.length; i++) {
            String versionName = this.versionNames[i];
            if (versionName == null || versionName.isEmpty()) {
                versionName = versionFileNames != null && i < versionFileNames.length
                        && versionFileNames[i] != null && !versionFileNames[i].isEmpty()
                        ? versionFileNames[i]
                        : "Version " + (i + 1);
            }

            String mcVersion = mcVersionNames != null && i < mcVersionNames.length ? mcVersionNames[i] : null;
            String[] supportedVersions = getGameVersions(i);
            if (supportedVersions.length <= 1 && mcVersion != null && !mcVersion.isEmpty()
                    && !versionName.contains(mcVersion)) {
                versionName += " - " + mcVersion;
            }
            this.versionNames[i] = versionName;
        }
    }

    public String[] getGameVersions(int versionIndex) {
        if (versionIndex >= 0 && versionGameVersions != null
                && versionIndex < versionGameVersions.length
                && versionGameVersions[versionIndex] != null) {
            return versionGameVersions[versionIndex];
        }
        if (versionIndex >= 0 && mcVersionNames != null
                && versionIndex < mcVersionNames.length
                && mcVersionNames[versionIndex] != null) {
            return new String[]{mcVersionNames[versionIndex]};
        }
        return new String[0];
    }

    public boolean supportsMinecraftVersion(int versionIndex, String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isEmpty()) return true;
        String[] supportedVersions = getGameVersions(versionIndex);
        if (supportedVersions.length == 0) return true;
        for (String supportedVersion : supportedVersions) {
            if (minecraftVersion.equalsIgnoreCase(supportedVersion)) return true;
        }
        return false;
    }

    private static String[][] expandPrimaryVersions(String[] primaryVersions) {
        if (primaryVersions == null) return new String[0][];
        String[][] expanded = new String[primaryVersions.length][];
        for (int i = 0; i < primaryVersions.length; i++) {
            expanded[i] = primaryVersions[i] == null
                    ? new String[0]
                    : new String[]{primaryVersions[i]};
        }
        return expanded;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionGameVersions=" + Arrays.deepToString(versionGameVersions) +
                ", versionIds=" + Arrays.toString(versionUrls) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", contentType=" + contentType +
                ", isModpack=" + isModpack +
                '}';
    }
}
