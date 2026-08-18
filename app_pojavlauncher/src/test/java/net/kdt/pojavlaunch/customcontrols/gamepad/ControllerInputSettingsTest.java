package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.view.KeyEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControllerInputSettingsTest {
    @Test
    public void battlyModeKeepsCameraMappingWhenGameRequestsDirectInput() {
        assertFalse(ControllerInputSettings.shouldUseNativeInput("battly", true));
        assertFalse(ControllerInputSettings.shouldUseNativeInput(null, true));
    }

    @Test
    public void nativeModeRequiresGameSupport() {
        assertTrue(ControllerInputSettings.shouldUseNativeInput("native", true));
        assertFalse(ControllerInputSettings.shouldUseNativeInput("native", false));
    }

    @Test
    public void cameraAxisAppliesSensitivityAndInversion() {
        assertEquals(1f, ControllerInputSettings.applyCameraAxis(0.5f, 200, false), 0.0001f);
        assertEquals(-1f, ControllerInputSettings.applyCameraAxis(0.5f, 200, true), 0.0001f);
        assertEquals(0.125f, ControllerInputSettings.applyCameraAxis(0.5f, 1, false), 0.0001f);
    }

    @Test
    public void hatDpadSuppressesAndroidCompatibilityKeys() {
        assertTrue(ControllerInputSettings.shouldSuppressDuplicateDpadKey(
                KeyEvent.KEYCODE_DPAD_LEFT, 0, true));
        assertTrue(ControllerInputSettings.shouldSuppressDuplicateDpadKey(
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.FLAG_FALLBACK, false));
        assertFalse(ControllerInputSettings.shouldSuppressDuplicateDpadKey(
                KeyEvent.KEYCODE_BUTTON_A, 0, true));
        assertFalse(ControllerInputSettings.shouldSuppressDuplicateDpadKey(
                KeyEvent.KEYCODE_DPAD_UP, 0, false));
    }
}
