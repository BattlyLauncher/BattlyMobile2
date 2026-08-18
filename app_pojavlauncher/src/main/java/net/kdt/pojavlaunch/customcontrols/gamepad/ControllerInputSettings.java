package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.view.MotionEvent;
import android.view.KeyEvent;

/** Pure controller configuration helpers shared by preferences and the runtime input path. */
public final class ControllerInputSettings {
    public static final String MODE_BATTLY = "battly";
    public static final String MODE_NATIVE = "native";

    private ControllerInputSettings() {
    }

    public static String normalizeMode(String value) {
        return MODE_NATIVE.equals(value) ? MODE_NATIVE : MODE_BATTLY;
    }

    public static boolean shouldUseNativeInput(String mode, boolean gameRequestedDirectInput) {
        return MODE_NATIVE.equals(normalizeMode(mode)) && gameRequestedDirectInput;
    }

    public static float applyCameraAxis(float value, int sensitivityPercent, boolean inverted) {
        float sensitivity = Math.max(25, Math.min(300, sensitivityPercent)) / 100f;
        float result = value * sensitivity;
        return inverted ? -result : result;
    }

    public static int[] resolveRightStickAxes(boolean remappedInput,
                                               int physicalHorizontalAxis,
                                               int physicalVerticalAxis) {
        if (remappedInput) {
            return new int[]{MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ};
        }
        return new int[]{physicalHorizontalAxis, physicalVerticalAxis};
    }

    public static boolean shouldSuppressDuplicateDpadKey(int keyCode, int flags,
                                                          boolean hasHatDpad) {
        boolean dpadKey = keyCode >= KeyEvent.KEYCODE_DPAD_UP
                && keyCode <= KeyEvent.KEYCODE_DPAD_CENTER;
        return dpadKey && (hasHatDpad || (flags & KeyEvent.FLAG_FALLBACK) != 0);
    }
}
