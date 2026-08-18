package net.kdt.pojavlaunch.customcontrols.buttons;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.widget.AppCompatImageView;

import com.bumptech.glide.Glide;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDeviceImageManager;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.gamepad.ControllerInputVisualizer;
import net.kdt.pojavlaunch.customcontrols.handleview.EditControlSideDialog;

@SuppressLint("ViewConstructor")
public final class ControlDeviceImage extends AppCompatImageView
        implements ControlInterface, InputManager.InputDeviceListener,
        ControllerInputVisualizer.Listener {
    private final ControlLayout controlLayout;
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ControlData properties;
    private InputManager inputManager;
    private ControllerInputVisualizer.State inputState;

    public ControlDeviceImage(ControlLayout layout, ControlData properties) {
        super(layout.getContext());
        controlLayout = layout;
        setScaleType(ScaleType.FIT_CENTER);
        setAdjustViewBounds(false);
        highlightPaint.setColor(Color.rgb(91, 226, 190));
        highlightPaint.setStyle(Paint.Style.FILL);
        outlinePaint.setColor(Color.WHITE);
        outlinePaint.setStyle(Paint.Style.STROKE);
        setContentDescription(getContext().getString(R.string.customctrl_device_image_title));
        setProperties(preProcessProperties(properties, layout));

        injectProperties();
        if (layout.getModifiable()) injectTouchEventBehavior();
        injectLayoutParamBehavior();
        injectGrabListenerBehavior();
        setClickable(layout.getModifiable());
        setFocusable(false);
    }

    @Override public View getControlView() { return this; }
    @Override public ControlData getProperties() { return properties; }

    @Override
    public void setProperties(ControlData properties, boolean changePos) {
        this.properties = properties;
        ControlInterface.super.setProperties(properties, changePos);
        setAlpha(properties.opacity);
        ControlDeviceImageManager.loadInto(this, properties.imageSource, properties.imagePath);
    }

    @Override public void setBackground() { setBackgroundDrawable(null); }
    @Override public void sendKeyPresses(boolean isDown) { }

    @Override
    public void loadEditValues(EditControlSideDialog editControlDialog) {
        editControlDialog.loadDeviceImageValues(properties);
    }

    @Override
    public void removeButton() {
        controlLayout.getLayout().mControlDataList.remove(properties);
        controlLayout.removeView(this);
    }

    @Override
    public void cloneButton() {
        ControlData clone = new ControlData(properties);
        clone.dynamicX = "0.5 * ${screen_width}";
        clone.dynamicY = "0.5 * ${screen_height}";
        controlLayout.addControlButton(clone);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return controlLayout.getModifiable() && super.onTouchEvent(event);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        inputManager = (InputManager) getContext().getSystemService(Context.INPUT_SERVICE);
        if (inputManager != null) inputManager.registerInputDeviceListener(this, null);
        ControllerInputVisualizer.register(this);
        refreshAutomaticImage();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        ControllerInputVisualizer.unregister(this);
        Glide.with(this).clear(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onControllerStateChanged(ControllerInputVisualizer.State state) {
        inputState = state;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ControllerInputVisualizer.State state = inputState;
        if (state == null || getDrawable() == null) return;

        String source = ControlDeviceImageManager.resolveDisplaySource(properties.imageSource);
        if (ControlDeviceImageManager.SOURCE_CUSTOM.equals(source)
                || ControlDeviceImageManager.SOURCE_KEYBOARD_MOUSE.equals(source)) return;

        RectF image = imageBounds();
        if (image.width() <= 0f || image.height() <= 0f) return;
        float markerRadius = Math.max(5f, Math.min(image.width(), image.height()) * 0.035f);
        outlinePaint.setStrokeWidth(Math.max(1.5f, markerRadius * 0.16f));

        if (ControlDeviceImageManager.SOURCE_DUALSENSE.equals(source)) {
            drawPlayStation(canvas, image, state, markerRadius, true);
        } else if (ControlDeviceImageManager.SOURCE_DUALSHOCK4.equals(source)) {
            drawPlayStation(canvas, image, state, markerRadius, false);
        } else if (ControlDeviceImageManager.SOURCE_SWITCH.equals(source)) {
            drawSwitch(canvas, image, state, markerRadius);
        } else {
            drawXbox(canvas, image, state, markerRadius);
        }
    }

    private RectF imageBounds() {
        int drawableWidth = Math.max(1, getDrawable().getIntrinsicWidth());
        int drawableHeight = Math.max(1, getDrawable().getIntrinsicHeight());
        float availableWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        float availableHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        float scale = Math.min(availableWidth / drawableWidth, availableHeight / drawableHeight);
        float width = drawableWidth * scale;
        float height = drawableHeight * scale;
        float left = getPaddingLeft() + (availableWidth - width) * 0.5f;
        float top = getPaddingTop() + (availableHeight - height) * 0.5f;
        return new RectF(left, top, left + width, top + height);
    }

    private void drawXbox(Canvas canvas, RectF image, ControllerInputVisualizer.State state, float r) {
        drawFaceButtons(canvas, image, state, r,
                point(0.73f, 0.30f), point(0.80f, 0.23f),
                point(0.67f, 0.21f), point(0.73f, 0.12f));
        drawDpad(canvas, image, state, r, 0.36f, 0.42f, 0.045f);
        drawStick(canvas, image, state, r, true, 0.25f, 0.24f);
        drawStick(canvas, image, state, r, false, 0.61f, 0.39f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.SELECT, 0.42f, 0.20f, r * 0.65f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.START, 0.56f, 0.20f, r * 0.65f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.GUIDE, 0.49f, 0.09f, r);
        shoulders(canvas, image, state, r, 0.25f, 0.75f, 0.025f);
    }

    private void drawPlayStation(Canvas canvas, RectF image, ControllerInputVisualizer.State state,
                                 float r, boolean dualSense) {
        if (dualSense) {
            drawFaceButtons(canvas, image, state, r,
                    point(0.70f, 0.41f), point(0.77f, 0.31f),
                    point(0.64f, 0.31f), point(0.70f, 0.22f));
            drawDpad(canvas, image, state, r, 0.17f, 0.32f, 0.055f);
            drawStick(canvas, image, state, r, true, 0.32f, 0.49f);
            drawStick(canvas, image, state, r, false, 0.58f, 0.49f);
            marker(canvas, image, state, ControllerInputVisualizer.Control.SELECT, 0.24f, 0.18f, r * 0.6f);
            marker(canvas, image, state, ControllerInputVisualizer.Control.START, 0.64f, 0.18f, r * 0.6f);
            marker(canvas, image, state, ControllerInputVisualizer.Control.GUIDE, 0.45f, 0.51f, r * 0.7f);
            shoulders(canvas, image, state, r, 0.20f, 0.72f, 0.07f);
        } else {
            drawFaceButtons(canvas, image, state, r,
                    point(0.79f, 0.47f), point(0.86f, 0.35f),
                    point(0.72f, 0.35f), point(0.79f, 0.24f));
            drawDpad(canvas, image, state, r, 0.18f, 0.31f, 0.055f);
            drawStick(canvas, image, state, r, true, 0.35f, 0.57f);
            drawStick(canvas, image, state, r, false, 0.61f, 0.57f);
            marker(canvas, image, state, ControllerInputVisualizer.Control.SELECT, 0.30f, 0.16f, r * 0.55f);
            marker(canvas, image, state, ControllerInputVisualizer.Control.START, 0.68f, 0.16f, r * 0.55f);
            marker(canvas, image, state, ControllerInputVisualizer.Control.GUIDE, 0.49f, 0.65f, r * 0.65f);
            shoulders(canvas, image, state, r, 0.25f, 0.72f, 0.04f);
        }
    }

    private void drawSwitch(Canvas canvas, RectF image, ControllerInputVisualizer.State state, float r) {
        drawFaceButtons(canvas, image, state, r,
                point(0.84f, 0.34f), point(0.76f, 0.43f),
                point(0.76f, 0.25f), point(0.68f, 0.34f));
        drawDpad(canvas, image, state, r, 0.36f, 0.53f, 0.055f);
        drawStick(canvas, image, state, r, true, 0.22f, 0.28f);
        drawStick(canvas, image, state, r, false, 0.61f, 0.51f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.SELECT, 0.38f, 0.20f, r * 0.65f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.START, 0.63f, 0.20f, r * 0.65f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.GUIDE, 0.56f, 0.33f, r * 0.7f);
        shoulders(canvas, image, state, r, 0.22f, 0.78f, 0.035f);
    }

    private void drawFaceButtons(Canvas canvas, RectF image, ControllerInputVisualizer.State state,
                                 float r, float[] a, float[] b, float[] x, float[] y) {
        marker(canvas, image, state, ControllerInputVisualizer.Control.A, a[0], a[1], r);
        marker(canvas, image, state, ControllerInputVisualizer.Control.B, b[0], b[1], r);
        marker(canvas, image, state, ControllerInputVisualizer.Control.X, x[0], x[1], r);
        marker(canvas, image, state, ControllerInputVisualizer.Control.Y, y[0], y[1], r);
    }

    private void drawDpad(Canvas canvas, RectF image, ControllerInputVisualizer.State state,
                          float r, float centerX, float centerY, float offset) {
        marker(canvas, image, state, ControllerInputVisualizer.Control.DPAD_UP,
                centerX, centerY - offset, r * 0.72f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.DPAD_DOWN,
                centerX, centerY + offset, r * 0.72f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.DPAD_LEFT,
                centerX - offset, centerY, r * 0.72f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.DPAD_RIGHT,
                centerX + offset, centerY, r * 0.72f);
    }

    private void drawStick(Canvas canvas, RectF image, ControllerInputVisualizer.State state,
                           float r, boolean left, float centerX, float centerY) {
        ControllerInputVisualizer.Control click = left
                ? ControllerInputVisualizer.Control.LEFT_STICK
                : ControllerInputVisualizer.Control.RIGHT_STICK;
        float x = left ? state.leftX : state.rightX;
        float y = left ? state.leftY : state.rightY;
        if (!state.isPressed(click) && Math.abs(x) < 0.01f && Math.abs(y) < 0.01f) return;
        float px = image.left + centerX * image.width();
        float py = image.top + centerY * image.height();
        drawPulse(canvas, px, py, r * 1.25f, 115);
        float travel = r * 0.8f;
        drawPulse(canvas, px + x * travel, py + y * travel, r * 0.55f, 220);
    }

    private void shoulders(Canvas canvas, RectF image, ControllerInputVisualizer.State state,
                           float r, float leftX, float rightX, float y) {
        marker(canvas, image, state, ControllerInputVisualizer.Control.L1, leftX, y, r * 0.9f);
        marker(canvas, image, state, ControllerInputVisualizer.Control.R1, rightX, y, r * 0.9f);
        trigger(canvas, image, state, ControllerInputVisualizer.Control.L2,
                leftX - 0.035f, Math.max(0.015f, y - 0.025f), r, state.leftTrigger);
        trigger(canvas, image, state, ControllerInputVisualizer.Control.R2,
                rightX + 0.035f, Math.max(0.015f, y - 0.025f), r, state.rightTrigger);
    }

    private void trigger(Canvas canvas, RectF image, ControllerInputVisualizer.State state,
                         ControllerInputVisualizer.Control control, float x, float y,
                         float r, float intensity) {
        if (!state.isPressed(control)) return;
        drawPulse(canvas, image.left + x * image.width(), image.top + y * image.height(),
                r, 100 + Math.round(155f * Math.max(0.25f, intensity)));
    }

    private void marker(Canvas canvas, RectF image, ControllerInputVisualizer.State state,
                        ControllerInputVisualizer.Control control, float x, float y, float r) {
        if (!state.isPressed(control)) return;
        drawPulse(canvas, image.left + x * image.width(), image.top + y * image.height(), r, 205);
    }

    private void drawPulse(Canvas canvas, float x, float y, float radius, int alpha) {
        highlightPaint.setAlpha(Math.max(0, Math.min(255, alpha)));
        outlinePaint.setAlpha(Math.max(120, alpha));
        canvas.drawCircle(x, y, radius, highlightPaint);
        canvas.drawCircle(x, y, radius, outlinePaint);
    }

    private static float[] point(float x, float y) {
        return new float[]{x, y};
    }

    private void refreshAutomaticImage() {
        if (ControlDeviceImageManager.SOURCE_AUTO.equals(properties.imageSource)) {
            ControlDeviceImageManager.loadInto(this, properties.imageSource, properties.imagePath);
        }
    }

    @Override public void onInputDeviceAdded(int deviceId) { refreshAutomaticImage(); }
    @Override public void onInputDeviceRemoved(int deviceId) { refreshAutomaticImage(); }
    @Override public void onInputDeviceChanged(int deviceId) { refreshAutomaticImage(); }
}
