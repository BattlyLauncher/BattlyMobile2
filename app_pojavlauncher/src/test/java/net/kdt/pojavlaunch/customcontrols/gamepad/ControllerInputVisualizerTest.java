package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.view.KeyEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControllerInputVisualizerTest {
    @Test
    public void mapsPhysicalControllerButtonsToVisualControls() {
        assertEquals(ControllerInputVisualizer.Control.A,
                ControllerInputVisualizer.controlForKeyCode(KeyEvent.KEYCODE_BUTTON_A));
        assertEquals(ControllerInputVisualizer.Control.L2,
                ControllerInputVisualizer.controlForKeyCode(KeyEvent.KEYCODE_BUTTON_L2));
        assertEquals(ControllerInputVisualizer.Control.DPAD_LEFT,
                ControllerInputVisualizer.controlForKeyCode(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(ControllerInputVisualizer.Control.START,
                ControllerInputVisualizer.controlForKeyCode(KeyEvent.KEYCODE_BUTTON_START));
    }

    @Test
    public void analogStateClampsValuesAndExposesActiveDirections() {
        ControllerInputVisualizer.State state = ControllerInputVisualizer.State.forAnalogValues(
                1.4f, -1.3f, -0.6f, 0.7f, -1f, 1f, 0.8f, 0.1f);

        assertEquals(1f, state.leftX, 0.0001f);
        assertEquals(-1f, state.leftY, 0.0001f);
        assertTrue(state.isPressed(ControllerInputVisualizer.Control.DPAD_LEFT));
        assertFalse(state.isPressed(ControllerInputVisualizer.Control.DPAD_RIGHT));
        assertTrue(state.isPressed(ControllerInputVisualizer.Control.L2));
        assertFalse(state.isPressed(ControllerInputVisualizer.Control.R2));
    }
}
