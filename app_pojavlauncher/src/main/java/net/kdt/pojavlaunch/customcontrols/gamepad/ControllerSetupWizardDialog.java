package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fr.spse.gamepad_remapper.Remapper;

/** Battly-owned controller setup flow with controller-specific labels and visualization. */
public final class ControllerSetupWizardDialog extends Dialog {
    public interface Listener {
        void onCompleted(Remapper remapper, InputDevice device, ControllerTypeResolver.Style style);
        void onCancelled();
    }

    private static final int[] CANONICAL_KEYS = {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
    };
    private static final int[] CANONICAL_AXES = {
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ
    };
    private static final String[] HIGHLIGHTS = {
            "faceBottom", "faceRight", "faceLeft", "faceTop", "start", "select",
            "leftShoulder", "rightShoulder", "leftTrigger", "rightTrigger",
            "leftStick", "rightStick", "dpadUp", "dpadDown", "dpadLeft", "dpadRight",
            "leftStick", "leftStick", "rightStick", "rightStick"
    };

    private final Listener mListener;
    private final ControllerTypeResolver.Style mRequestedStyle;
    private final Map<Integer, Integer> mKeyMap = new LinkedHashMap<>();
    private final Map<Integer, Integer> mMotionMap = new LinkedHashMap<>();
    private ControllerThreeView mVisual;
    private TextView mDeviceText;
    private TextView mStepText;
    private TextView mInstructionText;
    private ProgressBar mProgress;
    private InputDevice mDevice;
    private ControllerTypeResolver.Style mStyle;
    private int mStep;
    private boolean mFinished;
    private int mReleaseAxis = -1;
    private boolean mReleaseAxisIsTrigger;

