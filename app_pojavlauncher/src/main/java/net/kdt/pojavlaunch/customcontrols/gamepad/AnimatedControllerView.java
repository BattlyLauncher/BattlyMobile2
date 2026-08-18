package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Photo-accurate controller preview with animated input hotspots. */
public class AnimatedControllerView extends View {
    public interface OnControlSelectedListener {
        void onControlSelected(int mappingPosition);
    }

    private static final String ASSET_ROOT = "controller3d/";
    private static final int HOTSPOT_COUNT = 20;

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final Matrix tiltMatrix = new Matrix();

    private ControllerTypeResolver.Style style = ControllerTypeResolver.Style.GENERIC;
    private OnControlSelectedListener listener;
    private Bitmap controllerBitmap;
    private String loadedAsset;
    private boolean dualShock;
    private int activeMapping = -1;
    private float pulse;
    private float rightX;
    private float rightY;
    private ValueAnimator pulseAnimator;

    public AnimatedControllerView(Context context) {
        this(context, null);
    }

    public AnimatedControllerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setControllerStyle(ControllerTypeResolver.Style style) {
        this.style = style == null || style == ControllerTypeResolver.Style.AUTO
                ? ControllerTypeResolver.Style.GENERIC : style;
        loadControllerAsset();
    }

    public void setControllerDevice(@Nullable InputDevice device) {
        String name = device == null ? "" : device.getName().toLowerCase(Locale.ROOT);
        boolean nextDualShock = name.contains("dualshock") || name.contains("dual shock");
        if (dualShock != nextDualShock) {
            dualShock = nextDualShock;
            loadControllerAsset();
        }
    }

    public void setOnControlSelectedListener(OnControlSelectedListener listener) {
        this.listener = listener;
    }

    public void showInput(int keyCode, float value) {
        int mapping = mappingForKeyCode(keyCode);
        if (mapping >= 0 && value > 0.5f) animateMapping(mapping);
    }

    public void showRightStick(int axis, float value) {
        if (axis == MotionEvent.AXIS_Z || axis == MotionEvent.AXIS_RX) rightX = value;
        if (axis == MotionEvent.AXIS_RZ || axis == MotionEvent.AXIS_RY) rightY = value;
        activeMapping = 14;
        pulse = Math.min(1f, Math.abs(rightX) + Math.abs(rightY));
        invalidate();
    }

    private void loadControllerAsset() {
        String asset = assetForStyle();
        if (asset.equals(loadedAsset) && controllerBitmap != null) {
            invalidate();
            return;
        }
        try (InputStream stream = getContext().getAssets().open(ASSET_ROOT + asset)) {
            Bitmap decoded = BitmapFactory.decodeStream(stream);
            if (decoded != null) {
                controllerBitmap = decoded;
                loadedAsset = asset;
            }
        } catch (IOException ignored) {
            controllerBitmap = null;
            loadedAsset = null;
        }
        invalidate();
    }

    private String assetForStyle() {
        if (style == ControllerTypeResolver.Style.PLAYSTATION) {
            return dualShock ? "dualshock4.png" : "dualsense.png";
        }
        if (style == ControllerTypeResolver.Style.SWITCH) return "switch-pro.png";
        return "xbox-series.png";
    }

