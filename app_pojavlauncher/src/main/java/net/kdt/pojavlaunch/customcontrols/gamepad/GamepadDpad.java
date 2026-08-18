package net.kdt.pojavlaunch.customcontrols.gamepad;

import static android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC;
import static android.view.InputDevice.SOURCE_DPAD;
import static android.view.InputDevice.SOURCE_GAMEPAD;
import static android.view.InputDevice.SOURCE_JOYSTICK;

import android.view.InputDevice;
import android.view.KeyEvent;


public class GamepadDpad {
    public static boolean isDpadEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        int sources = event.getSource() | (device == null ? 0 : device.getSources());
        int keyboardType = device == null ? InputDevice.KEYBOARD_TYPE_NONE : device.getKeyboardType();
        return isControllerSource(sources, keyboardType);
    }

    static boolean isControllerSource(int sources, int keyboardType) {
        boolean controller = (sources & SOURCE_GAMEPAD) == SOURCE_GAMEPAD
                || (sources & SOURCE_DPAD) == SOURCE_DPAD
                || (sources & SOURCE_JOYSTICK) == SOURCE_JOYSTICK;
        return controller && keyboardType != KEYBOARD_TYPE_ALPHABETIC;
    }

}
