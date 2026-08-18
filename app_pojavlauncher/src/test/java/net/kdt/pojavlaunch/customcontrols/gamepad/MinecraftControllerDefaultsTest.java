package net.kdt.pojavlaunch.customcontrols.gamepad;

import static org.junit.Assert.assertEquals;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;

import org.junit.Test;

public class MinecraftControllerDefaultsTest {
    @Test
    public void newProfilesStartWithTheCompleteStandardLayout() {
        assertStandardLayout(GamepadMap.getDefaultGameMap());
    }

    @Test
    public void xboxAndPlayStationUseMinecraftDefaultActions() {
        assertStandardLayout(MinecraftControllerDefaults.createGameMap(
                ControllerTypeResolver.Style.XBOX));
        assertStandardLayout(MinecraftControllerDefaults.createGameMap(
                ControllerTypeResolver.Style.PLAYSTATION));
    }

    @Test
    public void switchUsesPrintedNintendoFaceButtons() {
        GamepadMap map = MinecraftControllerDefaults.createGameMap(
                ControllerTypeResolver.Style.SWITCH);

        // The setup assistant normalizes printed B/A/Y/X to canonical A/B/X/Y.
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, map.BUTTON_A.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_SPACE, map.BUTTON_B.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_E, map.BUTTON_X.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_E, map.BUTTON_Y.keycodes[0]);
        assertSharedActions(map);
    }

    @Test
    public void switchMenuUsesPrintedAForAcceptAndBForBack() {
        GamepadMap map = MinecraftControllerDefaults.createMenuMap(
                ControllerTypeResolver.Style.SWITCH);

        assertEquals(LwjglGlfwKeycode.GLFW_KEY_ESCAPE, map.BUTTON_A.keycodes[0]);
        assertEquals(GamepadMap.MOUSE_LEFT, map.BUTTON_B.keycodes[0]);
    }

    private static void assertStandardLayout(GamepadMap map) {
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_SPACE, map.BUTTON_A.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, map.BUTTON_B.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_E, map.BUTTON_X.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_E, map.BUTTON_Y.keycodes[0]);
        assertSharedActions(map);
    }

    private static void assertSharedActions(GamepadMap map) {
        assertEquals(GamepadMap.MOUSE_RIGHT, map.TRIGGER_LEFT.keycodes[0]);
        assertEquals(GamepadMap.MOUSE_LEFT, map.TRIGGER_RIGHT.keycodes[0]);
        assertEquals(GamepadMap.MOUSE_SCROLL_UP, map.SHOULDER_LEFT.keycodes[0]);
        assertEquals(GamepadMap.MOUSE_SCROLL_DOWN, map.SHOULDER_RIGHT.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_ESCAPE, map.BUTTON_START.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_TAB, map.BUTTON_SELECT.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, map.THUMBSTICK_LEFT.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_E, map.THUMBSTICK_RIGHT.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_B, map.DPAD_UP.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_Q, map.DPAD_DOWN.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_T, map.DPAD_RIGHT.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_F5, map.DPAD_LEFT.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_W, map.DIRECTION_FORWARD.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_S, map.DIRECTION_BACKWARD.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_A, map.DIRECTION_LEFT.keycodes[0]);
        assertEquals(LwjglGlfwKeycode.GLFW_KEY_D, map.DIRECTION_RIGHT.keycodes[0]);
    }
}
