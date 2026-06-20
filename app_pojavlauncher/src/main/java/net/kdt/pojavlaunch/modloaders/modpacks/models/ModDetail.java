package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionUrls;
    public String[] versionFileNames;
    public String[][] versionLoaders;
    public ModDependency[][] versionDependencies;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] versionFileNames, String[] hashes, String[][] versionLoaders, ModDependency[][] versionDependencies) {
        super(item.apiSource, item.contentType, item.id, item.title, item.description, item.imageUrl);
        this.categories = item.categories;
        this.loaders = item.loaders;
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionFileNames = versionFileNames;
        this.versionHashes = hashes;
        this.versionLoaders = versionLoaders;
        this.versionDependencies = versionDependencies;

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (!versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
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
