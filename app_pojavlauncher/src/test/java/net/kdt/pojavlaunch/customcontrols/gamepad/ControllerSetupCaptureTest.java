package net.kdt.pojavlaunch.customcontrols.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;
import android.view.InputDevice;

import org.junit.Test;

public class ControllerSetupCaptureTest {
    @Test
    public void capturesEveryHatDirectionFromAxisBasedDpads() {
        assertEquals(MotionEvent.AXIS_HAT_Y,
                ControllerSetupCapture.expectedHatAxis(12, 0f, -1f));
        assertEquals(MotionEvent.AXIS_HAT_Y,
                ControllerSetupCapture.expectedHatAxis(13, 0f, 1f));
        assertEquals(MotionEvent.AXIS_HAT_X,
                ControllerSetupCapture.expectedHatAxis(14, -1f, 0f));
        assertEquals(MotionEvent.AXIS_HAT_X,
                ControllerSetupCapture.expectedHatAxis(15, 1f, 0f));
    }

    @Test
    public void rejectsWrongOrHeldHatDirection() {
        assertEquals(-1, ControllerSetupCapture.expectedHatAxis(12, 0f, 1f));
        assertEquals(-1, ControllerSetupCapture.expectedHatAxis(13, 0f, -1f));
        assertEquals(-1, ControllerSetupCapture.expectedHatAxis(14, 1f, 0f));
        assertEquals(-1, ControllerSetupCapture.expectedHatAxis(15, -1f, 0f));
        assertEquals(-1, ControllerSetupCapture.expectedHatAxis(12, 0f, .2f));
    }

    @Test
    public void requiresControlsToReturnToCenterBetweenSteps() {
        assertFalse(ControllerSetupCapture.areAxesNeutral(new float[]{.73f, 0f}));
        assertTrue(ControllerSetupCapture.areAxesNeutral(new float[]{.12f, -.2f}));
    }

    @Test
    public void capturesAxisBackedTriggersDuringButtonSteps() {
        assertEquals(MotionEvent.AXIS_LTRIGGER,
                ControllerSetupCapture.expectedTriggerAxis(8, .8f, 0f, 0f, 0f));
        assertEquals(MotionEvent.AXIS_RTRIGGER,
                ControllerSetupCapture.expectedTriggerAxis(9, 0f, .8f, 0f, 0f));
        assertEquals(MotionEvent.AXIS_BRAKE,
                ControllerSetupCapture.expectedTriggerAxis(8, 0f, 0f, .8f, 0f));
        assertEquals(MotionEvent.AXIS_GAS,
                ControllerSetupCapture.expectedTriggerAxis(9, 0f, 0f, 0f, .8f));
    }

    @Test
    public void acceptsDpadOnlyControllerSources() {
        assertTrue(GamepadDpad.isControllerSource(
                InputDevice.SOURCE_DPAD, InputDevice.KEYBOARD_TYPE_NONE));
        assertTrue(GamepadDpad.isControllerSource(
                InputDevice.SOURCE_JOYSTICK, InputDevice.KEYBOARD_TYPE_NONE));
        assertFalse(GamepadDpad.isControllerSource(
                InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC));
    }

    @Test
    public void remappedRuntimeConsumesCanonicalRightStickAxes() {
        int[] axes = ControllerInputSettings.resolveRightStickAxes(
                true, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY);

        assertEquals(MotionEvent.AXIS_Z, axes[0]);
        assertEquals(MotionEvent.AXIS_RZ, axes[1]);
    }

    @Test
    public void capturesHatAxesWhenControllerReportsAStickAsHat() {
        int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
        float[] values = {0f, 0f, .91f, 0f};

        assertEquals(MotionEvent.AXIS_HAT_X,
                ControllerSetupCapture.strongestAvailableAxis(axes, values, new int[0]));
    }

    @Test
    public void doesNotReuseAnAxisAlreadyCapturedForAnotherStickDirection() {
        int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_RX, MotionEvent.AXIS_RY};
        float[] values = {.95f, 0f, .80f, 0f};

        assertEquals(MotionEvent.AXIS_RX,
                ControllerSetupCapture.strongestAvailableAxis(
                        axes, values, new int[]{MotionEvent.AXIS_X}));
    }
}
