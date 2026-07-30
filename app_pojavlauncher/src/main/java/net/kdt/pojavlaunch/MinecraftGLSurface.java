package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.MainActivity.touchCharInput;
import static net.kdt.pojavlaunch.Tools.LOCAL_RENDERER;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_MOUSE_GRAB_FORCE;
import static net.kdt.pojavlaunch.utils.MCOptionUtils.getMcScale;
import static org.lwjgl.glfw.CallbackBridge.sendMouseButton;
import static org.lwjgl.glfw.CallbackBridge.windowHeight;
import static org.lwjgl.glfw.CallbackBridge.windowWidth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.gamepad.DefaultDataProvider;
import net.kdt.pojavlaunch.customcontrols.gamepad.Gamepad;
import net.kdt.pojavlaunch.customcontrols.gamepad.direct.DirectGamepad;
import net.kdt.pojavlaunch.customcontrols.gamepad.direct.DirectGamepadEnableHandler;
import net.kdt.pojavlaunch.customcontrols.mouse.AbstractTouchpad;
import net.kdt.pojavlaunch.customcontrols.mouse.AndroidPointerCapture;
import net.kdt.pojavlaunch.customcontrols.mouse.InGUIEventProcessor;
import net.kdt.pojavlaunch.customcontrols.mouse.InGameEventProcessor;
import net.kdt.pojavlaunch.customcontrols.mouse.TouchEventProcessor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.TouchControllerUtils;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLControllerManager;
import org.lwjgl.glfw.CallbackBridge;


import fr.spse.gamepad_remapper.GamepadHandler;
import fr.spse.gamepad_remapper.RemapperManager;
import fr.spse.gamepad_remapper.RemapperView;

/**
 * Class dealing with showing minecraft surface and taking inputs to dispatch them to minecraft
 */
public class MinecraftGLSurface extends View implements GrabListener, DirectGamepadEnableHandler {
    /* Gamepad object for gamepad inputs, instantiated on need */
    private GamepadHandler mGamepadHandler;
    /* The RemapperView.Builder object allows you to set which buttons to remap */
    private final RemapperManager mInputManager = new RemapperManager(getContext(), new RemapperView.Builder(null)
            .remapA(true)
            .remapB(true)
            .remapX(true)
            .remapY(true)

            .remapLeftJoystick(true)
            .remapRightJoystick(true)
            .remapStart(true)
            .remapSelect(true)
            .remapLeftShoulder(true)
            .remapRightShoulder(true)
            .remapLeftTrigger(true)
            .remapRightTrigger(true)
            .remapDpad(true));

    /* Sensitivity, adjusted according to screen size */
    private final double mSensitivityFactor = (1.4 * (1080f/ Tools.getDisplayMetrics((Activity) getContext()).heightPixels));

    /* Surface ready listener, used by the activity to launch minecraft */
    SurfaceReadyListener mSurfaceReadyListener = null;
    final Object mSurfaceReadyListenerLock = new Object();
    Runnable mFirstFrameListener = null;
    boolean mFirstFrameReported = false;
    private static final int SURFACE_FRAME_PROBE_SIZE = 24;
    private static final int SURFACE_FRAME_PROBE_INTERVAL_MS = 250;
    private static final int SURFACE_FRAME_PROBE_MAX_ATTEMPTS = 80;
    private Runnable mSurfaceViewFrameProbe = null;
    private int mSurfaceViewFrameProbeAttempts = 0;
    /* View holding the surface, either a SurfaceView or a TextureView */
    View mSurface;
    String TAG = "MinecraftGLSurface";

    private final InGameEventProcessor mIngameProcessor = new InGameEventProcessor(mSensitivityFactor);
    private final InGUIEventProcessor mInGUIProcessor = new InGUIEventProcessor();
    private TouchEventProcessor mCurrentTouchProcessor = mInGUIProcessor;
    private AndroidPointerCapture mPointerCapture;
    private boolean mLastGrabState = false;
    public static boolean sdlEnabled = false;
    private static boolean sdlWindowBridgeEnabled = false;
    boolean useSurfaceView = LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;

