package net.kdt.pojavlaunch.customcontrols.gamepad;

/** Pure helpers for assigning Battly actions to physical controller controls. */
public final class GamepadActionBindings {
    private GamepadActionBindings() {
    }

    public static int find(GamepadMap map, short action) {
        GamepadEmulatedButton[] buttons = orderedButtons(map);
        for (int index = 0; index < buttons.length; index++) {
            for (short keycode : buttons[index].keycodes) {
                if (keycode == action) return index;
            }
        }
        return -1;
    }

    public static void assign(GamepadMap map, short action, int controlIndex) {
        GamepadEmulatedButton[] buttons = orderedButtons(map);
        if (controlIndex < 0 || controlIndex >= buttons.length) return;
        for (GamepadEmulatedButton button : buttons) {
            for (int keyIndex = 0; keyIndex < button.keycodes.length; keyIndex++) {
                if (button.keycodes[keyIndex] == action) {
                    button.keycodes[keyIndex] = GamepadMap.UNSPECIFIED;
                }
            }
        }
        buttons[controlIndex].keycodes[0] = action;
    }

    /** Order shared with GamepadMapperAdapter and the dedicated action selectors. */
    public static GamepadEmulatedButton[] orderedButtons(GamepadMap map) {
        return new GamepadEmulatedButton[]{
                map.BUTTON_A, map.BUTTON_B, map.BUTTON_X, map.BUTTON_Y,
                map.BUTTON_START, map.BUTTON_SELECT,
                map.TRIGGER_RIGHT, map.TRIGGER_LEFT,
                map.SHOULDER_RIGHT, map.SHOULDER_LEFT,
                map.DIRECTION_FORWARD, map.DIRECTION_RIGHT,
                map.DIRECTION_LEFT, map.DIRECTION_BACKWARD,
                map.THUMBSTICK_RIGHT, map.THUMBSTICK_LEFT,
                map.DPAD_UP, map.DPAD_DOWN, map.DPAD_RIGHT, map.DPAD_LEFT
        };
    }
}
