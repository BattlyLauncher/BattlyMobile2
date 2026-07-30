package org.lwjgl.glfw;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.customcontrols.gamepad.direct.DirectGamepadEnableHandler;

import android.content.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.libsdl.app.SDLActivity;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import dalvik.annotation.optimization.CriticalNative;

public class CallbackBridge {
    public static final Choreographer sChoreographer = Choreographer.getInstance();
    private static final Handler sInputHandler = new Handler(Looper.getMainLooper());
    private static boolean isGrabbing = false;
    private static final ArrayList<GrabListener> grabListeners = new ArrayList<>();
    // Use a weak reference here to avoid possibly statically referencing a Context.
    private static @Nullable WeakReference<DirectGamepadEnableHandler> sDirectGamepadEnableHandler;
    private static boolean sSdlInputBridgeLogged;
    private static int sSdlMouseButtonState;
    
    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;
    public static final int CLIPBOARD_OPEN = 2002;
    
    public static volatile int windowWidth, windowHeight;
    public static volatile int physicalWidth, physicalHeight;
    public static float mouseX, mouseY;
    public volatile static boolean holdingAlt, holdingCapslock, holdingCtrl,
            holdingNumlock, holdingShift;

    public static final ByteBuffer sGamepadButtonBuffer;
    public static final FloatBuffer sGamepadAxisBuffer;
    public static boolean sGamepadDirectInput = false;

    public static void putMouseEventWithCoords(int button, float x, float y) {
        putMouseEventWithCoords(button, true, x, y);
        // Menu transitions can pause Choreographer before its delayed frame callback
        // executes. A main-loop task still runs and guarantees the matching release.
        sInputHandler.postDelayed(() -> putMouseEventWithCoords(button, false, x, y), 33);
    }
    
    public static void putMouseEventWithCoords(int button, boolean isDown, float x, float y /* , int dz, long nanos */) {
        sendCursorPos(x, y);
        sendMouseKeycode(button, CallbackBridge.getCurrentMods(), isDown);
    }


    public static void sendCursorPos(float x, float y) {
        mouseX = x;
        mouseY = y;
        if (MinecraftGLSurface.isSdlWindowBridgeEnabled()) {
            sendSdlMouse(getSdlMouseButtonState(), MotionEvent.ACTION_MOVE, mouseX, mouseY);
        } else {
            nativeSendCursorPos(mouseX, mouseY);
        }
    }

    public static void sendCursorDelta(float deltaX, float deltaY) {
        if (MinecraftGLSurface.isSdlWindowBridgeEnabled()) {
            // SDL consumes captured pointer motion as deltas. Accumulating them into
            // an absolute GLFW cursor caused the virtual cursor to drift and eventually
            // stopped camera movement after interacting with a menu.
            sendSdlMouse(getSdlMouseButtonState(),
                    MotionEvent.ACTION_MOVE, deltaX, deltaY, true);
        } else {
            mouseX += deltaX;
            mouseY += deltaY;
            nativeSendCursorPos(mouseX, mouseY);
        }
    }

    /**
     * Sends keycodes if keycode is populated. Used for in-game controls.
     * Sends character if keychar is populated. Used for chat and text input.
     * You can refer to glfwSetKeyCallback for the arguments.
     * @param keycode LwjglGlfwKeycode
     * @param keychar Literal char. Modifier keys does not affect this.
     * @param scancode
     * @param modifiers The action is one of The action is one of GLFW_PRESS, or GLFW_RELEASE.
     *                  We don't have GLFW_REPEAT working.
     * @param isDown If its being pressed down or not. 1 is true.
     */
    public static void sendKeycode(int keycode, char keychar, int scancode, int modifiers, boolean isDown) {
        if (keycode != 0 && MinecraftGLSurface.isSdlWindowBridgeEnabled()) {
            // Battly control maps store GLFW keycodes, while SDL's Android backend
            // consumes Android keycodes. Route the converted key only through SDL
            // so overlay buttons such as ESC, TAB and inventory reach Minecraft.
            int androidKeycode = EfficientAndroidLWJGLKeycode.getAndroidKeycode(keycode);
            if (androidKeycode != KeyEvent.KEYCODE_UNKNOWN) {
                logSdlInputBridge();
                if (isDown) {
                    SDLActivity.onNativeKeyDown(androidKeycode);
                } else {
                    SDLActivity.onNativeKeyUp(androidKeycode);
                }
            } else {
                Log.w("BattlyInput", "No Android mapping for GLFW keycode " + keycode);
            }
        } else if (keycode != 0) {
            nativeSendKey(keycode, scancode, isDown ? 1 : 0, modifiers);
        }
        // Only controlmaps goes through here, that means we need to block ISOControl or else
        // Minecraft tries to type :TAB: as a character in chat, fails, and then ignores the key,
        // breaking the tab autofill function in old versions. (like 1.12.2, 1.8.9).
        if(isDown && !Character.isISOControl(keychar)) {
            nativeSendCharMods(keychar,modifiers);
            nativeSendChar(keychar);
        }
    }