    public ControllerSetupWizardDialog(Context context, InputDevice device,
                                       ControllerTypeResolver.Style requestedStyle, Listener listener) {
        super(context, R.style.BattlyFullscreenDialog);
        mDevice = device;
        mRequestedStyle = requestedStyle;
        mStyle = ControllerTypeResolver.resolve(requestedStyle, device);
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureFullscreenWindow();
        setContentView(R.layout.dialog_controller_setup_wizard);
        setCanceledOnTouchOutside(false);
        mVisual = findViewById(R.id.controller_setup_visual);
        mDeviceText = findViewById(R.id.controller_setup_device);
        mStepText = findViewById(R.id.controller_setup_step);
        mInstructionText = findViewById(R.id.controller_setup_instruction);
        mProgress = findViewById(R.id.controller_setup_progress);
        findViewById(R.id.controller_setup_cancel).setOnClickListener(v -> cancelSetup());
        findViewById(R.id.controller_setup_skip).setOnClickListener(v -> skipStep());
        mVisual.setControllerDevice(mDevice);
        mVisual.setControllerStyle(mStyle);
        updateStep();
        Window window = getWindow();
        if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);
    }

    @Override
    public void show() {
        Window window = getWindow();
        if (window != null) window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        super.show();
        hideSystemBars();
        if (window != null) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        configureFullscreenWindow();
        sizeCenteredPanel();
        View panel = findViewById(R.id.controller_setup_panel);
        if (panel != null) panel.post(this::sizeCenteredPanel);
        hideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void sizeCenteredPanel() {
        View panel = findViewById(R.id.controller_setup_panel);
        if (panel == null) return;
        View root = (View) panel.getParent();
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        int windowWidth = root.getWidth() > 0 ? root.getWidth() : metrics.widthPixels;
        int windowHeight = root.getHeight() > 0 ? root.getHeight() : metrics.heightPixels;
        int availableWidth = Math.max(1,
                windowWidth - root.getPaddingLeft() - root.getPaddingRight());
        int availableHeight = Math.max(1,
                windowHeight - root.getPaddingTop() - root.getPaddingBottom());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                availableWidth,
                availableHeight,
                Gravity.CENTER);
        panel.setLayoutParams(params);
        boolean compact = availableHeight < dp(390);
        panel.setPadding(dp(compact ? 8 : 12), dp(compact ? 7 : 10),
                dp(compact ? 8 : 12), dp(compact ? 7 : 10));
        applyCompactTypography(compact);
    }

    private void applyCompactTypography(boolean compact) {
        setTextSize(R.id.controller_setup_title, compact ? 15 : 21);
        setTextSize(R.id.controller_setup_device, compact ? 9 : 11);
        setTextSize(R.id.controller_setup_step, compact ? 14 : 18);
        setTextSize(R.id.controller_setup_instruction, compact ? 9 : 12);
        setTextSize(R.id.controller_setup_note, compact ? 8 : 10);
    }

    private void setTextSize(int viewId, int sp) {
        TextView view = findViewById(viewId);
        if (view != null) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private void hideSystemBars() {
        Window window = getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private void configureFullscreenWindow() {
        Window window = getWindow();
        if (window == null) return;
        window.setGravity(Gravity.FILL);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = WindowManager.LayoutParams.MATCH_PARENT;
        attributes.height = WindowManager.LayoutParams.MATCH_PARENT;
        attributes.gravity = Gravity.FILL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(attributes);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        hideSystemBars();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
        if (!Gamepad.isGamepadEvent(event)) return super.dispatchKeyEvent(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return true;
        attachDevice(event.getDevice());
        if (mStep < CANONICAL_KEYS.length) {
            mKeyMap.put(event.getKeyCode(), CANONICAL_KEYS[mStep]);
            advance();
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!Gamepad.isGamepadEvent(event)) return super.dispatchGenericMotionEvent(event);
        attachDevice(event.getDevice());
        if (waitForAxisRelease(event)) return true;
        if (mStep < CANONICAL_KEYS.length) {
            int triggerAxis = ControllerSetupCapture.expectedTriggerAxis(mStep,
                    event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_BRAKE),
                    event.getAxisValue(MotionEvent.AXIS_GAS));
            if (triggerAxis != -1) {
                int target = mStep == 8 ? MotionEvent.AXIS_LTRIGGER : MotionEvent.AXIS_RTRIGGER;
                captureMotionAxis(triggerAxis, target, true);
                return true;
            }
            int hatAxis = ControllerSetupCapture.expectedHatAxis(mStep,
                    event.getAxisValue(MotionEvent.AXIS_HAT_X),
                    event.getAxisValue(MotionEvent.AXIS_HAT_Y));
            if (hatAxis != -1) captureMotionAxis(hatAxis, hatAxis, false);
            return true;
        }
        int axis = strongestAxis(event);
        if (axis == -1) return true;
        captureMotionAxis(axis, CANONICAL_AXES[mStep - CANONICAL_KEYS.length], false);
        return true;
    }

    private boolean waitForAxisRelease(MotionEvent event) {
        if (mReleaseAxis == -1) return false;
        if (ControllerSetupCapture.isAxisReleased(
                event.getAxisValue(mReleaseAxis), mReleaseAxisIsTrigger)) {
            mReleaseAxis = -1;
            mReleaseAxisIsTrigger = false;
        }
        return true;
    }

    private void captureMotionAxis(int sourceAxis, int targetAxis, boolean trigger) {
        // A physical axis can only have one purpose. This also replaces an
        // earlier HAT/D-pad assignment when a controller exposes a stick as HAT.
        mMotionMap.remove(sourceAxis);
        mMotionMap.put(sourceAxis, targetAxis);
        mReleaseAxis = sourceAxis;
        mReleaseAxisIsTrigger = trigger;
        advance();
    }

    private void attachDevice(InputDevice device) {
        if (device == null || mDevice != null) return;
        mDevice = device;
        mStyle = ControllerTypeResolver.resolve(mRequestedStyle, device);
        mVisual.setControllerDevice(device);
        mVisual.setControllerStyle(mStyle);
    }

    private int strongestAxis(MotionEvent event) {
        int[] candidates = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z,
                MotionEvent.AXIS_RZ, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
        float[] values = new float[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            values[i] = event.getAxisValue(candidates[i]);
        }
        return ControllerSetupCapture.strongestAvailableAxis(
                candidates, values, capturedStickAxes());
    }

    private int[] capturedStickAxes() {
        List<Integer> axes = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : mMotionMap.entrySet()) {
            int target = entry.getValue();
            if (target == MotionEvent.AXIS_X || target == MotionEvent.AXIS_Y
                    || target == MotionEvent.AXIS_Z || target == MotionEvent.AXIS_RZ) {
                axes.add(entry.getKey());
            }
        }
        int[] result = new int[axes.size()];
        for (int i = 0; i < axes.size(); i++) result[i] = axes.get(i);
        return result;
    }

    private void skipStep() {
        if (mDevice == null) return;
        advance();
    }

    private void advance() {
        mStep++;
        if (mStep >= CANONICAL_KEYS.length + CANONICAL_AXES.length) {
            complete();
        } else {
            updateStep();
        }
    }

    private void updateStep() {
        boolean waiting = mDevice == null;
        mProgress.setProgress(mStep);
        mDeviceText.setText(waiting
                ? getContext().getString(R.string.controller_setup_connect_prompt)
                : getContext().getString(R.string.controller_device_detected, mDevice.getName()));
        if (waiting) {
            mStepText.setText(R.string.controller_setup_waiting);
            mInstructionText.setText(R.string.controller_setup_waiting_description);
            mVisual.highlightControl("");
            return;
        }
        mStepText.setText(getStepName(mStep));
        mInstructionText.setText(mStep < CANONICAL_KEYS.length
                ? R.string.controller_setup_press_button : R.string.controller_setup_move_axis);
        mVisual.highlightControl(HIGHLIGHTS[mStep]);
    }

    private String getStepName(int step) {
        if (step >= CANONICAL_KEYS.length) {
            String[] axes = {
                    getContext().getString(R.string.controller_axis_left_horizontal),
                    getContext().getString(R.string.controller_axis_left_vertical),
                    getContext().getString(R.string.controller_axis_right_horizontal),
                    getContext().getString(R.string.controller_axis_right_vertical)
            };
            return axes[step - CANONICAL_KEYS.length];
        }
        String[][] labels = {
                {"A", "B", "X", "Y", "Menu", "View", "LB", "RB", "LT", "RT", "L3", "R3"},
                {
                        getContext().getString(R.string.controller_button_cross),
                        getContext().getString(R.string.controller_button_circle),
                        getContext().getString(R.string.controller_button_square),
                        getContext().getString(R.string.controller_button_triangle),
                        "Options", "Create", "L1", "R1", "L2", "R2", "L3", "R3"
                },
                {"B", "A", "Y", "X", "+", "-", "L", "R", "ZL", "ZR", "Stick L", "Stick R"}
        };
        if (step >= 12) {
            String[] dpad = {
                    getContext().getString(R.string.controller_dpad_up),
                    getContext().getString(R.string.controller_dpad_down),
                    getContext().getString(R.string.controller_dpad_left),
                    getContext().getString(R.string.controller_dpad_right)
            };
            return dpad[step - 12];
        }
        int styleIndex = mStyle == ControllerTypeResolver.Style.PLAYSTATION ? 1
                : mStyle == ControllerTypeResolver.Style.SWITCH ? 2 : 0;
        return labels[styleIndex][step];
    }

    private void complete() {
        if (mFinished || mDevice == null) return;
        mFinished = true;
        mVisual.highlightControl("");
        // Persist the same physical-to-canonical map in both stores. The runtime Remapper consumes
        // this map and Battly keeps its copy for migrations and controller diagnostics.
        ControllerAxisPreferences.save(getContext(), mDevice.getDescriptor(), mMotionMap);
        Remapper remapper = new Remapper(mKeyMap, mMotionMap);
        Log.i("ControllerSetup", "Saved controller mapping for " + mDevice.getName()
                + ": keys=" + mKeyMap.size() + ", axes=" + mMotionMap);
        dismiss();
        mListener.onCompleted(remapper, mDevice, mStyle);
    }

    private void cancelSetup() {
        if (mFinished) return;
        mFinished = true;
        dismiss();
        mListener.onCancelled();
    }
}
