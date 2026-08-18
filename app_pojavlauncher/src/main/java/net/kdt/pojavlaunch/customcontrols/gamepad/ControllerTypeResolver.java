package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.Locale;

/** Normalizes Android controller metadata without changing the persisted key map format. */
public final class ControllerTypeResolver {
    public static final int SONY_VENDOR_ID = 0x054c;
    public static final String PREFERENCE_KEY = "gamepad_controller_style";

    public enum Style {
        AUTO("auto"),
        XBOX("xbox"),
        PLAYSTATION("playstation"),
        SWITCH("switch"),
        GENERIC("generic");

        public final String preferenceValue;

        Style(String preferenceValue) {
            this.preferenceValue = preferenceValue;
        }

        public static Style fromPreference(String value) {
            if (value != null) {
                for (Style style : values()) {
                    if (style.preferenceValue.equalsIgnoreCase(value)) return style;
                }
            }
            return AUTO;
        }
    }

    private ControllerTypeResolver() {}

    public static Style resolve(Style requested, InputDevice device) {
        return resolve(requested,
                device == null ? 0 : device.getVendorId(),
                device == null ? null : device.getName());
    }

    public static Style resolve(Style requested, int vendorId, String deviceName) {
        if (requested != null && requested != Style.AUTO) return requested;
        String name = deviceName == null ? "" : deviceName.toLowerCase(Locale.ROOT);
        if (vendorId == SONY_VENDOR_ID
                || name.contains("dualsense")
                || name.contains("dualshock")
                || name.contains("playstation")
                || name.contains("sony interactive")) {
            return Style.PLAYSTATION;
        }
        if (name.contains("xbox") || name.contains("x-input") || name.contains("xinput")) {
            return Style.XBOX;
        }
        if (name.contains("nintendo") || name.contains("switch") || name.contains("joy-con")
                || name.contains("pro controller")) {
            return Style.SWITCH;
        }
        return Style.GENERIC;
    }

    public static int normalizeKeyCode(Style style, int keyCode) {
        // Android exposes the DualShock/DualSense touchpad click as BUTTON_1 on many devices.
        if (style == Style.PLAYSTATION && keyCode == KeyEvent.KEYCODE_BUTTON_1) {
            return KeyEvent.KEYCODE_BUTTON_SELECT;
        }
        return keyCode;
    }

    public static int[] resolveRightStickAxes(InputDevice device) {
        return resolveRightStickAxes(device, resolve(Style.AUTO, device));
    }

    public static int[] resolveRightStickAxes(InputDevice device, Style style) {
        boolean hasStandardPair = isBipolarPair(device, MotionEvent.AXIS_Z,
                MotionEvent.AXIS_RZ);
        boolean hasRotationPair = isBipolarPair(device, MotionEvent.AXIS_RX,
                MotionEvent.AXIS_RY);
        if (!hasStandardPair && !hasRotationPair) {
            hasStandardPair = hasAxis(device, MotionEvent.AXIS_Z)
                    && hasAxis(device, MotionEvent.AXIS_RZ);
            hasRotationPair = hasAxis(device, MotionEvent.AXIS_RX)
                    && hasAxis(device, MotionEvent.AXIS_RY);
        }
        return selectRightStickAxes(style, hasStandardPair, hasRotationPair);
    }

    static int[] selectRightStickAxes(Style style, boolean hasStandardPair,
                                      boolean hasRotationPair) {
        // Android's standard mapping, Pojav and Mojo all use Z/RZ for the right stick. Some
        // vendor drivers expose RX/RY instead, so keep that as the device-range fallback. The
        // visual button style must never change physical axis routing.
        if (hasStandardPair) return new int[]{MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ};
        if (hasRotationPair) return new int[]{MotionEvent.AXIS_RX, MotionEvent.AXIS_RY};
        return new int[]{MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ};
    }

    static boolean isBipolarRange(float min, float max) {
        return min < -0.5f && max > 0.5f;
    }

    private static boolean isBipolarPair(InputDevice device, int horizontal, int vertical) {
        InputDevice.MotionRange horizontalRange = getRange(device, horizontal);
        InputDevice.MotionRange verticalRange = getRange(device, vertical);
        return horizontalRange != null && verticalRange != null
                && isBipolarRange(horizontalRange.getMin(), horizontalRange.getMax())
                && isBipolarRange(verticalRange.getMin(), verticalRange.getMax());
    }

    private static InputDevice.MotionRange getRange(InputDevice device, int axis) {
        if (device == null) return null;
        InputDevice.MotionRange range = device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
        return range != null ? range : device.getMotionRange(axis);
    }

    private static boolean hasAxis(InputDevice device, int axis) {
        if (device == null) return false;
        return device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null
                || device.getMotionRange(axis) != null;
    }
}
