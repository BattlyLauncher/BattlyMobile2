package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public static final int SOURCE_ANY = -1;
    public static final int TYPE_MODPACK = 0;
    public static final int TYPE_MOD = 1;
    public static final int TYPE_RESOURCEPACK = 2;
    public static final int TYPE_SHADER = 3;
    public static final int TYPE_DATAPACK = 4;
    public static final String LOADER_ANY = "";

    public int contentType = TYPE_MODPACK;
    public int source = SOURCE_ANY;
    public String name;
    @Nullable public String mcVersion;
    @Nullable public String loader = LOADER_ANY;
    @Nullable public SearchCategory category;

    public boolean isModpack() {
        return contentType == TYPE_MODPACK;
    }

    public boolean isMod() {
        return contentType == TYPE_MOD;
    }

    public boolean isResourcepack() {
        return contentType == TYPE_RESOURCEPACK;
    }

    public boolean isShader() {
        return contentType == TYPE_SHADER;
    }

    public boolean isDatapack() {
        return contentType == TYPE_DATAPACK;
    }
}
