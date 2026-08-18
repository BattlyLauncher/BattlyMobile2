package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.view.MotionEvent;

/** Stateless input classification used by the controller setup assistant. */
final class ControllerSetupCapture {
    private static final float ACTIVATION_THRESHOLD = .62f;
    private static final float RELEASE_THRESHOLD = .25f;

    private ControllerSetupCapture() {
    }

    static int expectedHatAxis(int step, float hatX, float hatY) {
        switch (step) {
            case 12:
                return hatY < -ACTIVATION_THRESHOLD ? MotionEvent.AXIS_HAT_Y : -1;
            case 13:
                return hatY > ACTIVATION_THRESHOLD ? MotionEvent.AXIS_HAT_Y : -1;
            case 14:
                return hatX < -ACTIVATION_THRESHOLD ? MotionEvent.AXIS_HAT_X : -1;
            case 15:
                return hatX > ACTIVATION_THRESHOLD ? MotionEvent.AXIS_HAT_X : -1;
            default:
                return -1;
        }
    }

    static int expectedTriggerAxis(int step, float leftTrigger, float rightTrigger,
                                   float brake, float gas) {
        if (step == 8) {
            if (leftTrigger > ACTIVATION_THRESHOLD) return MotionEvent.AXIS_LTRIGGER;
            if (brake > ACTIVATION_THRESHOLD) return MotionEvent.AXIS_BRAKE;
        } else if (step == 9) {
            if (rightTrigger > ACTIVATION_THRESHOLD) return MotionEvent.AXIS_RTRIGGER;
            if (gas > ACTIVATION_THRESHOLD) return MotionEvent.AXIS_GAS;
        }
        return -1;
    }

    static boolean areAxesNeutral(float[] values) {
        for (float value : values) {
            if (Math.abs(value) > RELEASE_THRESHOLD) return false;
        }
        return true;
    }

    static boolean isAxisReleased(float value, boolean trigger) {
        return trigger ? value <= RELEASE_THRESHOLD : Math.abs(value) <= RELEASE_THRESHOLD;
    }

    static int strongestAvailableAxis(int[] axes, float[] values, int[] excludedAxes) {
        if (axes == null || values == null || axes.length != values.length) return -1;
        float strongest = ACTIVATION_THRESHOLD;
        int result = -1;
        for (int i = 0; i < axes.length; i++) {
            if (contains(excludedAxes, axes[i])) continue;
            float value = Math.abs(values[i]);
            if (value <= strongest) continue;
            strongest = value;
            result = axes[i];
        }
        return result;
    }

    private static boolean contains(int[] values, int candidate) {
        if (values == null) return false;
        for (int value : values) {
            if (value == candidate) return true;
        }
        return false;
    }
}