    public MinecraftGLSurface(Context context) {
        this(context, null);
    }

    public MinecraftGLSurface(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setFocusable(true);
        CallbackBridge.setDirectGamepadEnableHandler(this);
        SDLControllerManager.setDirectGamepadEnableHandler(this);
    }

    public static void setSdlWindowBridgeEnabled(boolean enabled) {
        sdlWindowBridgeEnabled = enabled;
    }

    public static boolean isSdlWindowBridgeEnabled() {
        return sdlWindowBridgeEnabled;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setUpPointerCapture(AbstractTouchpad touchpad) {
        if(mPointerCapture != null) mPointerCapture.detach();
        mPointerCapture = new AndroidPointerCapture(touchpad, this);
    }

    /** Initialize the view and all its settings
     * @param isAlreadyRunning set to true to tell the view that the game is already running
     *                         (only updates the window without calling the start listener)
     * @param touchpad the optional cursor-emulating touchpad, used for touch event processing
     *                 when the cursor is not grabbed
     */
    public void start(boolean isAlreadyRunning, AbstractTouchpad touchpad){
        if(Tools.isAndroid8OrHigher()) setUpPointerCapture(touchpad);
        mInGUIProcessor.setAbstractTouchpad(touchpad);
        useSurfaceView = LauncherPreferences.PREF_USE_ALTERNATE_SURFACE && !rendererNeedsTextureView();
        Log.i(TAG, "Minecraft surface backend: " + (useSurfaceView ? "SurfaceView" : "TextureView")
                + ", renderer=" + LOCAL_RENDERER);
        if(useSurfaceView){
            SurfaceView surfaceView = new SurfaceView(getContext());
            mSurface = surfaceView;

            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                private boolean isCalled = isAlreadyRunning;
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    if (sdlWindowBridgeEnabled) {
                        SDLActivity.attachExternalSurface(holder.getSurface(),
                                surfaceView.getWidth(), surfaceView.getHeight());
                    }
                    if(isCalled) {
                        JREUtils.setupBridgeWindow(surfaceView.getHolder().getSurface());
                        startSurfaceViewFirstFrameProbe(surfaceView);
                        return;
                    }
                    isCalled = true;

                    realStart(surfaceView.getHolder().getSurface());
                    startSurfaceViewFirstFrameProbe(surfaceView);
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                    refreshSize();
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    if (sdlWindowBridgeEnabled) {
                        SDLActivity.detachExternalSurface(holder.getSurface());
                    }
                    cancelSurfaceViewFirstFrameProbe();
                }
            });

