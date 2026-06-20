package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.NonNull;

public class SearchCategory {
    public final int source;
    public final String id;
    public final String name;

    public SearchCategory(int source, String id, String name) {
        this.source = source;
        this.id = id;
        this.name = name;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
