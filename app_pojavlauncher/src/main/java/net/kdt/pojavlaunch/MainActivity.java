package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.Tools.dialogForceClose;
import static net.kdt.pojavlaunch.Tools.hasMods;
import static net.kdt.pojavlaunch.Tools.runMethodbyReflection;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VIRTUAL_MOUSE_START;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.windowHeight;
import static org.lwjgl.glfw.CallbackBridge.windowWidth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kdt.LoggerView;

import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsDialog;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsFeature;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites;
import net.kdt.pojavlaunch.battlysocial.BattlySocialManager;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsManager;
import net.kdt.pojavlaunch.customcontrols.ControlButtonMenuListener;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.customcontrols.keyboard.LwjglCharSender;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.customcontrols.mouse.GyroControl;
import net.kdt.pojavlaunch.customcontrols.mouse.HotbarView;
import net.kdt.pojavlaunch.customcontrols.mouse.Touchpad;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.QuickSettingSideDialog;
import net.kdt.pojavlaunch.services.GameService;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.MinecraftCompatibilityEngine;
import net.kdt.pojavlaunch.utils.RendererPluginRegistry;
import net.kdt.pojavlaunch.utils.ControllerProfileManager;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.TouchControllerUtils;
import net.kdt.pojavlaunch.utils.BattlyClientCompat;
import net.kdt.pojavlaunch.utils.VanillaPostShaderCompat;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.libsdl.app.SDL;
import org.libsdl.app.SDLSurface;
import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable, ServiceConnection {
    public static volatile ClipboardManager GLOBAL_CLIPBOARD;
    public static final String TAG = "MainActivity";
    public static final String INTENT_MINECRAFT_VERSION = "intent_version";

    volatile public static boolean isInputStackCall;
    protected static View.OnGenericMotionListener motionListener = (v, event) -> false;

    public static TouchCharInput touchCharInput;
    private MinecraftGLSurface minecraftGLView;
    private static Touchpad touchpad;
    private LoggerView loggerView;
    private FrameLayout mContentFrame;
    private View mGameStartingOverlay;
    private TextView mGameStartingSubtitle;
    private ImageView mGameStartingLogo;
    private ProgressBar mGameStartingProgress;
    private DrawerLayout drawerLayout;
    private ListView navDrawer;
    private View mDrawerPullButton;
    private GyroControl mGyroControl = null;
    private ControlLayout mControlLayout;
    private HotbarView mHotbarView;
    private boolean mRendererAutoSelected;

    MinecraftProfile minecraftProfile;

    private ArrayAdapter<String> gameActionArrayAdapter;
    private AdapterView.OnItemClickListener gameActionClickListener;
    public ArrayAdapter<String> ingameControlsEditorArrayAdapter;
    public AdapterView.OnItemClickListener ingameControlsEditorListener;
    private GameService.LocalBinder mServiceBinder;

    private QuickSettingSideDialog mQuickSettingSideDialog;
    private BroadcastReceiver mBattlyWorldsInviteReceiver;
    private View mLanInvitePrompt;
    private boolean mLanInvitePromptHidden;
    private String mLastLanPromptToken = "";
    private final Runnable mLanInviteChecker = new Runnable() {
        @Override
        public void run() {
            checkLanInvitePrompt();
            Tools.MAIN_HANDLER.postDelayed(this, 10000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LauncherPreferences.PREF_GAMEPAD_SDL_PASSTHRU) {
            // TODO: Use lower level HID capture that needs a dialogue box from the user for the
            // app to fully take focus of the input devices. Might cause issues with older android
            // versions so we don't use that right now. Needs testing.
            // Currently tried but only identification works OOTB, inputs aren't being sent.

            // TODO: Use a hook to load SDL logic depending on whether libSDL3.so is loaded.
            try {
                // Note: This doesn't dlopen it for the mod, they still have to do it themselves
                // Why? https://github.com/android/ndk/issues/201#issuecomment-248060092
                // Just in case that gets deleted off the internet:
                // "On Android only the main executable and LD_PRELOADs are considered to be
                // RTLD_GLOBAL, all the dependencies of the main executable remain RTLD_LOCAL." - dimitry
                SDL.loadLibrary("SDL3", this);
                SDL.loadLibrary("SDL2", this);
                SDL.initialize();
                SDL.setupJNI();
                SDL.setContext(this);
                new SDLSurface(this);
                motionListener = (View.OnGenericMotionListener)
                        runMethodbyReflection("org.libsdl.app.SDLActivity",
                                "getMotionListener");
                if (LauncherPreferences.PREF_GAMEPAD_FORCEDSDL_PASSTHRU) Tools.SDL.initializeControllerSubsystems();
            } catch (UnsatisfiedLinkError ignored) {
                // Ignore because if SDL.setupJNI(); fails, SDL wasn't loaded.
            } catch (ReflectiveOperationException e) {
                Tools.showErrorRemote("SDL did not load properly.", e);
            }
        }

        minecraftProfile = LauncherProfiles.getCurrentProfile();
        BattlySocialManager.heartbeatGame(this,
                minecraftProfile == null ? "" : minecraftProfile.lastVersionId);
        ControllerProfileManager.apply(this, minecraftProfile);

        String gameDirPath = Tools.getGameDirPath(minecraftProfile).getAbsolutePath();
        MCOptionUtils.load(gameDirPath);
        if (Tools.hasTouchController(new File(gameDirPath)) || LauncherPreferences.PREF_FORCE_ENABLE_TOUCHCONTROLLER) {
            TouchControllerUtils.initialize(this);
        }

        Intent gameServiceIntent = new Intent(this, GameService.class);
        // Start the service a bit early
        ContextCompat.startForegroundService(this, gameServiceIntent);
        initLayout(R.layout.activity_basemain);
        showGameStartingOverlay();
        CallbackBridge.addGrabListener(touchpad);
        CallbackBridge.addGrabListener(minecraftGLView);

        mGyroControl = new GyroControl(this);

        // Enabling this on TextureView results in a broken white result
        if(PREF_USE_ALTERNATE_SURFACE) getWindow().setBackgroundDrawable(null);
        else getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        // Set the sustained performance mode for available APIs
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            getWindow().setSustainedPerformanceMode(PREF_SUSTAINED_PERFORMANCE);

        ingameControlsEditorArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_customcontrol));
        ingameControlsEditorListener = (parent, view, position, id) -> {
            switch(position) {
                case 0: mControlLayout.addControlButton(new ControlData("New")); break;
                case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
                case 2: mControlLayout.addJoystickButton(new ControlJoystickData()); break;
                case 3: mControlLayout.addControlButton(ControlData.createPerformanceWidget()); break;
                case 4: mControlLayout.openLoadDialog(); break;
                case 5: mControlLayout.openSaveDialog(this); break;
                case 6: mControlLayout.openSetDefaultDialog(); break;
                case 7: mControlLayout.openExitDialog(this);
            }
        };

        // Recompute the gui scale when options are changed
        MCOptionUtils.MCOptionListener optionListener = MCOptionUtils::getMcScale;
        MCOptionUtils.addMCOptionListener(optionListener);
        mControlLayout.setModifiable(false);

        // Set the activity for the executor. Must do this here, or else Tools.showErrorRemote() may not
        // execute the correct method
        ContextExecutor.setActivity(this);
        if (BattlyWorldsFeature.ENABLED) {
            BattlyWorldsInvites.setGameActive(this, true);
            mBattlyWorldsInviteReceiver = BattlyWorldsInvites.createGameInviteReceiver(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mBattlyWorldsInviteReceiver,
                        BattlyWorldsInvites.inviteIntentFilter(),
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(mBattlyWorldsInviteReceiver, BattlyWorldsInvites.inviteIntentFilter());
            }
            BattlyWorldsInvites.joinPendingIfAny(this);
            Tools.MAIN_HANDLER.postDelayed(mLanInviteChecker, 10000);
        }
        //Now, attach to the service. The game will only start when this happens, to make sure that we know the right state.
        bindService(gameServiceIntent, this, 0);
    }

    protected void initLayout(int resId) {
        String version = getIntent().getStringExtra(INTENT_MINECRAFT_VERSION);
        version = version == null ? minecraftProfile.lastVersionId : version;
        JMinecraftVersionList.Version mVersionInfo = Tools.getVersionInfo(version);

        // The surface backend is selected while activity_basemain is inflated. Resolve Auto here,
        // so it follows the same initialization path as an explicitly selected renderer.
        resolveRendererBeforeSurfaceCreation(version, mVersionInfo);

        setContentView(resId);
        bindValues();
        mControlLayout.setMenuListener(this);

        mDrawerPullButton.setOnClickListener(v -> onClickedMenu());
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if(!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());
            // FIXME: is it safe for multi thread?
            GLOBAL_CLIPBOARD = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            touchCharInput.setCharacterSender(new LwjglCharSender());

            setTitle("Minecraft " + minecraftProfile.lastVersionId);

            // Minecraft 1.13+
            isInputStackCall = mVersionInfo.arguments != null;
            CallbackBridge.nativeSetUseInputStackQueue(isInputStackCall);

            Tools.getDisplayMetrics(this);
            windowWidth = Tools.getDisplayFriendlyRes(currentDisplayMetrics.widthPixels, 1f);
            windowHeight = Tools.getDisplayFriendlyRes(currentDisplayMetrics.heightPixels, 1f);


            // Menu
            gameActionArrayAdapter = createGameActionAdapter();
            gameActionClickListener = (parent, view, position, id) -> {
                boolean closeDrawer = true;
                switch(position) {
                    case 0: dialogForceClose(MainActivity.this); break;
                    case 1: openLogOutput(); break;
                    case 2: dialogSendCustomKey(); break;
                    case 3: openQuickSettings(); break;
                    case 4:
                        openCustomControls();
                        closeDrawer = false;
                        break;
                    case 5: openBattlyWorlds(); break;
                }
                if (closeDrawer) {
                    drawerLayout.closeDrawers();
                }
            };
            navDrawer.setAdapter(gameActionArrayAdapter);
            navDrawer.setOnItemClickListener(gameActionClickListener);
            drawerLayout.closeDrawers();

            final String finalVersion = version;
            minecraftGLView.setSurfaceReadyListener(() -> {
                try {
                    updateGameStartingStage(R.string.launcher_starting_stage_profile, 28);
                    // Setup virtual mouse right before launching
                    if (PREF_VIRTUAL_MOUSE_START) {
                        touchpad.post(() -> touchpad.switchState());
                    }

                    runCraft(finalVersion, mVersionInfo);
                }catch (Throwable e){
                    returnToLauncherAfterGameFailure(e);
                }
            });
        } catch (Throwable e) {
            Tools.showError(this, e, true);
        }
    }

    private void loadControls() {
        try {
            // Load keys
            mControlLayout.loadLayout(
                    minecraftProfile.controlFile == null
                            ? LauncherPreferences.PREF_DEFAULTCTRL_PATH
                            : Tools.CTRLMAP_PATH + "/" + minecraftProfile.controlFile);
        } catch(IOException e) {
            try {
                Log.w("MainActivity", "Unable to load the control file, loading the default now", e);
                mControlLayout.loadLayout(Tools.CTRLDEF_FILE);
            } catch (IOException ioException) {
                Tools.showError(this, ioException);
            }
        } catch (Throwable th) {
            Tools.showError(this, th);
        }
        mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        mControlLayout.toggleControlVisible();
    }

    @Override
    public void onAttachedToWindow() {
        // Post to get the correct display dimensions after layout.
        LauncherPreferences.computeNotchSize(this);
        mControlLayout.post(()->{
            Tools.getDisplayMetrics(this);
            loadControls();
        });
    }

    /** Boilerplate binding */
    private void bindValues(){
        mContentFrame = findViewById(R.id.content_frame);
        mGameStartingOverlay = findViewById(R.id.main_game_starting_overlay);
        mGameStartingSubtitle = findViewById(R.id.main_game_starting_subtitle);
        mGameStartingLogo = findViewById(R.id.main_game_starting_logo);
        mGameStartingProgress = findViewById(R.id.main_game_starting_progress);
        mControlLayout = findViewById(R.id.main_control_layout);
        minecraftGLView = findViewById(R.id.main_game_render_view);
        touchpad = findViewById(R.id.main_touchpad);
        drawerLayout = findViewById(R.id.main_drawer_options);
        navDrawer = findViewById(R.id.main_navigation_view);
        loggerView = findViewById(R.id.mainLoggerView);
        mControlLayout = findViewById(R.id.main_control_layout);
        touchCharInput = findViewById(R.id.mainTouchCharInput);
        mDrawerPullButton = findViewById(R.id.drawer_button);
        mHotbarView = findViewById(R.id.hotbar_view);
        minecraftGLView.setFirstFrameListener(this::hideGameStartingOverlay);
    }

    private void resolveRendererBeforeSurfaceCreation(String versionId,
                                                       JMinecraftVersionList.Version version) {
        String requestedRenderer = normalizeRendererSelection(minecraftProfile.pojavRendererName);
        if (requestedRenderer == null) {
            requestedRenderer = normalizeRendererSelection(LauncherPreferences.PREF_RENDERER);
        }

        mRendererAutoSelected = requestedRenderer == null;
        if (mRendererAutoSelected) {
            MinecraftCompatibilityEngine.Report compatibility = MinecraftCompatibilityEngine.evaluate(
                    this, versionId, version, null);
            requestedRenderer = compatibility.rendererId;
            Log.i(TAG, "Auto renderer resolved before surface creation: " + requestedRenderer);
        } else {
            Log.i("RdrDebug", "__P_renderer=" + requestedRenderer);
        }
        Tools.LOCAL_RENDERER = RendererPluginRegistry.runtimeRendererFor(this, requestedRenderer);
    }

    private String normalizeRendererSelection(String renderer) {
        if (renderer == null || renderer.trim().isEmpty() || "auto".equalsIgnoreCase(renderer.trim())) {
            return null;
        }
        return "vulkan_zink".equals(renderer)
                ? "opengles3_desktopgl_zink_kopper"
                : renderer;
    }

    private void showGameStartingOverlay() {
        if (mGameStartingOverlay == null) {
            return;
        }
        updateGameStartingStage(R.string.launcher_starting_stage_surface, 8);
        mGameStartingOverlay.setVisibility(View.VISIBLE);
        mGameStartingOverlay.setAlpha(1f);
        if (mGameStartingLogo != null) {
            mGameStartingLogo.setScaleX(0.92f);
            mGameStartingLogo.setScaleY(0.92f);
            mGameStartingLogo.animate()
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(900)
                    .withEndAction(() -> {
                        if (mGameStartingLogo != null && mGameStartingOverlay != null
                                && mGameStartingOverlay.getVisibility() == View.VISIBLE) {
                            mGameStartingLogo.animate()
                                    .scaleX(0.96f)
                                    .scaleY(0.96f)
                                    .setDuration(900)
                                    .start();
                        }
                    })
                    .start();
        }
    }

    private void hideGameStartingOverlay() {
        if (mGameStartingOverlay == null) {
            return;
        }
        updateGameStartingStage(R.string.launcher_starting_stage_ready, 100);
        mGameStartingOverlay.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> {
                    mGameStartingOverlay.setVisibility(View.GONE);
                    mGameStartingOverlay.setAlpha(1f);
                })
                .start();
        if (mGameStartingLogo != null) {
            mGameStartingLogo.animate().cancel();
        }
    }

    private void returnToLauncherAfterGameFailure(Throwable throwable) {
        hideGameStartingOverlay();
        Logger.appendToLog("Minecraft launch failed before Java runtime:\n" + Log.getStackTraceString(throwable));
        LauncherActivity.openAfterGameExit(this, -1, throwable.getMessage());
        finish();
        Tools.MAIN_HANDLER.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 450);
    }

    private void updateGameStartingStage(int messageResId, int progress) {
        if (mGameStartingOverlay == null) {
            return;
        }
        Tools.runOnUiThread(() -> {
            if (mGameStartingSubtitle != null) {
                mGameStartingSubtitle.animate().cancel();
                mGameStartingSubtitle.setAlpha(1f);
                mGameStartingSubtitle.setText(messageResId);
            }
            if (mGameStartingProgress != null) {
                mGameStartingProgress.setIndeterminate(false);
                mGameStartingProgress.setProgress(progress);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Tools.updateWindowSize(this);
        if (minecraftGLView != null) {
            minecraftGLView.post(() -> minecraftGLView.refreshSize(true));
            Tools.MAIN_HANDLER.postDelayed(() -> {
                if (minecraftGLView != null) {
                    Tools.updateWindowSize(this);
                    minecraftGLView.refreshSize(true);
                }
            }, 900);
        }
        if(PREF_ENABLE_GYRO) mGyroControl.enable();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1);
    }

    @Override
    protected void onPause() {
        mGyroControl.disable();
        if (CallbackBridge.isGrabbing()){
            sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
        }
        if(mQuickSettingSideDialog != null) {
            mQuickSettingSideDialog.cancel();
        }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0);

        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 1);
    }

    @Override
    protected void onStop() {
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 0);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Tools.MAIN_HANDLER.removeCallbacks(mLanInviteChecker);
        if (BattlyWorldsFeature.ENABLED) {
            BattlyWorldsInvites.setGameActive(this, false);
            if (mBattlyWorldsInviteReceiver != null) {
                try {
                    unregisterReceiver(mBattlyWorldsInviteReceiver);
                } catch (IllegalArgumentException ignored) {
                }
                mBattlyWorldsInviteReceiver = null;
            }
        }
        CallbackBridge.removeGrabListener(touchpad);
        CallbackBridge.removeGrabListener(minecraftGLView);
        ContextExecutor.clearActivity();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(mGyroControl != null) mGyroControl.updateOrientation();
        // Layout resize is practically guaranteed on a configuration change, and `onConfigurationChanged`
        // does not implicitly start a layout. So, request a layout and expect the screen dimensions to be valid after the]
        // post.
        mControlLayout.requestLayout();
        mControlLayout.post(()->{
            // Child of mControlLayout, so refreshing size here is correct
            minecraftGLView.refreshSize();
            Tools.updateWindowSize(this);
            mControlLayout.refreshControlButtonPositions();
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if(minecraftGLView != null)  // Useful when backing out of the app
            Tools.MAIN_HANDLER.postDelayed(() -> minecraftGLView.refreshSize(), 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            // Reload PREF_DEFAULTCTRL_PATH
            // If the storage root got unmounted/unreadable we won't be able to load the file anyway,
            // and MissingStorageActivity will be started.
            if(!Tools.checkStorageRoot(this)) return;
            LauncherPreferences.loadPreferences(getApplicationContext());
            try {
                mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (BattlyWorldsFeature.ENABLED && requestCode == BattlyWorldsManager.REQUEST_VPN_PERMISSION) {
            BattlyWorldsManager.onVpnPermissionResult(this, resultCode);
        }
    }

    private void runCraft(String versionId, JMinecraftVersionList.Version version) throws Throwable {
        updateGameStartingStage(R.string.launcher_starting_stage_profile, 32);
        boolean isBattlyClient = versionId != null && versionId.toLowerCase(Locale.ROOT).contains("battly client");
        if (isBattlyClient && "1.8.9".equals(version.assets)) {
            Logger.appendToLog("Info: Battly Client asset index normalized from 1.8.9 to 1.8");
            version.assets = "1.8";
        }
        String assetVersion;
        try {
            if (version.inheritsFrom != null) { // We are almost definitely modded if this runs
                File vanillaJsonFile = new File(Tools.DIR_HOME_VERSION + "/" + version.inheritsFrom + "/" + version.inheritsFrom + ".json");
                JMinecraftVersionList.Version vanillaJson;
                try { // Get the vanilla json from modded instance
                    vanillaJson = Tools.GLOBAL_GSON.fromJson(Tools.read(vanillaJsonFile.getAbsolutePath()), JMinecraftVersionList.Version.class);
                } catch (IOException ignored) { // Should never happen, we check for this in MinecraftDownloader().start()
                    throw new RuntimeException(getString(R.string.error_vanilla_json_corrupt));
                }
                // Something went wrong if this is somehow not the case anymore
                if (!Objects.equals(vanillaJson.assets, vanillaJson.assetIndex.id))
                    Tools.showErrorRemote(new RuntimeException(getString(R.string.error_vanilla_json_corrupt)));
                assetVersion = vanillaJson.assets;
            } else {
                // Else assume we are vanilla
                if (!Objects.equals(version.assets, version.assetIndex.id))
                    Tools.showErrorRemote(new RuntimeException(getString(R.string.error_vanilla_json_corrupt)));
                assetVersion = version.assets;
            }
       } catch (RuntimeException ignored){
            assetVersion = "legacy";
       } // If this fails.. oh well.
        
        updateGameStartingStage(R.string.launcher_starting_stage_renderer, 48);
        boolean usesModernAssets = isModernAssetVersion(assetVersion);
        MinecraftCompatibilityEngine.Report compatibility = MinecraftCompatibilityEngine.evaluate(
                this, versionId, version, Tools.LOCAL_RENDERER);
        if (Tools.LOCAL_RENDERER == null || !Tools.checkRendererCompatible(this, Tools.LOCAL_RENDERER)) {
            Tools.LOCAL_RENDERER = compatibility.rendererId;
            mRendererAutoSelected = true;
            Logger.appendToLog("Info: Auto-selected compatible renderer: " + Tools.LOCAL_RENDERER);
        }
        Tools.LOCAL_RENDERER = RendererPluginRegistry.runtimeRendererFor(this, Tools.LOCAL_RENDERER);
        if (mRendererAutoSelected) {
            Logger.appendToLog("Info: Auto renderer initialized before game surface: " + Tools.LOCAL_RENDERER);
        }
        Logger.appendToLog("Info: " + compatibility.diagnosticLine());
        if (usesModernAssets && shouldForceMobileGluesForModernEmulator(Tools.LOCAL_RENDERER)) {
            Log.w("runCraft", "Renderer " + Tools.LOCAL_RENDERER
                    + " is not reliable for modern Minecraft on the Android emulator; using MobileGlues");
            Tools.LOCAL_RENDERER = "opengles_mobileglues";
        }
        if(!Tools.checkRendererCompatible(this, Tools.LOCAL_RENDERER)) {
            Tools.RenderersList renderersList = Tools.getCompatibleRenderers(this);
            String firstCompatibleRenderer = renderersList.rendererIds.get(0);
            Log.w("runCraft","Incompatible renderer "+Tools.LOCAL_RENDERER+ " will be replaced with "+firstCompatibleRenderer);
            Tools.LOCAL_RENDERER = firstCompatibleRenderer;
            runOnUiThread(() -> Toast.makeText(this, R.string.autorendererselectfailed, Toast.LENGTH_LONG).show());
            Tools.releaseRenderersCache();
        }

        // MCL-3732 Mitigation
        // I don't trust the bug tracker. 'server-resource-pack" was removed in 1.20.3-pre3
        // so we use 12 to detect that. We still generate till 1.20.5 else we don't cover
        // 1.20.3-pre2 and such. Better to over than to under.
        File folder = new File(Tools.getGameDirPath(minecraftProfile), "server-resource-pack");
        try {
            if (Integer.parseInt(assetVersion) <= 12) folder.mkdir();
        } catch (NumberFormatException e) { folder.mkdir(); }

        MinecraftAccount minecraftAccount = PojavProfile.getCurrentProfileContent(this, null);
        if (hasMods("sodium"))
            Logger.appendToLog("WARNING: Sodium is being used, Battly Launcher does NOT support this mod, you are on your own");
        Logger.appendToLog("--------- Starting game with Launcher Debug!");
        Tools.printLauncherInfo(versionId, Tools.isValidString(minecraftProfile.javaArgs) ? minecraftProfile.javaArgs : LauncherPreferences.PREF_CUSTOM_JAVA_ARGS);
        if(Tools.LOCAL_RENDERER.equals("opengles_mobileglues")) {
            LauncherPreferences.writeMGRendererSettings(isBattlyClient, versionId);
        }
        updateGameStartingStage(R.string.launcher_starting_stage_jre, 64);
        JREUtils.redirectAndPrintJRELog();
        LauncherProfiles.load();
        int requiredJavaVersion = 8;
        if(version.javaVersion != null) requiredJavaVersion = version.javaVersion.majorVersion;
        Telemetry.logGameLaunch(versionId, requiredJavaVersion, Tools.LOCAL_RENDERER);
        VanillaPostShaderCompat.patchForAndroid(minecraftProfile, versionId);
        BattlyClientCompat.patchForAndroid(versionId);
        updateGameStartingStage(R.string.launcher_starting_stage_first_frame, 86);
        Tools.launchMinecraft(this, minecraftAccount, minecraftProfile, versionId, requiredJavaVersion);
        //Note that we actually stall in the above function, even if the game crashes. But let's be safe.
        Tools.runOnUiThread(this::hideGameStartingOverlay);
        Tools.runOnUiThread(()-> mServiceBinder.isActive = false);
    }

    private boolean isModernAssetVersion(String assetVersion) {
        return assetVersion != null
                && (assetVersion.matches("\\d+")
                || "1.17".equals(assetVersion)
                || "1.18".equals(assetVersion)
                || "1.19".equals(assetVersion));
    }

    private boolean shouldForceMobileGluesForModernEmulator(String renderer) {
        return Tools.isAndroidEmulator()
                && renderer != null
                && renderer.toLowerCase(Locale.ROOT).contains("zink");
    }

    private void dialogSendCustomKey() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this, R.style.BattlyDialog);
        dialog.setTitle(R.string.control_customkey);
        dialog.setItems(EfficientAndroidLWJGLKeycode.generateKeyName(), (dInterface, position) -> EfficientAndroidLWJGLKeycode.execKeyIndex(position));
        dialog.show();
    }

    boolean isInEditor;
    private void openCustomControls() {
        if(ingameControlsEditorListener == null || ingameControlsEditorArrayAdapter == null) return;

        mControlLayout.setModifiable(true);
        navDrawer.setAdapter(ingameControlsEditorArrayAdapter);
        navDrawer.setOnItemClickListener(ingameControlsEditorListener);
        mDrawerPullButton.setVisibility(View.VISIBLE);
        isInEditor = true;
    }

    private void openLogOutput() {
        loggerView.setVisibility(View.VISIBLE);
    }

    private void openQuickSettings() {
        if(mQuickSettingSideDialog == null) {
            mQuickSettingSideDialog = new QuickSettingSideDialog(this, mControlLayout) {
                @Override
                public void onResolutionChanged() {
                    minecraftGLView.refreshSize();
                    mHotbarView.onResolutionChanged();
                }

                @Override
                public void onGyroStateChanged() {
                    mGyroControl.updateOrientation();
                    if (PREF_ENABLE_GYRO) {
                        mGyroControl.enable();
                    } else {
                        mGyroControl.disable();
                    }
                }
            };
        }
        mQuickSettingSideDialog.appear(true);
    }

    private void openBattlyWorlds() {
        openBattlyWorlds(false);
    }

    private void openBattlyWorlds(boolean autoHost) {
        if (!BattlyWorldsFeature.ENABLED) {
            BattlyWorldsFeature.showDisabledDialog(this);
            return;
        }
        new BattlyWorldsDialog(this, autoHost).show();
    }

    private ArrayAdapter<String> createGameActionAdapter() {
        String[] titles = getResources().getStringArray(R.array.menu_ingame);
        int[] descriptions = {
                R.string.ingame_menu_force_close_desc,
                R.string.ingame_menu_logs_desc,
                R.string.ingame_menu_custom_key_desc,
                R.string.ingame_menu_quick_settings_desc,
                R.string.ingame_menu_controls_desc,
                R.string.ingame_menu_battlyworlds_desc
        };
        int[] icons = {
                R.drawable.ic_close_white,
                R.drawable.ic_battly_logs_line,
                R.drawable.ic_battly_keyboard_line,
                R.drawable.ic_battly_settings_line,
                R.drawable.ic_battly_gamepad_line,
                R.drawable.bworlds
        };
        return new ArrayAdapter<String>(this, 0, titles) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(6), dp(10), dp(6));
                row.setMinimumHeight(dp(52));

                GradientDrawable rowBackground = new GradientDrawable();
                rowBackground.setColor(position == 0 ? 0x223A1E24 : 0x1F8ADBC6);
                rowBackground.setCornerRadius(dp(18));
                rowBackground.setStroke(1, position == 0 ? 0x44FF6B7A : 0x225C7F87);
                row.setBackground(rowBackground);

                ImageView icon = new ImageView(MainActivity.this);
                icon.setImageResource(position < icons.length ? icons[position] : R.drawable.ic_battly_logo);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                icon.setClipToOutline(false);
                if (position == 5) {
                    icon.clearColorFilter();
                    icon.setPadding(dp(7), dp(7), dp(7), dp(7));
                } else {
                    icon.setColorFilter(position == 0 ? 0xFFFF8FA0 : 0xFF8ADBC6);
                    icon.setPadding(dp(8), dp(8), dp(8), dp(8));
                }
                GradientDrawable iconBackground = new GradientDrawable();
                iconBackground.setColor(0x333C4E58);
                iconBackground.setCornerRadius(dp(13));
                icon.setBackground(iconBackground);
                row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

                LinearLayout copy = new LinearLayout(MainActivity.this);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(dp(12), 0, 0, 0);

                TextView title = new TextView(MainActivity.this);
                title.setText(getItem(position));
                title.setTextColor(0xFFFFFFFF);
                title.setTextSize(14);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setSingleLine(true);

                TextView subtitle = new TextView(MainActivity.this);
                subtitle.setText(position < descriptions.length ? getString(descriptions[position]) : "");
                subtitle.setTextColor(0xFFB4C5D0);
                subtitle.setTextSize(11);
                subtitle.setMaxLines(2);

                copy.addView(title);
                copy.addView(subtitle);
                row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout container = new LinearLayout(MainActivity.this);
                container.setPadding(dp(8), dp(3), dp(8), dp(3));
                container.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                return container;
            }
        };
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void checkLanInvitePrompt() {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        if (mLanInvitePromptHidden
                || mContentFrame == null
                || BattlyWorldsManager.getMode() != null
                || (mLanInvitePrompt != null && mLanInvitePrompt.getParent() != null)) {
            return;
        }
        String token = findLatestLanPortToken();
        if (token.isEmpty() || token.equals(mLastLanPromptToken)) {
            return;
        }
        mLastLanPromptToken = token;
        showLanInvitePrompt();
    }

    private String findLatestLanPortToken() {
        File latestLog = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        if (!latestLog.exists()) {
            return "";
        }
        try {
            String log = Tools.read(latestLog);
            String[] lines = log.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                String lower = line.toLowerCase();
                if (lower.contains("local game hosted on port")
                        || lower.contains("started on port")
                        || (lower.contains("started on 0.0.0.0:") && lower.matches(".*:[0-9]{2,5}.*"))) {
                    return line;
                }
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    private void showLanInvitePrompt() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(10), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF21A2A33);
        background.setCornerRadius(dp(20));
        background.setStroke(1, 0x335C7F87);
        panel.setBackground(background);
        panel.setElevation(dp(8));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.bworlds);
        icon.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(0x333C4E58);
        iconBackground.setCornerRadius(dp(14));
        icon.setBackground(iconBackground);
        panel.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(10), 0);
        TextView title = new TextView(this);
        title.setText(R.string.battlyworlds_lan_prompt_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView message = new TextView(this);
        message.setText(R.string.battlyworlds_lan_prompt_message);
        message.setTextColor(0xFFB4C5D0);
        message.setTextSize(12);
        message.setMaxLines(2);
        copy.addView(title);
        copy.addView(message);
        panel.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = new TextView(this);
        action.setText(R.string.battlyworlds_lan_prompt_action);
        action.setTextColor(0xFF0B171B);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setTextSize(13);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable actionBackground = new GradientDrawable();
        actionBackground.setColor(0xFF8ADBC6);
        actionBackground.setCornerRadius(dp(14));
        action.setBackground(actionBackground);
        action.setOnClickListener(v -> {
            hideLanInvitePrompt(false);
            openBattlyWorlds(true);
        });
        panel.addView(action);

        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_close_white);
        close.setColorFilter(0xFFFFFFFF);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setOnClickListener(v -> hideLanInvitePrompt(true));
        panel.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));

        int promptWidth = Math.min(dp(360), getResources().getDisplayMetrics().widthPixels - dp(36));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(promptWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(0, dp(42), dp(18), 0);
        mLanInvitePrompt = panel;
        mContentFrame.addView(panel, params);
    }

    private void hideLanInvitePrompt(boolean remember) {
        if (remember) {
            mLanInvitePromptHidden = true;
        }
        if (mLanInvitePrompt != null && mLanInvitePrompt.getParent() instanceof ViewGroup) {
            ((ViewGroup) mLanInvitePrompt.getParent()).removeView(mLanInvitePrompt);
        }
        mLanInvitePrompt = null;
    }

    public static void toggleMouse(Context ctx) {
        if (CallbackBridge.isGrabbing()) return;

        Toast.makeText(ctx, touchpad.switchState()
                        ? R.string.control_mouseon : R.string.control_mouseoff,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(isInEditor) {
            if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) mControlLayout.askToExit(this);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean handleEvent;
        if(!(handleEvent = minecraftGLView.processKeyEvent(event))) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !touchCharInput.isEnabled()) {
                if(event.getAction() != KeyEvent.ACTION_UP) return true; // We eat it anyway
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
                return true;
            }
        }
        return handleEvent;
    }

    public static void switchKeyboardState() {
        if(touchCharInput != null) touchCharInput.switchKeyboardState();
    }

    public static void switchKeyboardState(boolean panning) {
        if (touchCharInput == null) return;
        Context context = touchCharInput.getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.getWindow().setSoftInputMode(panning
                    ? WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                    : WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        touchCharInput.switchKeyboardState();
    }

    @Keep
    public static void openLink(String link) {
        Context ctx = touchpad.getContext(); // no more better way to obtain a context statically
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                if(link.startsWith("file:")) {
                    int truncLength = 5;
                    if(link.startsWith("file://")) truncLength = 7;
                    String path = link.substring(truncLength);
                    Tools.openPath(ctx, new File(path), false);
                }else {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(link), "*/*");
                    ctx.startActivity(intent);
                }
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    @SuppressWarnings("unused") //TODO: actually use it
    public static void openPath(String path) {
        Context ctx = touchpad.getContext(); // no more better way to obtain a context statically
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                Tools.openPath(ctx, new File(path), false);
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    @Keep
    public static void querySystemClipboard() {
        Tools.runOnUiThread(()->{
            ClipData clipData = GLOBAL_CLIPBOARD.getPrimaryClip();
            if(clipData == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            ClipData.Item firstClipItem = clipData.getItemAt(0);
            //TODO: coerce to HTML if the clip item is styled
            CharSequence clipItemText = firstClipItem.getText();
            if(clipItemText == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            AWTInputBridge.nativeClipboardReceived(clipItemText.toString(), "plain");
        });
    }

    @Keep
    public static void putClipboardData(String data, String mimeType) {
        Tools.runOnUiThread(()-> {
            ClipData clipData = null;
            switch(mimeType) {
                case "text/plain":
                    clipData = ClipData.newPlainText("AWT Paste", data);
                    break;
                case "text/html":
                    clipData = ClipData.newHtmlText("AWT Paste", data, data);
            }
            if(clipData != null) GLOBAL_CLIPBOARD.setPrimaryClip(clipData);
        });
    }

    @Override
    public void onClickedMenu() {
        drawerLayout.openDrawer(navDrawer);
        navDrawer.requestLayout();
    }

    @Override
    public void exitEditor() {
        try {
            mControlLayout.loadLayout((CustomControls)null);
            mControlLayout.setModifiable(false);
            System.gc();
            mControlLayout.loadLayout(
                    minecraftProfile.controlFile == null
                            ? LauncherPreferences.PREF_DEFAULTCTRL_PATH
                            : Tools.CTRLMAP_PATH + "/" + minecraftProfile.controlFile);
            mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        } catch (IOException e) {
            Tools.showError(this,e);
        }

        navDrawer.setAdapter(gameActionArrayAdapter);
        navDrawer.setOnItemClickListener(gameActionClickListener);
        isInEditor = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        GameService.LocalBinder localBinder = (GameService.LocalBinder) service;
        mServiceBinder = localBinder;
        updateGameStartingStage(R.string.launcher_starting_stage_surface, 18);
        minecraftGLView.start(localBinder.isActive, touchpad);
        localBinder.isActive = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    /*
     * Android 14 (or some devices, at least) seems to dispatch the the captured mouse events as trackball events
     * due to a bug(?) somewhere(????)
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean checkCaptureDispatchConditions(MotionEvent event) {
        int eventSource = event.getSource();
        // On my device, the mouse sends events as a relative mouse device.
        // Not comparing with == here because apparently `eventSource` is a mask that can
        // sometimes indicate multiple sources, like in the case of InputDevice.SOURCE_TOUCHPAD
        // (which is *also* an InputDevice.SOURCE_MOUSE when controlling a cursor)
        return (eventSource & InputDevice.SOURCE_MOUSE_RELATIVE) != 0 ||
                (eventSource & InputDevice.SOURCE_MOUSE) != 0;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        if(Tools.isAndroid8OrHigher() && checkCaptureDispatchConditions(ev))
            return minecraftGLView.dispatchCapturedPointerEvent(ev);
        else return super.dispatchTrackballEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            Tools.setFullscreen(this, setFullscreen());
            Tools.updateWindowSize(this);
            if (minecraftGLView != null) {
                minecraftGLView.postDelayed(() -> minecraftGLView.refreshSize(true), 250);
            }
        }
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
