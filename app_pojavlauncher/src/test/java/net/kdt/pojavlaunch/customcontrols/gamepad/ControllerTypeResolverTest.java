package net.kdt.pojavlaunch.customcontrols.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import android.view.MotionEvent;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ControllerTypeResolverTest {
    @Test
    public void detectsSonyControllersByVendor() {
        assertEquals(ControllerTypeResolver.Style.PLAYSTATION,
                ControllerTypeResolver.resolve(ControllerTypeResolver.Style.AUTO,
                        ControllerTypeResolver.SONY_VENDOR_ID, "Wireless Controller"));
    }

    @Test
    public void detectsDualSenseWithoutVendorMetadata() {
        assertEquals(ControllerTypeResolver.Style.PLAYSTATION,
                ControllerTypeResolver.resolve(ControllerTypeResolver.Style.AUTO, 0,
                        "DualSense Wireless Controller"));
    }

    @Test
    public void manualStyleOverridesDetection() {
        assertEquals(ControllerTypeResolver.Style.XBOX,
                ControllerTypeResolver.resolve(ControllerTypeResolver.Style.XBOX,
                        ControllerTypeResolver.SONY_VENDOR_ID, "DualShock 4"));
    }

    @Test
    public void detectsNintendoControllersByName() {
        assertEquals(ControllerTypeResolver.Style.SWITCH,
                ControllerTypeResolver.resolve(ControllerTypeResolver.Style.AUTO, 0,
                        "Nintendo Switch Pro Controller"));
    }

    @Test
    public void mapsSonyTouchpadClickToSelect() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_SELECT,
                ControllerTypeResolver.normalizeKeyCode(
                        ControllerTypeResolver.Style.PLAYSTATION,
                        KeyEvent.KEYCODE_BUTTON_1));
    }

    @Test
    public void leavesGenericExtraButtonUnchanged() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_1,
                ControllerTypeResolver.normalizeKeyCode(
                        ControllerTypeResolver.Style.GENERIC,
                        KeyEvent.KEYCODE_BUTTON_1));
    }

    @Test
    public void playStationUsesAndroidStandardAxesWhenBothLayoutsAreReported() {
        assertArrayEquals(new int[]{MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ},
                ControllerTypeResolver.selectRightStickAxes(
                        ControllerTypeResolver.Style.PLAYSTATION, true, true));
    }

    @Test
    public void playStationFallsBackToRotationAxes() {
        assertArrayEquals(new int[]{MotionEvent.AXIS_RX, MotionEvent.AXIS_RY},
                ControllerTypeResolver.selectRightStickAxes(
                        ControllerTypeResolver.Style.PLAYSTATION, false, true));
    }

    @Test
    public void stickAxesMustBeBipolar() {
        assertTrue(ControllerTypeResolver.isBipolarRange(-1f, 1f));
        assertFalse(ControllerTypeResolver.isBipolarRange(0f, 1f));
    }

    @Test
    public void genericAndroidControllersKeepStandardZAndRzAxes() {
        assertArrayEquals(new int[]{MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ},
                ControllerTypeResolver.selectRightStickAxes(
                        ControllerTypeResolver.Style.GENERIC, true, true));
    }
}