            ((ViewGroup)getParent()).addView(surfaceView);
        }else{
            TextureView textureView = new TextureView(getContext());
            textureView.setOpaque(true);
            textureView.setAlpha(1.0f);
            mSurface = textureView;

            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                private boolean isCalled = isAlreadyRunning;
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    Surface tSurface = new Surface(surface);
                    if (sdlWindowBridgeEnabled) {
                        SDLActivity.attachExternalSurface(tSurface, width, height);
                    }
                    if(isCalled) {
                        JREUtils.setupBridgeWindow(tSurface);
                        return;
                    }
                    isCalled = true;

                    realStart(tSurface);
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                    refreshSize();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                    if (sdlWindowBridgeEnabled && mSurface instanceof TextureView) {
                        SDLActivity.detachExternalSurface(null);
                    }
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                    reportFirstFrame();
                }
            });

            ((ViewGroup)getParent()).addView(textureView);
        }


    }

    private static boolean rendererNeedsTextureView() {
        String renderer = LOCAL_RENDERER == null ? "" : LOCAL_RENDERER.toLowerCase(java.util.Locale.ROOT);
        return renderer.contains("zink")
                || renderer.contains("mobileglues")
                || renderer.contains("freedreno")
                || renderer.contains("vulkan")
                || renderer.contains("opengles3");
    }

    /**
     * The touch event for both grabbed an non-grabbed mouse state on the touch screen
     * Does not cover the virtual mouse touchpad
     */
    @Override
    @SuppressWarnings("accessibility")
    public boolean onTouchEvent(MotionEvent e) {
        // Kinda need to send this back to the layout
        if(((ControlLayout)getParent()).getModifiable()) return false;

        // Looking for a mouse to handle, won't have an effect if no mouse exists.
        for (int i = 0; i < e.getPointerCount(); i++) {
            int toolType = e.getToolType(i);
            if(toolType == MotionEvent.TOOL_TYPE_MOUSE) {
                if(Tools.isAndroid8OrHigher() &&
                        mPointerCapture != null) {
                    // Can't handleAutomaticCapture if mouse isn't captured
                    if (!CallbackBridge.isGrabbing() // Only capture if not in menu and user said so
                            && !PREF_MOUSE_GRAB_FORCE) {
                        // This returns true but we really can't consume this.
                        // Else we don't receive ACTION_MOVE
                        return !dispatchGenericMotionEvent(e);
                    }
                    mPointerCapture.handleAutomaticCapture();
                    return true;
                }
            }else if(toolType != MotionEvent.TOOL_TYPE_STYLUS) continue;

            // Mouse found
            if(CallbackBridge.isGrabbing()) return false;
            CallbackBridge.sendCursorPos(   e.getX(i) * LauncherPreferences.PREF_SCALE_FACTOR, e.getY(i) * LauncherPreferences.PREF_SCALE_FACTOR);
            return true; //mouse event handled successfully
        }
        TouchControllerUtils.processTouchEvent(e, this);
        if (mIngameProcessor == null || mInGUIProcessor == null) return true;
        return mCurrentTouchProcessor.processTouchEvent(e);
    }

    private void createGamepad(View contextView, InputDevice inputDevice) {
        if(CallbackBridge.sGamepadDirectInput && !sdlEnabled) {
            mGamepadHandler = new DirectGamepad();
        }else if(!sdlEnabled) {
            mGamepadHandler = new Gamepad(contextView, inputDevice, DefaultDataProvider.INSTANCE, true);
        }else mGamepadHandler = (code, value) -> {}; // Ensure it isn't null while also not processing the events.
    }

    /**
     * The event for mouse/joystick movements
     */
    @SuppressLint("NewApi")
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if(sdlEnabled && Gamepad.isGamepadEvent(event)) {
            final MotionEvent copy = MotionEvent.obtain(event);
            PojavApplication.sExecutorService.execute(()->{
                try {
                    MainActivity.motionListener.onGenericMotion(this, copy);
                    copy.recycle();
                } catch (Throwable ignored) {
                    Log.e(TAG, "SDL failed to send motionevent!");
                }
            });
            return true;
        }
        super.dispatchGenericMotionEvent(event);
        int mouseCursorIndex = -1;

        if(!sdlEnabled && Gamepad.isGamepadEvent(event)){
            if(mGamepadHandler == null) createGamepad(this, event.getDevice());

            mInputManager.handleMotionEventInput(getContext(), event, mGamepadHandler);
            return true;
        }

        for(int i = 0; i < event.getPointerCount(); i++) {
            if(event.getToolType(i) != MotionEvent.TOOL_TYPE_MOUSE && event.getToolType(i) != MotionEvent.TOOL_TYPE_STYLUS ) continue;
            // Mouse found
            mouseCursorIndex = i;
            break;
        }
        if(mouseCursorIndex == -1) return false; // we cant consoom that, theres no mice!

        // Make sure we grabbed the mouse if necessary
        updateGrabState(CallbackBridge.isGrabbing());
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_MOVE:
                CallbackBridge.mouseX = (event.getX(mouseCursorIndex) * LauncherPreferences.PREF_SCALE_FACTOR);
                CallbackBridge.mouseY = (event.getY(mouseCursorIndex) * LauncherPreferences.PREF_SCALE_FACTOR);
                CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
                return true;
            case MotionEvent.ACTION_SCROLL:
                CallbackBridge.sendScroll(event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                return sendMouseButtonUnconverted(event.getActionButton(),true);
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return sendMouseButtonUnconverted(event.getActionButton(),false);
            default:
                return false;
        }
    }

    /** The event for keyboard/ gamepad button inputs */
    public boolean processKeyEvent(KeyEvent event) {
        //Log.i("KeyEvent", event.toString());

        //Filtering useless events by order of probability
        int eventKeycode = event.getKeyCode();
        if(eventKeycode == KeyEvent.KEYCODE_UNKNOWN) return true;
        if(eventKeycode == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if(eventKeycode == KeyEvent.KEYCODE_VOLUME_UP) return false;
        if(event.getRepeatCount() != 0) return true;
        int action = event.getAction();
        if(action == KeyEvent.ACTION_MULTIPLE) return true;
        // Ignore the cancelled up events. They occur when the user switches layouts.
        // In accordance with https://developer.android.com/reference/android/view/KeyEvent#FLAG_CANCELED
        if(action == KeyEvent.ACTION_UP &&
                (event.getFlags() & KeyEvent.FLAG_CANCELED) != 0) return true;

        //Sometimes, key events comes from SOME keys of the software keyboard
        //Even weirder, is is unknown why a key or another is selected to trigger a keyEvent
        if((event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == KeyEvent.FLAG_SOFT_KEYBOARD){
            if(eventKeycode == KeyEvent.KEYCODE_ENTER) return true; //We already listen to it.
            touchCharInput.dispatchKeyEvent(event);
            return true;
        }

        //Sometimes, key events may come from the mouse
        if(event.getDevice() != null
                && ( (event.getSource() & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
                ||   (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE)  ){

            if(eventKeycode == KeyEvent.KEYCODE_BACK){
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, event.getAction() == KeyEvent.ACTION_DOWN);
                return true;
            }
        }
        // Android bundles in garbage KeyEvents for compatibility with old apps
        // that don't have controller code so we are, checking for em.
        boolean isGamepadEvent = Gamepad.isGamepadEvent(event);
        if (sdlEnabled && isGamepadEvent) {
            final KeyEvent copy = new KeyEvent(event);
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    SDLActivity.handleKeyEvent(this, eventKeycode, copy, null);
                } catch (Throwable ignored) {
                    Log.e(TAG, "SDL failed to send keyevent!");
                }
            });
            return true;
        }
        if(!sdlEnabled && isGamepadEvent){
            if(mGamepadHandler == null) createGamepad(this, event.getDevice());

            mInputManager.handleKeyEventInput(getContext(), event, mGamepadHandler);
            return true;
        }

        int index = EfficientAndroidLWJGLKeycode.getIndexByKey(eventKeycode);
        if(EfficientAndroidLWJGLKeycode.containsIndex(index)) {
            EfficientAndroidLWJGLKeycode.execKey(event, index);
            return true;
        }

        // Some events will be generated an infinite number of times when no consumed
        return (event.getFlags() & KeyEvent.FLAG_FALLBACK) == KeyEvent.FLAG_FALLBACK;
    }

    /** Convert the mouse button, then send it
     * @return Whether the event was processed
     */
    public static boolean sendMouseButtonUnconverted(int button, boolean status) {
        int glfwButton = -256;
        switch (button) {
            case MotionEvent.BUTTON_PRIMARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT;
                break;
            case MotionEvent.BUTTON_TERTIARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE;
                break;
            case MotionEvent.BUTTON_SECONDARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT;
                break;
        }
        if(glfwButton == -256) return false;
        sendMouseButton(glfwButton, status);
        return true;
    }

    /** Called when the size need to be set at any point during the surface lifecycle **/
    public void refreshSize(){
        refreshSize(false);
    }

    /** Same as refreshSize, but allows you to force an immediate size update **/
    public void refreshSize(boolean immediate) {
        if(isInLayout() && !immediate) {
            post(this::refreshSize);
            return;
        }
        // Use the width and height of the View instead of display dimensions to avoid
        // getting squiched/stretched due to inconsistencies between the layout and
        // screen dimensions.
        int newWidth = Tools.getDisplayFriendlyRes(
                Math.max(getWidth(), getHeight()), LauncherPreferences.PREF_SCALE_FACTOR);
        int newHeight = Tools.getDisplayFriendlyRes(
                Math.min(getWidth(), getHeight()), LauncherPreferences.PREF_SCALE_FACTOR);
        if (newHeight < 1 || newWidth < 1) {
            Log.e("MGLSurface", String.format("Impossible resolution : %dx%d", newWidth, newHeight));
            return;
        }
        windowWidth = newWidth;
        windowHeight = newHeight;
        if(mSurface == null){
            Log.w("MGLSurface", "Attempt to refresh size on null surface");
            return;
        }
        if(useSurfaceView){
            SurfaceView view = (SurfaceView) mSurface;
            if(view.getHolder() != null){
                view.getHolder().setFixedSize(windowWidth, windowHeight);
            }
        }else{
            TextureView view = (TextureView)mSurface;
            if(view.getSurfaceTexture() != null){
                view.getSurfaceTexture().setDefaultBufferSize(windowWidth, windowHeight);
            }
        }

        // GLFW, SDL and the Android buffer must agree on one pixel size. Sending the
        // unscaled display size here caused modern LWJGL/SDL snapshots to render zoomed.
        if (sdlWindowBridgeEnabled) {
            SDLActivity.resizeExternalSurface(windowWidth, windowHeight);
        }

        CallbackBridge.sendUpdateWindowSize(windowWidth, windowHeight);

    }

    private void realStart(Surface surface){
        // Initial size set. Request immedate refresh, otherwise the initial width and height for the game
        // may be broken/unknown.
        refreshSize(true);
        // Ensures we run at correct refresh rate (should also NOT change the resolution being used)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float maxHz = 120f; // Set to 120 by default just to be safe
            for (float altHz : getDisplay().getMode().getAlternativeRefreshRates()) {
                maxHz = Math.max(maxHz, altHz);
            }
            surface.setFrameRate(maxHz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
        }

        //Load Minecraft options:
        // Modern RenderPearl versions parse this strictly as a boolean.
        MCOptionUtils.set("fullscreen", "false");
        MCOptionUtils.set("overrideWidth", String.valueOf(windowWidth));
        MCOptionUtils.set("overrideHeight", String.valueOf(windowHeight));
        MCOptionUtils.save();
        getMcScale();

        JREUtils.setupBridgeWindow(surface);

        new Thread(() -> {
            try {
                // Wait until the listener is attached
                synchronized(mSurfaceReadyListenerLock) {
                    if(mSurfaceReadyListener == null) mSurfaceReadyListenerLock.wait();
                }

                mSurfaceReadyListener.isReady();
            } catch (Throwable e) {
                Tools.showError(getContext(), e, true);
            }
        }, "JVM Main thread").start();
    }

    private void reportFirstFrame() {
        if (mFirstFrameReported) {
            return;
        }
        mFirstFrameReported = true;
        cancelSurfaceViewFirstFrameProbe();
        Runnable listener = mFirstFrameListener;
        if (listener != null) {
            post(listener);
        }
    }

    private void startSurfaceViewFirstFrameProbe(SurfaceView surfaceView) {
        cancelSurfaceViewFirstFrameProbe();
        mSurfaceViewFrameProbeAttempts = 0;
        mSurfaceViewFrameProbe = () -> probeSurfaceViewFirstFrame(surfaceView);
        Tools.MAIN_HANDLER.postDelayed(mSurfaceViewFrameProbe, SURFACE_FRAME_PROBE_INTERVAL_MS);
    }

    private void cancelSurfaceViewFirstFrameProbe() {
        if (mSurfaceViewFrameProbe != null) {
            Tools.MAIN_HANDLER.removeCallbacks(mSurfaceViewFrameProbe);
            mSurfaceViewFrameProbe = null;
        }
    }

    private void probeSurfaceViewFirstFrame(SurfaceView surfaceView) {
        if (mFirstFrameReported || mSurfaceViewFrameProbe == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (++mSurfaceViewFrameProbeAttempts >= SURFACE_FRAME_PROBE_MAX_ATTEMPTS) {
                Log.w(TAG, "SurfaceView first-frame probe unavailable on this Android version; hiding launcher overlay.");
                reportFirstFrame();
            } else {
                Tools.MAIN_HANDLER.postDelayed(mSurfaceViewFrameProbe, SURFACE_FRAME_PROBE_INTERVAL_MS);
            }
            return;
        }
        if (!surfaceView.isAttachedToWindow()
                || surfaceView.getWidth() <= 0
                || surfaceView.getHeight() <= 0
                || surfaceView.getHolder() == null
                || surfaceView.getHolder().getSurface() == null
                || !surfaceView.getHolder().getSurface().isValid()) {
            scheduleNextSurfaceViewProbe();
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                SURFACE_FRAME_PROBE_SIZE,
                SURFACE_FRAME_PROBE_SIZE,
                Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(surfaceView, bitmap, result -> {
                try {
                    if (result == PixelCopy.SUCCESS && bitmapHasVisibleGameContent(bitmap)) {
                        reportFirstFrame();
                    } else {
                        scheduleNextSurfaceViewProbe();
                    }
                } finally {
                    bitmap.recycle();
                }
            }, Tools.MAIN_HANDLER);
        } catch (IllegalArgumentException e) {
            bitmap.recycle();
            scheduleNextSurfaceViewProbe();
        }
    }

    private void scheduleNextSurfaceViewProbe() {
        if (mFirstFrameReported || mSurfaceViewFrameProbe == null) {
            return;
        }
        mSurfaceViewFrameProbeAttempts++;
        if (mSurfaceViewFrameProbeAttempts >= SURFACE_FRAME_PROBE_MAX_ATTEMPTS) {
            Log.w(TAG, "SurfaceView first-frame probe timed out; hiding launcher overlay to avoid blocking the game.");
            reportFirstFrame();
            return;
        }
        Tools.MAIN_HANDLER.postDelayed(mSurfaceViewFrameProbe, SURFACE_FRAME_PROBE_INTERVAL_MS);
    }

    private boolean bitmapHasVisibleGameContent(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int visiblePixels = 0;
        for (int color : pixels) {
            int alpha = (color >>> 24) & 0xff;
            int red = (color >>> 16) & 0xff;
            int green = (color >>> 8) & 0xff;
            int blue = color & 0xff;
            if (alpha > 32 && red + green + blue > 36) {
                visiblePixels++;
            }
        }
        return visiblePixels >= Math.max(8, pixels.length / 32);
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        post(()->updateGrabState(isGrabbing));
    }

    private TouchEventProcessor pickEventProcessor(boolean isGrabbing) {
        return isGrabbing ? mIngameProcessor : mInGUIProcessor;
    }

    private void updateGrabState(boolean isGrabbing) {
        if(mLastGrabState != isGrabbing) {
            mCurrentTouchProcessor.cancelPendingActions();
            CallbackBridge.resetSdlInputState();
            mCurrentTouchProcessor = pickEventProcessor(isGrabbing);
            mLastGrabState = isGrabbing;
        }
    }

    @Override
    public void onDirectGamepadEnabled() {
        post(()->{
            if(mGamepadHandler != null && mGamepadHandler instanceof Gamepad) {
                ((Gamepad)mGamepadHandler).removeSelf();
            }
            // Force gamepad recreation on next event
            mGamepadHandler = null;
        });
    }

    /** A small interface called when the listener is ready for the first time */
    public interface SurfaceReadyListener {
        void isReady();
    }

    public void setSurfaceReadyListener(SurfaceReadyListener listener){
        synchronized (mSurfaceReadyListenerLock) {
            mSurfaceReadyListener = listener;
            mSurfaceReadyListenerLock.notifyAll();
        }
    }

    public void setFirstFrameListener(Runnable listener) {
        mFirstFrameListener = listener;
        if (mFirstFrameReported && listener != null) {
            post(listener);
        }
    }
}
