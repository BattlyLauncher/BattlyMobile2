package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.WeakHashMap;

/** Publishes a read-only copy of physical controller input for visual overlays. */
public final class ControllerInputVisualizer {
    public enum Control {
        A, B, X, Y,
        L1, R1, L2, R2,
        LEFT_STICK, RIGHT_STICK,
        START, SELECT, GUIDE,
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT
    }

    public interface Listener {
        void onControllerStateChanged(State state);
    }

    public static final class State {
        private final EnumSet<Control> pressed;
        public final float leftX;
        public final float leftY;
        public final float rightX;
        public final float rightY;
        public final float leftTrigger;
        public final float rightTrigger;

        private State(EnumSet<Control> pressed, float leftX, float leftY,
                      float rightX, float rightY, float leftTrigger, float rightTrigger) {
            this.pressed = pressed.clone();
            this.leftX = clampAxis(leftX);
            this.leftY = clampAxis(leftY);
            this.rightX = clampAxis(rightX);
            this.rightY = clampAxis(rightY);
            this.leftTrigger = clampTrigger(leftTrigger);
            this.rightTrigger = clampTrigger(rightTrigger);
        }

        public boolean isPressed(Control control) {
            return pressed.contains(control);
        }

        static State forAnalogValues(float leftX, float leftY, float rightX, float rightY,
                                     float hatX, float hatY, float leftTrigger, float rightTrigger) {
            EnumSet<Control> controls = EnumSet.noneOf(Control.class);
            applyAnalogControls(controls, hatX, hatY, leftTrigger, rightTrigger);
            return new State(controls, leftX, leftY, rightX, rightY, leftTrigger, rightTrigger);
        }
    }

    private static final float ACTIVE_THRESHOLD = 0.18f;
    private static final WeakHashMap<Listener, Boolean> LISTENERS = new WeakHashMap<>();
    private static final EnumSet<Control> DIGITAL_PRESSED = EnumSet.noneOf(Control.class);
    private static int activeDeviceId = -1;
    private static float currentHatX;
    private static float currentHatY;
    private static State currentState = new State(DIGITAL_PRESSED, 0f, 0f, 0f, 0f, 0f, 0f);

    private ControllerInputVisualizer() {}

    public static synchronized void register(Listener listener) {
        LISTENERS.put(listener, Boolean.TRUE);
        listener.onControllerStateChanged(currentState);
    }