    private void animateMapping(int mapping) {
        activeMapping = mapping;
        if (pulseAnimator != null) pulseAnimator.cancel();
        pulseAnimator = ValueAnimator.ofFloat(1f, 0f);
        pulseAnimator.setDuration(420);
        pulseAnimator.setInterpolator(new DecelerateInterpolator());
        pulseAnimator.addUpdateListener(animation -> {
            pulse = (float) animation.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (controllerBitmap == null) {
            loadControllerAsset();
            if (controllerBitmap == null) return;
        }

        float availableW = getWidth() * 0.96f;
        float availableH = getHeight() * 0.94f;
        float scale = Math.min(availableW / controllerBitmap.getWidth(),
                availableH / controllerBitmap.getHeight());
        float drawW = controllerBitmap.getWidth() * scale;
        float drawH = controllerBitmap.getHeight() * scale;
        float left = (getWidth() - drawW) * 0.5f;
        float top = (getHeight() - drawH) * 0.5f;
        imageRect.set(left, top, left + drawW, top + drawH);

        canvas.save();
        tiltMatrix.reset();
        tiltMatrix.setRotate(Math.max(-1.5f, Math.min(1.5f, rightX * 1.5f)),
                imageRect.centerX(), imageRect.centerY());
        canvas.concat(tiltMatrix);
        canvas.drawBitmap(controllerBitmap, null, imageRect, imagePaint);
        drawActiveHotspot(canvas);
        canvas.restore();
    }

    private void drawActiveHotspot(Canvas canvas) {
        if (activeMapping < 0 || activeMapping >= HOTSPOT_COUNT || pulse <= 0.01f) return;
        float[] hotspot = hotspots()[activeMapping];
        if (hotspot == null) return;
        float x = imageRect.left + hotspot[0] * imageRect.width();
        float y = imageRect.top + hotspot[1] * imageRect.height();
        float radius = hotspot[2] * Math.min(imageRect.width(), imageRect.height());
        if (activeMapping == 14) {
            x += rightX * radius * 0.38f;
            y += rightY * radius * 0.38f;
        }
        float animatedRadius = radius * (1f + pulse * 0.18f);
        int alpha = Math.max(45, (int) (170 * pulse));
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setShader(new RadialGradient(x, y, animatedRadius * 1.8f,
                new int[]{0xAA79F3DB, 0x5579F3DB, Color.TRANSPARENT},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, animatedRadius * 1.8f, glowPaint);
        glowPaint.setShader(null);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(Math.max(2f, imageRect.width() * 0.006f));
        glowPaint.setColor(Color.argb(alpha, 121, 243, 219));
        canvas.drawCircle(x, y, animatedRadius, glowPaint);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event);
        int mapping = hitTest(event.getX(), event.getY());
        if (mapping >= 0) {
            animateMapping(mapping);
            if (listener != null) listener.onControlSelected(mapping);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int hitTest(float x, float y) {
        if (!imageRect.contains(x, y)) return -1;
        float nx = (x - imageRect.left) / imageRect.width();
        float ny = (y - imageRect.top) / imageRect.height();
        float[][] points = hotspots();
        int closest = -1;
        float closestDistance = Float.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            float[] point = points[i];
            if (point == null) continue;
            float dx = nx - point[0];
            float dy = ny - point[1];
            float distance = dx * dx + dy * dy;
            float hitRadius = point[2] * 1.55f;
            if (distance <= hitRadius * hitRadius && distance < closestDistance) {
                closest = i;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private float[][] hotspots() {
        if (style == ControllerTypeResolver.Style.PLAYSTATION) {
            return dualShock ? DUALSHOCK_HOTSPOTS : DUALSENSE_HOTSPOTS;
        }
        if (style == ControllerTypeResolver.Style.SWITCH) return SWITCH_HOTSPOTS;
        return XBOX_HOTSPOTS;
    }

    private static float[] p(float x, float y, float radius) {
        return new float[]{x, y, radius};
    }

    private static final float[][] XBOX_HOTSPOTS = new float[][]{
            p(.745f,.325f,.045f), p(.815f,.255f,.045f), p(.675f,.255f,.045f), p(.745f,.185f,.045f),
            p(.555f,.255f,.035f), p(.415f,.255f,.035f), p(.84f,.06f,.065f), p(.16f,.06f,.065f),
            p(.76f,.08f,.075f), p(.24f,.08f,.075f), null, null, null, null,
            p(.615f,.48f,.075f), p(.245f,.23f,.085f), p(.36f,.41f,.04f), p(.36f,.57f,.04f),
            p(.44f,.49f,.04f), p(.28f,.49f,.04f)
    };

    private static final float[][] SWITCH_HOTSPOTS = new float[][]{
            p(.84f,.27f,.043f), p(.765f,.35f,.043f), p(.69f,.27f,.043f), p(.765f,.19f,.043f),
            p(.61f,.16f,.032f), p(.365f,.16f,.032f), p(.85f,.04f,.07f), p(.15f,.04f,.07f),
            p(.76f,.055f,.075f), p(.24f,.055f,.075f), null, null, null, null,
            p(.63f,.51f,.09f), p(.215f,.24f,.09f), p(.35f,.43f,.04f), p(.35f,.59f,.04f),
            p(.43f,.51f,.04f), p(.27f,.51f,.04f)
    };

    private static final float[][] DUALSENSE_HOTSPOTS = new float[][]{
            p(.77f,.455f,.04f), p(.835f,.385f,.04f), p(.705f,.385f,.04f), p(.77f,.315f,.04f),
            p(.665f,.285f,.027f), p(.34f,.285f,.027f), p(.86f,.08f,.07f), p(.14f,.08f,.07f),
            p(.78f,.10f,.075f), p(.22f,.10f,.075f), null, null, null, null,
            p(.615f,.655f,.08f), p(.39f,.655f,.08f), p(.25f,.315f,.038f), p(.25f,.455f,.038f),
            p(.315f,.385f,.038f), p(.185f,.385f,.038f)
    };

    private static final float[][] DUALSHOCK_HOTSPOTS = new float[][]{
            p(.79f,.49f,.045f), p(.855f,.40f,.045f), p(.725f,.40f,.045f), p(.79f,.31f,.045f),
            p(.685f,.20f,.03f), p(.315f,.20f,.03f), p(.87f,.06f,.07f), p(.13f,.06f,.07f),
            p(.77f,.08f,.075f), p(.23f,.08f,.075f), null, null, null, null,
            p(.62f,.66f,.08f), p(.38f,.66f,.08f), p(.21f,.31f,.04f), p(.21f,.49f,.04f),
            p(.29f,.40f,.04f), p(.13f,.40f,.04f)
    };

    private int mappingForKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return 0;
            case KeyEvent.KEYCODE_BUTTON_B: return 1;
            case KeyEvent.KEYCODE_BUTTON_X: return 2;
            case KeyEvent.KEYCODE_BUTTON_Y: return 3;
            case KeyEvent.KEYCODE_BUTTON_START: return 4;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return 5;
            case KeyEvent.KEYCODE_BUTTON_R2: return 6;
            case KeyEvent.KEYCODE_BUTTON_L2: return 7;
            case KeyEvent.KEYCODE_BUTTON_R1: return 8;
            case KeyEvent.KEYCODE_BUTTON_L1: return 9;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return 14;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return 15;
            case KeyEvent.KEYCODE_DPAD_UP: return 16;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 17;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 18;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 19;
            default: return -1;
        }
    }
}
