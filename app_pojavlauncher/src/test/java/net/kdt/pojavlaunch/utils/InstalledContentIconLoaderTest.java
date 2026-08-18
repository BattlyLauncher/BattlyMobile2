package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;

public class InstalledContentIconLoaderTest {
    @Test
    public void preferredMetadataIconWins() {
        assertEquals("assets/example/logo.png", InstalledContentIconLoader.chooseArchiveEntry(
                Arrays.asList("icon.png", "assets/example/logo.png"),
                "assets/example/logo.png"));
    }

    @Test
    public void packIconWinsOverGenericImages() {
        assertEquals("pack.png", InstalledContentIconLoader.chooseArchiveEntry(
                Arrays.asList("assets/example/icon.png", "pack.png", "preview.png"),
                null));
    }

    @Test
    public void matchingIsCaseInsensitive() {
        assertEquals("META-INF/Logo.PNG", InstalledContentIconLoader.chooseArchiveEntry(
                Arrays.asList("META-INF/Logo.PNG"),
                "meta-inf/logo.png"));
    }

    @Test
    public void unrelatedTexturesAreIgnored() {
        assertNull(InstalledContentIconLoader.chooseArchiveEntry(
                Arrays.asList("assets/example/textures/block/stone.png", "screenshot.jpg"),
                null));
    }
}
