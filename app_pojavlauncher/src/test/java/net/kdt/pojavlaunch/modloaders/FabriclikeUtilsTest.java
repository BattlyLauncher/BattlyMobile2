package net.kdt.pojavlaunch.modloaders;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FabriclikeUtilsTest {
    @Test
    public void sortsRecentStableLoaderBeforeOldAndPrereleaseBuilds() {
        assertTrue(FabriclikeUtils.compareVersionDescending("0.30.0", "0.24.0") < 0);
        assertTrue(FabriclikeUtils.compareVersionDescending("0.30.0", "0.30.0-beta.8") < 0);
        assertTrue(FabriclikeUtils.compareVersionDescending("0.29.2", "0.28.1") < 0);
    }
}