    public static synchronized void unregister(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void onKeyEvent(KeyEvent event) {
        if (event == null || !Gamepad.isGamepadEvent(event)) return;
        Control control = controlForKeyCode(event.getKeyCode());
        if (control == null) return;

        synchronized (ControllerInputVisualizer.class) {
            prepareDevice(event.getDeviceId());
            if (event.getAction() == KeyEvent.ACTION_DOWN) DIGITAL_PRESSED.add(control);
            else if (event.getAction() == KeyEvent.ACTION_UP) DIGITAL_PRESSED.remove(control);
            else return;
            currentState = buildState(currentState.leftX, currentState.leftY,
                    currentState.rightX, currentState.rightY, currentHatX, currentHatY,
                    currentState.leftTrigger, currentState.rightTrigger);
        }
        publish();
    }

    public static void onMotionEvent(MotionEvent event) {
        if (event == null || !Gamepad.isGamepadEvent(event)) return;
        synchronized (ControllerInputVisualizer.class) {
            prepareDevice(event.getDeviceId());
            float leftX = axis(event, MotionEvent.AXIS_X);
            float leftY = axis(event, MotionEvent.AXIS_Y);
            float rightX = firstAxis(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX);
            float rightY = firstAxis(event, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY);
            float hatX = axis(event, MotionEvent.AXIS_HAT_X);
            float hatY = axis(event, MotionEvent.AXIS_HAT_Y);
            float leftTrigger = firstPositiveAxis(event,
                    MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE);
            float rightTrigger = firstPositiveAxis(event,
                    MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS);

            currentHatX = hatX;
            currentHatY = hatY;
            currentState = buildState(leftX, leftY, rightX, rightY,
                    hatX, hatY, leftTrigger, rightTrigger);
        }
        publish();
    }

    @Nullable
    static Control controlForKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return Control.A;
            case KeyEvent.KEYCODE_BUTTON_B: return Control.B;
            case KeyEvent.KEYCODE_BUTTON_X: return Control.X;
            case KeyEvent.KEYCODE_BUTTON_Y: return Control.Y;
            case KeyEvent.KEYCODE_BUTTON_L1: return Control.L1;
            case KeyEvent.KEYCODE_BUTTON_R1: return Control.R1;
            case KeyEvent.KEYCODE_BUTTON_L2: return Control.L2;
            case KeyEvent.KEYCODE_BUTTON_R2: return Control.R2;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return Control.LEFT_STICK;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return Control.RIGHT_STICK;
            case KeyEvent.KEYCODE_BUTTON_START: return Control.START;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return Control.SELECT;
            case KeyEvent.KEYCODE_BUTTON_MODE: return Control.GUIDE;
            case KeyEvent.KEYCODE_DPAD_UP: return Control.DPAD_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return Control.DPAD_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return Control.DPAD_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return Control.DPAD_RIGHT;
            default: return null;
        }
    }

    private static synchronized void prepareDevice(int deviceId) {
        if (deviceId == activeDeviceId) return;
        activeDeviceId = deviceId;
        DIGITAL_PRESSED.clear();
        currentHatX = 0f;
        currentHatY = 0f;
        currentState = new State(DIGITAL_PRESSED, 0f, 0f, 0f, 0f, 0f, 0f);
    }

    private static State buildState(float leftX, float leftY, float rightX, float rightY,
                                    float hatX, float hatY,
                                    float leftTrigger, float rightTrigger) {
        EnumSet<Control> pressed = DIGITAL_PRESSED.clone();
        applyAnalogControls(pressed, hatX, hatY, leftTrigger, rightTrigger);
        return new State(pressed, leftX, leftY, rightX, rightY,
                leftTrigger, rightTrigger);
    }

    private static void applyAnalogControls(EnumSet<Control> controls, float hatX, float hatY,
                                            float leftTrigger, float rightTrigger) {
        if (hatX < -ACTIVE_THRESHOLD) controls.add(Control.DPAD_LEFT);
        if (hatX > ACTIVE_THRESHOLD) controls.add(Control.DPAD_RIGHT);
        if (hatY < -ACTIVE_THRESHOLD) controls.add(Control.DPAD_UP);
        if (hatY > ACTIVE_THRESHOLD) controls.add(Control.DPAD_DOWN);
        if (leftTrigger > ACTIVE_THRESHOLD) controls.add(Control.L2);
        if (rightTrigger > ACTIVE_THRESHOLD) controls.add(Control.R2);
    }

    private static float axis(MotionEvent event, int axis) {
        return applyDeadzone(event.getAxisValue(axis));
    }

    private static float firstAxis(MotionEvent event, int primary, int fallback) {
        float value = axis(event, primary);
        return value != 0f ? value : axis(event, fallback);
    }

    private static float firstPositiveAxis(MotionEvent event, int primary, int fallback) {
        return Math.max(normalizeTrigger(event.getAxisValue(primary)),
                normalizeTrigger(event.getAxisValue(fallback)));
    }

    private static float applyDeadzone(float value) {
        return Math.abs(value) < ACTIVE_THRESHOLD ? 0f : clampAxis(value);
    }

    private static float normalizeTrigger(float value) {
        if (value < 0f) value = (value + 1f) * 0.5f;
        return clampTrigger(value);
    }

    private static float clampAxis(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }

    private static float clampTrigger(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static void publish() {
        State snapshot;
        List<Listener> listeners;
        synchronized (ControllerInputVisualizer.class) {
            snapshot = currentState;
            listeners = new ArrayList<>(LISTENERS.keySet());
        }
        for (Listener listener : listeners) {
            if (listener != null) listener.onControllerStateChanged(snapshot);
        }
    }
}
