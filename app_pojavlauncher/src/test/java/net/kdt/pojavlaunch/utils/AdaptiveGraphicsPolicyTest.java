package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdaptiveGraphicsPolicyTest {
    private static final String MOBILE_GLUES = "opengles_mobileglues";
    private static final String FREEDRENO = "opengles3_desktopgl_freedreno";
    private static final String KOPPER = "opengles3_desktopgl_zink_kopper";

    @Test
    public void adrenoSixWithShadersPrefersFreedreno() {
        AdaptiveGraphicsPolicy.Decision decision = decide(
                "auto", "Adreno (TM) 640", true, true, false, 8192, 100,
                Arrays.asList(MOBILE_GLUES, FREEDRENO, KOPPER));

        assertEquals(FREEDRENO, decision.rendererId);
        assertEquals(75, decision.resolutionPercent);
        assertTrue(decision.enableSustainedPerformance);
    }

    @Test
    public void adrenoSevenAvoidsAutomaticFreedreno() {
        AdaptiveGraphicsPolicy.Decision decision = decide(
                "auto", "Qualcomm Adreno 740", true, true, false, 12288, 100,
                Arrays.asList(MOBILE_GLUES, FREEDRENO, KOPPER));

        assertEquals(MOBILE_GLUES, decision.rendererId);
    }

    @Test
    public void desktopRequiredVersionKeepsMobileGluesFirst() {
        AdaptiveGraphicsPolicy.Decision decision = decide(
                "performance", "Adreno (TM) 650", true, true, true, 8192, 90,
                Arrays.asList(MOBILE_GLUES, FREEDRENO, KOPPER));

        assertEquals(MOBILE_GLUES, decision.rendererId);
        assertEquals(60, decision.resolutionPercent);
    }

    @Test
    public void maliNeverSelectsFreedreno() {
        AdaptiveGraphicsPolicy.Decision decision = decide(
                "balanced", "ARM Mali-G78", true, true, false, 6144, 100,
                Arrays.asList(MOBILE_GLUES, FREEDRENO, KOPPER));

        assertEquals(MOBILE_GLUES, decision.rendererId);
        assertEquals(72, decision.resolutionPercent);
    }

    @Test
    public void qualityDoesNotReduceUserResolution() {
        AdaptiveGraphicsPolicy.Decision decision = decide(
                "quality", "Adreno (TM) 640", true, true, false, 8192, 88,
                Arrays.asList(MOBILE_GLUES, FREEDRENO, KOPPER));

        assertEquals(88, decision.resolutionPercent);
    }

    @Test
    public void inactiveShadersLeaveScaleAndSustainedModeUntouched() {
        AdaptiveGraphicsPolicy.Decision decision = decide(
                "auto", "Adreno (TM) 640", false, true, false, 4096, 93,
                Arrays.asList(MOBILE_GLUES, FREEDRENO, KOPPER));

        assertEquals(MOBILE_GLUES, decision.rendererId);
        assertEquals(93, decision.resolutionPercent);
        assertFalse(decision.enableSustainedPerformance);
    }

    @Test
    public void customModeNeverOverridesBaseline() {
        AdaptiveGraphicsPolicy.Decision decision = new AdaptiveGraphicsPolicy.Input(
                "custom", "Adreno (TM) 640", true, true, false, 4096, 80,
                false, KOPPER, Arrays.asList(MOBILE_GLUES, FREEDRENO, KOPPER),
                Collections.emptyList()).decide();

        assertEquals(KOPPER, decision.rendererId);
        assertEquals(80, decision.resolutionPercent);
        assertFalse(decision.adapted);
    }

    @Test
    public void blockedRendererIsSkipped() {
        AdaptiveGraphicsPolicy.Decision decision = new AdaptiveGraphicsPolicy.Input(
                "auto", "Adreno (TM) 640", true, true, false, 8192, 100,
                false, MOBILE_GLUES, Arrays.asList(MOBILE_GLUES, FREEDRENO, KOPPER),
                Collections.singletonList(FREEDRENO)).decide();

        assertEquals(MOBILE_GLUES, decision.rendererId);
    }

    @Test
    public void legacyMinecraftKeepsCompatibilityRendererWithShaders() {
        AdaptiveGraphicsPolicy.Decision decision = new AdaptiveGraphicsPolicy.Input(
                "auto", "Adreno (TM) 640", true, false, false, 4096, 100,
                false, "opengles2", Arrays.asList("opengles2", MOBILE_GLUES, FREEDRENO),
                Collections.emptyList()).decide();

        assertEquals("opengles2", decision.rendererId);
        assertEquals(65, decision.resolutionPercent);
    }

    private static AdaptiveGraphicsPolicy.Decision decide(String profile, String gpu,
                                                            boolean shaders, boolean modern,
                                                            boolean desktop,
                                                            long ramMb, int resolution,
                                                            java.util.List<String> compatible) {
        return new AdaptiveGraphicsPolicy.Input(profile, gpu, shaders, modern, desktop, ramMb,
                resolution, false, MOBILE_GLUES, compatible,
                Collections.emptyList()).decide();
    }
}
