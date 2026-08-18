package net.kdt.pojavlaunch.customcontrols.gamepad;

/** Filters controller axes that Android exposes without a reliable hardware deadzone. */
public final class ControllerAnalogFilter {
    private static final float DEFAULT_ENTER_DEADZONE = 0.14f;
    private static final float DEFAULT_EXIT_DEADZONE = 0.10f;
    private static final float TRIGGER_PRESS_THRESHOLD = 0.65f;
    private static final float TRIGGER_RELEASE_THRESHOLD = 0.35f;

    private ControllerAnalogFilter() {
    }

    public static final class StickState {
        private final float mEnterDeadzone;
        private final float mExitDeadzone;
        private float mRawX;
        private float mRawY;
        private float mFilteredX;
        private float mFilteredY;
        private boolean mActive;

        public StickState(float deadzoneScale) {
            float scale = clamp(deadzoneScale, 0.5f, 2f);
            mEnterDeadzone = clamp(DEFAULT_ENTER_DEADZONE * scale, 0.08f, 0.32f);
            mExitDeadzone = clamp(DEFAULT_EXIT_DEADZONE * scale, 0.06f, mEnterDeadzone);
        }

        public void setX(float value) {
            mRawX = clamp(value, -1f, 1f);
            update();
        }

        public void setY(float value) {
            mRawY = clamp(value, -1f, 1f);
            update();
        }

        public float getX() {
            return mFilteredX;
        }

        public float getY() {
            return mFilteredY;
        }

        public void reset() {
            mRawX = 0f;
            mRawY = 0f;
            mFilteredX = 0f;
            mFilteredY = 0f;
            mActive = false;
        }

        private void update() {
            float magnitude = (float) Math.hypot(mRawX, mRawY);
            float threshold = mActive ? mExitDeadzone : mEnterDeadzone;
            if (magnitude <= threshold) {
                mFilteredX = 0f;
                mFilteredY = 0f;
                mActive = false;
                return;
            }

            mActive = true;
            float scaledMagnitude = clamp(
                    (magnitude - mExitDeadzone) / (1f - mExitDeadzone), 0f, 1f);
            float scale = scaledMagnitude / magnitude;
            mFilteredX = mRawX * scale;
            mFilteredY = mRawY * scale;
        }
    }

    /** Merges the digital and analog reports emitted for the same physical trigger. */
    public static final class TriggerState {
        private boolean mDigitalPressed;
        private boolean mAnalogPressed;
        private boolean mCombinedPressed;

        public boolean setDigitalPressed(boolean pressed) {
            mDigitalPressed = pressed;
            return updateCombinedState();
        }

        public boolean setAnalogValue(float value) {
            if (!mAnalogPressed && value >= TRIGGER_PRESS_THRESHOLD) {
                mAnalogPressed = true;
            } else if (mAnalogPressed && value <= TRIGGER_RELEASE_THRESHOLD) {
                mAnalogPressed = false;
            }
            return updateCombinedState();
        }

        public boolean isPressed() {
            return mCombinedPressed;
        }

        public void reset() {
            mDigitalPressed = false;
            mAnalogPressed = false;
            mCombinedPressed = false;
        }

        private boolean updateCombinedState() {
            boolean pressed = mDigitalPressed || mAnalogPressed;
            if (pressed == mCombinedPressed) return false;
            mCombinedPressed = pressed;
            return true;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
