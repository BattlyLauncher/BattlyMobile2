package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FramePacingPolicyTest {
    @Test
    public void usesTheCurrentDisplayModeInsteadOfInventing120Hz() {
        assertEquals(60f, FramePacingPolicy.selectSurfaceFrameRate(60f,
                new float[0]), 0.01f);
        assertEquals(90f, FramePacingPolicy.selectSurfaceFrameRate(90f,
                new float[]{60f, 120f}), 0.01f);
    }

    @Test
    public void fallsBackToARealAlternativeWhenCurrentModeIsInvalid() {
        assertEquals(120f, FramePacingPolicy.selectSurfaceFrameRate(0f,
                new float[]{60f, Float.NaN, 120f}), 0.01f);
    }

    @Test
    public void letsAndroidChooseWhenNoValidModeIsKnown() {
        assertEquals(0f, FramePacingPolicy.selectSurfaceFrameRate(Float.NaN,
                new float[]{0f, -1f}), 0.01f);
    }

    @Test
    public void alternateSurfaceSelectionIsHonored() {
        assertTrue(FramePacingPolicy.useSurfaceView(true));
        assertFalse(FramePacingPolicy.useSurfaceView(false));
    }
}
