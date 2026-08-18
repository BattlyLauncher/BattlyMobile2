package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GraphicsAdvisorTest {
    private static final List<String> RENDERERS = Arrays.asList(
            "opengles2",
            "opengles_mobileglues",
            "opengles3_desktopgl_freedreno",
            "opengles3_desktopgl_zink_kopper");

    @Test
    public void modernMinecraftPrioritizesMobileGlues() {
        List<String> ranked = GraphicsAdvisor.rankCandidates(
                RENDERERS, "qualcomm adreno 840", true, true, 8192, 30);

        assertEquals("opengles_mobileglues", ranked.get(0));
        assertEquals("opengles3_desktopgl_freedreno", ranked.get(1));
    }

    @Test
    public void lowMemoryLegacyMinecraftPrioritizesGl4es() {
        List<String> ranked = GraphicsAdvisor.rankCandidates(
                RENDERERS, "arm mali-g52", false, false, 3072, 80);

        assertEquals("opengles2", ranked.get(0));
        assertFalse(ranked.isEmpty());
    }

    @Test
    public void resultNeverContainsUnavailableRenderer() {
        List<String> ranked = GraphicsAdvisor.rankCandidates(
                Arrays.asList("opengles2"), "qualcomm adreno", true, true, 8192, 25);

        assertEquals(Arrays.asList("opengles2"), ranked);
    }
}