    public static void sendChar(char keychar, int modifiers){
        // Only an EditText goes through here, that means emojis are allowed, so no isISOControl
        // cause we might break emoji mods then.
        // See net/kdt/pojavlaunch/customcontrols/keyboard/TouchCharInput.java#L147 (onTextChanged)
        nativeSendCharMods(keychar,modifiers);
        nativeSendChar(keychar);
    }

    public static void sendKeyPress(int keyCode, int modifiers, boolean status) {
        sendKeyPress(keyCode, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, int scancode, int modifiers, boolean status) {
        sendKeyPress(keyCode, '\u0000', scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int scancode, int modifiers, boolean status) {
        CallbackBridge.sendKeycode(keyCode, keyChar, scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int modifiers, boolean status) {
        sendKeyPress(keyCode, keyChar, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode) {
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendMouseButton(int button, boolean status) {
        CallbackBridge.sendMouseKeycode(button, CallbackBridge.getCurrentMods(), status);
    }

    public static void sendMouseKeycode(int button, int modifiers, boolean isDown) {
        // if (isGrabbing()) DEBUG_STRING.append("MouseGrabStrace: " + android.util.Log.getStackTraceString(new Throwable()) + "\n");
        if (MinecraftGLSurface.isSdlWindowBridgeEnabled()) {
            int buttonState = updateSdlMouseButtonState(toAndroidMouseButton(button), isDown);
            sendSdlMouse(buttonState,
                    isDown ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, mouseX, mouseY);
        } else {
            nativeSendMouseButton(button, isDown ? 1 : 0, modifiers);
        }
    }

    public static void sendMouseKeycode(int keycode) {
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), true);
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), false);
    }
    
    public static void sendScroll(double xoffset, double yoffset) {
        if (MinecraftGLSurface.isSdlWindowBridgeEnabled()) {
            sendSdlMouse(getSdlMouseButtonState(),
                    MotionEvent.ACTION_SCROLL, (float) xoffset, (float) yoffset);
        } else {
            nativeSendScroll(xoffset, yoffset);
        }
    }

    private static int toAndroidMouseButton(int glfwButton) {
        switch (glfwButton) {
            case 0:
                return MotionEvent.BUTTON_PRIMARY;
            case 1:
                return MotionEvent.BUTTON_SECONDARY;
            case 2:
                return MotionEvent.BUTTON_TERTIARY;
            default:
                return 0;
        }
    }

    private static void sendSdlMouse(int button, int action, float x, float y) {
        sendSdlMouse(button, action, x, y, false);
    }

    private static void sendSdlMouse(int button, int action, float x, float y,
                                     boolean relative) {
        if (!MinecraftGLSurface.isSdlWindowBridgeEnabled()) return;
        try {
            logSdlInputBridge();
            SDLActivity.onNativeMouse(button, action, x, y, relative);
        } catch (UnsatisfiedLinkError error) {
            Log.w("BattlyInput", "SDL input bridge is unavailable; retaining GLFW input", error);
        }
    }

    private static void logSdlInputBridge() {
        if (sSdlInputBridgeLogged) return;
        sSdlInputBridgeLogged = true;
        Log.i("BattlyInput", "Overlay controls are routed to the SDL window");
    }

    private static synchronized int updateSdlMouseButtonState(int button, boolean isDown) {
        if (button == 0) return sSdlMouseButtonState;
        if (isDown) sSdlMouseButtonState |= button;
        else sSdlMouseButtonState &= ~button;
        return sSdlMouseButtonState;
    }

    private static synchronized int getSdlMouseButtonState() {
        return sSdlMouseButtonState;
    }

    public static void resetSdlInputState() {
        int previousState;
        synchronized (CallbackBridge.class) {
            previousState = sSdlMouseButtonState;
            sSdlMouseButtonState = 0;
        }
        if (previousState != 0 && MinecraftGLSurface.isSdlWindowBridgeEnabled()) {
            sendSdlMouse(0, MotionEvent.ACTION_UP, mouseX, mouseY);
        }
    }

    public static void sendUpdateWindowSize(int w, int h) {
        nativeSendScreenSize(w, h);
    }

    public static boolean isGrabbing() {
        // Avoid going through the JNI each time.
        return isGrabbing;
    }

    // Called from JRE side
    @SuppressWarnings("unused")
    @Keep
    public static @Nullable String accessAndroidClipboard(int type, String copy) {
        switch (type) {
            case CLIPBOARD_COPY:
                MainActivity.GLOBAL_CLIPBOARD.setPrimaryClip(ClipData.newPlainText("Copy", copy));
                return null;

            case CLIPBOARD_PASTE:
                if (MainActivity.GLOBAL_CLIPBOARD.hasPrimaryClip() && MainActivity.GLOBAL_CLIPBOARD.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    return MainActivity.GLOBAL_CLIPBOARD.getPrimaryClip().getItemAt(0).getText().toString();
                } else {
                    return "";
                }

            case CLIPBOARD_OPEN:
                MainActivity.openLink(copy);
                return null;
            default: return null;
        }
    }


    public static int getCurrentMods() {
        int currMods = 0;
        if (holdingAlt) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_ALT;
        } if (holdingCapslock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CAPS_LOCK;
        } if (holdingCtrl) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CONTROL;
        } if (holdingNumlock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_NUM_LOCK;
        } if (holdingShift) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_SHIFT;
        }
        return currMods;
    }

    public static void setModifiers(int keyCode, boolean isDown){
        switch (keyCode){
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT:
                CallbackBridge.holdingShift = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL:
                CallbackBridge.holdingCtrl = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT:
                CallbackBridge.holdingAlt = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK:
                CallbackBridge.holdingCapslock = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK:
                CallbackBridge.holdingNumlock = isDown;
        }
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    @Keep
    private static void onDirectInputEnable() {
        Log.i("CallbackBridge", "onDirectInputEnable()");
        DirectGamepadEnableHandler enableHandler = Tools.getWeakReference(sDirectGamepadEnableHandler);
        if(enableHandler != null) enableHandler.onDirectGamepadEnabled();
        sGamepadDirectInput = true;
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    @Keep
    private static void onGrabStateChanged(final boolean grabbing) {
        setGrabState(grabbing);
    }

    /**
     * SDL-backed LWJGL versions bypass the GLFW shim when Minecraft changes
     * relative mouse mode. Mirror SDL's state into the existing grab listeners
     * so touchscreen input switches between GUI and camera handling.
     */
    public static void setSdlGrabState(final boolean grabbing) {
        setGrabState(grabbing);
    }

    private static void setGrabState(final boolean grabbing) {
        if (isGrabbing != grabbing) {
            resetSdlInputState();
        }
        isGrabbing = grabbing;
        sChoreographer.postFrameCallbackDelayed((time) -> {
            // If the grab re-changed, skip notify process
            if(isGrabbing != grabbing) return;

            Log.i("BattlyInput", "Grab changed: " + grabbing
                    + (MinecraftGLSurface.isSdlWindowBridgeEnabled() ? " (SDL)" : " (GLFW)"));
            synchronized (grabListeners) {
                for (GrabListener g : grabListeners) g.onGrabState(grabbing);
            }

        }, 16);

    }
    public static void addGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            listener.onGrabState(isGrabbing);
            grabListeners.add(listener);
        }
    }
    public static void removeGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            grabListeners.remove(listener);
        }
    }

    public static FloatBuffer createGamepadAxisBuffer() {
        ByteBuffer axisByteBuffer = nativeCreateGamepadAxisBuffer();
        // NOTE: hardcoded order (also in jre_lwjgl3glfw CallbackBridge)
        return axisByteBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    }

    public static void setDirectGamepadEnableHandler(DirectGamepadEnableHandler h) {
        sDirectGamepadEnableHandler = new WeakReference<>(h);
    }

    @Keep @CriticalNative public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);

    @Keep @CriticalNative private static native boolean nativeSendChar(char codepoint);
    // GLFW: GLFWCharModsCallback deprecated, but is Minecraft still use?
    @Keep @CriticalNative private static native boolean nativeSendCharMods(char codepoint, int mods);
    @Keep @CriticalNative private static native void nativeSendKey(int key, int scancode, int action, int mods);
    // private static native void nativeSendCursorEnter(int entered);
    @Keep @CriticalNative private static native void nativeSendCursorPos(float x, float y);
    @Keep @CriticalNative private static native void nativeSendMouseButton(int button, int action, int mods);
    @Keep @CriticalNative private static native void nativeSendScroll(double xoffset, double yoffset);
    @Keep @CriticalNative private static native void nativeSendScreenSize(int width, int height);
    public static native void nativeSetWindowAttrib(int attrib, int value);
    private static native ByteBuffer nativeCreateGamepadButtonBuffer();
    private static native ByteBuffer nativeCreateGamepadAxisBuffer();
    static {
        System.loadLibrary("pojavexec");
        sGamepadButtonBuffer = nativeCreateGamepadButtonBuffer();
        sGamepadAxisBuffer = createGamepadAxisBuffer();
    }
}

