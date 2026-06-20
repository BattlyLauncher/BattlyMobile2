package net.kdt.pojavlaunch.modloaders.modpacks.models;

public class ModDependency {
    public final String projectId;
    public final String displayName;
    public final boolean required;

    public ModDependency(String projectId, String displayName, boolean required) {
        this.projectId = projectId;
        this.displayName = displayName;
        this.required = required;
    }
}
