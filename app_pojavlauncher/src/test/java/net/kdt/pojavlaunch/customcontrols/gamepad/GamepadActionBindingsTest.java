package net.kdt.pojavlaunch.customcontrols.gamepad;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GamepadActionBindingsTest {
    @Test
    public void defaultScrollBindingsAreDiscoverable() {
        GamepadMap map = GamepadMap.getDefaultGameMap();
        assertEquals(9, GamepadActionBindings.find(map, GamepadMap.MOUSE_SCROLL_UP));
        assertEquals(8, GamepadActionBindings.find(map, GamepadMap.MOUSE_SCROLL_DOWN));
    }

    @Test
    public void assigningScrollMovesItInsteadOfDuplicatingIt() {
        GamepadMap map = GamepadMap.getDefaultGameMap();
        GamepadActionBindings.assign(map, GamepadMap.MOUSE_SCROLL_UP, 16);
        assertEquals(16, GamepadActionBindings.find(map, GamepadMap.MOUSE_SCROLL_UP));
        assertEquals(GamepadMap.UNSPECIFIED, map.SHOULDER_LEFT.keycodes[0]);
    }
}
