package net.kdt.patch;

import java.io.File;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class LabyModPatchTweaker implements ITweaker {
    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        LabyModShaderPatcher.patchShaderJar();
        classLoader.registerTransformer("net.kdt.patch.LabyModScreenTransformer");
        System.out.println("[LwjglPatchAgent] Registered LabyMod LaunchWrapper screen transformer.");
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
