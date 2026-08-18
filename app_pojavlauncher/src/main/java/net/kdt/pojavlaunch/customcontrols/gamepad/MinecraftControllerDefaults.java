package net.kdt.pojavlaunch.customcontrols.gamepad;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;

/** Complete Java Edition bindings matching Minecraft's Xbox, PlayStation and Switch layouts. */
public final class MinecraftControllerDefaults {
    private MinecraftControllerDefaults() {
    }

    public static GamepadMap createGameMap(ControllerTypeResolver.Style style) {
        GamepadMap map = GamepadMap.createEmptyMap();
        boolean isSwitch = style == ControllerTypeResolver.Style.SWITCH;

        // The guided setup normalizes printed Switch B/A/Y/X into canonical A/B/X/Y.
        map.BUTTON_A.keycodes[0] = isSwitch
                ? LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT : LwjglGlfwKeycode.GLFW_KEY_SPACE;
        map.BUTTON_B.keycodes[0] = isSwitch
                ? LwjglGlfwKeycode.GLFW_KEY_SPACE : LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT;
        map.BUTTON_X.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_E;
        map.BUTTON_Y.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_E;

        map.DIRECTION_FORWARD.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_W;
        map.DIRECTION_BACKWARD.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_S;
        map.DIRECTION_LEFT.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_A;
        map.DIRECTION_RIGHT.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_D;

        map.TRIGGER_LEFT.keycodes[0] = GamepadMap.MOUSE_RIGHT;
        map.TRIGGER_RIGHT.keycodes[0] = GamepadMap.MOUSE_LEFT;
        map.SHOULDER_LEFT.keycodes[0] = GamepadMap.MOUSE_SCROLL_UP;
        map.SHOULDER_RIGHT.keycodes[0] = GamepadMap.MOUSE_SCROLL_DOWN;

        map.BUTTON_START.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_ESCAPE;
        map.BUTTON_SELECT.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_TAB;
        map.THUMBSTICK_LEFT.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL;
        map.THUMBSTICK_RIGHT.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_E;

        // Java has no vanilla emote key. B supports Emotecraft and E opens crafting/inventory.
        map.DPAD_UP.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_B;
        map.DPAD_DOWN.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_Q;
        map.DPAD_RIGHT.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_T;
        map.DPAD_LEFT.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_F5;
        return map;
    }

    public static GamepadMap createMenuMap(ControllerTypeResolver.Style style) {
        GamepadMap map = GamepadMap.getDefaultMenuMap();
        if (style == ControllerTypeResolver.Style.SWITCH) {
            map.BUTTON_A.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_ESCAPE;
            map.BUTTON_B.keycodes[0] = GamepadMap.MOUSE_LEFT;
        }
        map.BUTTON_START.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_ESCAPE;
        map.BUTTON_SELECT.keycodes[0] = LwjglGlfwKeycode.GLFW_KEY_TAB;
        return map;
    }
}
