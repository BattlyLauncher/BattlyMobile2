package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class MinecraftOptionsFileTest {
    @Test
    public void mergePreservesMinecraftSettingsAndUnknownLines() {
        String current = "version:4189\n"
                + "resourcePacks:[\"vanilla\",\"file/Stay enabled.zip\"]\n"
                + "incompatibleResourcePacks:[\"file/Stay enabled.zip\"]\n"
                + "fancyModOption:{\"enabled\":true}\n"
                + "future-format-line-without-a-colon\n"
                + "fullscreen:true\n";
        Map<String, String> launcherUpdates = new LinkedHashMap<>();
        launcherUpdates.put("fullscreen", "false");
        launcherUpdates.put("overrideWidth", "1920");

        String merged = MinecraftOptionsFile.merge(current, launcherUpdates);

        assertEquals("version:4189\n"
                + "resourcePacks:[\"vanilla\",\"file/Stay enabled.zip\"]\n"
                + "incompatibleResourcePacks:[\"file/Stay enabled.zip\"]\n"
                + "fancyModOption:{\"enabled\":true}\n"
                + "future-format-line-without-a-colon\n"
                + "fullscreen:false\n"
                + "overrideWidth:1920\n", merged);
    }

    @Test
    public void mergeKeepsWindowsLineEndingsAndOnlyReplacesRequestedKeys() {
        String current = "language:es_es\r\nresourcePacks:[\"file/Pack.zip\"]\r\nmaxFps:120\r\n";
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("maxFps", "60");

        assertEquals("language:es_es\r\nresourcePacks:[\"file/Pack.zip\"]\r\nmaxFps:60\r\n",
                MinecraftOptionsFile.merge(current, updates));
    }
}
