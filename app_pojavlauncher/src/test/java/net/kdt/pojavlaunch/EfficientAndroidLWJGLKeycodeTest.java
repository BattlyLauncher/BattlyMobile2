package net.kdt.pojavlaunch;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

public class EfficientAndroidLWJGLKeycodeTest {
    @Test
    public void escapeUsesKeyboardEscapeForSdlBridge() {
        assertEquals(
                KeyEvent.KEYCODE_ESCAPE,
                EfficientAndroidLWJGLKeycode.getAndroidKeycode(LwjglGlfwKeycode.GLFW_KEY_ESCAPE));
    }
}
