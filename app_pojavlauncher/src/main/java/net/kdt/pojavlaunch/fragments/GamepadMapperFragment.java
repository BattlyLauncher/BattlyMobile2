package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.gamepad.AnimatedControllerView;
import net.kdt.pojavlaunch.customcontrols.gamepad.ControllerInputSettings;
import net.kdt.pojavlaunch.customcontrols.gamepad.ControllerRemapperBridge;
import net.kdt.pojavlaunch.customcontrols.gamepad.ControllerSetupWizardDialog;
import net.kdt.pojavlaunch.customcontrols.gamepad.Gamepad;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadActionBindings;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadMapperAdapter;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadMap;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadMapStore;
import net.kdt.pojavlaunch.customcontrols.gamepad.ControllerTypeResolver;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.ControllerProfileManager;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import fr.spse.gamepad_remapper.RemapperManager;
import fr.spse.gamepad_remapper.RemapperView;

public class GamepadMapperFragment extends Fragment implements
        View.OnKeyListener, View.OnGenericMotionListener, AdapterView.OnItemSelectedListener {
    public static final String TAG = "GamepadMapperFragment";
    private final RemapperView.Builder mRemapperViewBuilder = new RemapperView.Builder(null)
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
            .remapDpad(true);
    private final Handler mExitHandler = new Handler(Looper.getMainLooper());
    private final Runnable mExitRunnable = () -> {
        Activity activity = getActivity();
        if(activity == null) return;
        activity.onBackPressed();
    };
    private RemapperManager mInputManager;
    private String mInputManagerDescriptor;
    private GamepadMapperAdapter mMapperAdapter;
    private Gamepad mGamepad;
    private boolean mCalibrating;
    private float mRestingAxis;
    private float mReportedFlat = 0.05f;
    private Spinner mModeSpinner;
    private Spinner mStyleSpinner;
    private Spinner mInputModeSpinner;
    private TextView mDeviceLabel;
    private AnimatedControllerView mControllerVisual;
    private RecyclerView mMappingRecycler;
    private InputDevice mLastInputDevice;
    private ControllerSetupWizardDialog mSetupAssistant;
    private Spinner mScrollUpSpinner;
    private Spinner mScrollDownSpinner;
    private boolean mUpdatingScrollSelectors;
    private ControllerTypeResolver.Style mRequestedStyle = ControllerTypeResolver.Style.AUTO;
    private ControllerTypeResolver.Style mResolvedStyle = ControllerTypeResolver.Style.GENERIC;
    public GamepadMapperFragment() {
        super(R.layout.fragment_controller_remapper);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mMappingRecycler = view.findViewById(R.id.gamepad_remapper_recycler);
        mMapperAdapter = new GamepadMapperAdapter(view.getContext());
        mMappingRecycler.setLayoutManager(new LinearLayoutManager(view.getContext()));
        mMappingRecycler.setAdapter(mMapperAdapter);
        mMappingRecycler.setOnKeyListener(this);
        mMappingRecycler.setOnGenericMotionListener(this);
        mMappingRecycler.requestFocus();
        mInputManager = null;
        mModeSpinner = view.findViewById(R.id.gamepad_remapper_mode_spinner);
        ArrayAdapter<String> mGrabStateAdapter = new ArrayAdapter<>(view.getContext(), R.layout.support_simple_spinner_dropdown_item);
        mGrabStateAdapter.addAll(getString(R.string.controller_mode_menu), getString(R.string.controller_mode_game));
        mModeSpinner.setAdapter(mGrabStateAdapter);
        mModeSpinner.setSelection(0);
        mModeSpinner.setOnItemSelectedListener(this);

        mDeviceLabel = view.findViewById(R.id.gamepad_controller_device);
        mStyleSpinner = view.findViewById(R.id.gamepad_controller_style_spinner);
        ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(view.getContext(), R.layout.support_simple_spinner_dropdown_item);
        styleAdapter.addAll(getString(R.string.controller_style_auto), getString(R.string.controller_style_xbox),
                getString(R.string.controller_style_playstation), getString(R.string.controller_style_switch),
                getString(R.string.controller_style_generic));
        mStyleSpinner.setAdapter(styleAdapter);
        mRequestedStyle = ControllerTypeResolver.Style.fromPreference(
                LauncherPreferences.DEFAULT_PREF.getString(ControllerTypeResolver.PREFERENCE_KEY, "auto"));
        mStyleSpinner.setSelection(mRequestedStyle.ordinal());
        mStyleSpinner.setOnItemSelectedListener(this);

        mInputModeSpinner = view.findViewById(R.id.gamepad_input_mode_spinner);
        ArrayAdapter<String> inputModeAdapter = new ArrayAdapter<>(view.getContext(),
                R.layout.support_simple_spinner_dropdown_item);
        inputModeAdapter.addAll(getString(R.string.controller_input_mode_battly),
                getString(R.string.controller_input_mode_native));
        mInputModeSpinner.setAdapter(inputModeAdapter);
        mInputModeSpinner.setSelection(ControllerInputSettings.MODE_NATIVE.equals(
                ControllerInputSettings.normalizeMode(LauncherPreferences.PREF_GAMEPAD_INPUT_MODE)) ? 1 : 0);
        mInputModeSpinner.setOnItemSelectedListener(this);

        mControllerVisual = view.findViewById(R.id.gamepad_controller_visual);
        mControllerVisual.setOnControlSelectedListener(position ->
                mMapperAdapter.focusMapping(position, mMappingRecycler));
        configureCameraControls(view);
        configureScrollControls(view);
        updateControllerStyle(null);
        view.findViewById(R.id.gamepad_setup_assistant).setOnClickListener(v -> startSetupAssistant());
        view.findViewById(R.id.gamepad_calibrate).setOnClickListener(v -> startCalibration());
        view.findViewById(R.id.gamepad_save_profile).setOnClickListener(v -> saveProfile());
    }

    private void configureScrollControls(View view) {
        mScrollUpSpinner = view.findViewById(R.id.gamepad_scroll_up_spinner);
        mScrollDownSpinner = view.findViewById(R.id.gamepad_scroll_down_spinner);
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                if (mUpdatingScrollSelectors) return;
                GamepadMap map = mModeSpinner.getSelectedItemPosition() == 1
                        ? GamepadMapStore.getGameMap() : GamepadMapStore.getMenuMap();
                short action = parent == mScrollUpSpinner
                        ? GamepadMap.MOUSE_SCROLL_UP : GamepadMap.MOUSE_SCROLL_DOWN;
                GamepadActionBindings.assign(map, action, position);
                try {
                    GamepadMapStore.save();
                    mMapperAdapter.refreshMappings();
                } catch (Exception exception) {
                    Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        mScrollUpSpinner.setOnItemSelectedListener(listener);
        mScrollDownSpinner.setOnItemSelectedListener(listener);
        refreshScrollSelectors();
    }

    private void refreshScrollSelectors() {
        if (mScrollUpSpinner == null || mScrollDownSpinner == null) return;
        String[] labels = GamepadMapperAdapter.getControlLabels(requireContext(), mResolvedStyle);
        String[] upLabels = new String[labels.length];
        String[] downLabels = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            upLabels[i] = getString(R.string.controller_scroll_up_control, labels[i]);
            downLabels[i] = getString(R.string.controller_scroll_down_control, labels[i]);
        }
        ArrayAdapter<String> upAdapter = new ArrayAdapter<>(requireContext(),
                R.layout.support_simple_spinner_dropdown_item, upLabels);
        ArrayAdapter<String> downAdapter = new ArrayAdapter<>(requireContext(),
                R.layout.support_simple_spinner_dropdown_item, downLabels);
        GamepadMap map = mModeSpinner != null && mModeSpinner.getSelectedItemPosition() == 1
                ? GamepadMapStore.getGameMap() : GamepadMapStore.getMenuMap();
        mUpdatingScrollSelectors = true;
        mScrollUpSpinner.setAdapter(upAdapter);
        mScrollDownSpinner.setAdapter(downAdapter);
        int up = GamepadActionBindings.find(map, GamepadMap.MOUSE_SCROLL_UP);
        int down = GamepadActionBindings.find(map, GamepadMap.MOUSE_SCROLL_DOWN);
        if (up >= 0) mScrollUpSpinner.setSelection(up, false);
        if (down >= 0) mScrollDownSpinner.setSelection(down, false);
        mScrollUpSpinner.post(() -> mUpdatingScrollSelectors = false);
    }

    private void configureCameraControls(View view) {
        SeekBar sensitivity = view.findViewById(R.id.gamepad_camera_sensitivity);
        TextView sensitivityValue = view.findViewById(R.id.gamepad_camera_sensitivity_value);
        int initialSensitivity = Math.max(25, Math.min(300,
                Math.round(LauncherPreferences.PREF_GAMEPAD_CAMERA_SENSITIVITY * 100f)));
        sensitivity.setProgress(initialSensitivity - 25);
        sensitivityValue.setText(getString(R.string.controller_camera_percent, initialSensitivity));
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 25;
                sensitivityValue.setText(getString(R.string.controller_camera_percent, value));
                if (!fromUser) return;
                LauncherPreferences.PREF_GAMEPAD_CAMERA_SENSITIVITY = value / 100f;
                LauncherPreferences.DEFAULT_PREF.edit().putInt("gamepadCameraSensitivity", value).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        CheckBox invertX = view.findViewById(R.id.gamepad_camera_invert_x);
        CheckBox invertY = view.findViewById(R.id.gamepad_camera_invert_y);
        invertX.setChecked(LauncherPreferences.PREF_GAMEPAD_CAMERA_INVERT_X);
        invertY.setChecked(LauncherPreferences.PREF_GAMEPAD_CAMERA_INVERT_Y);
        invertX.setOnCheckedChangeListener((button, checked) -> {
            LauncherPreferences.PREF_GAMEPAD_CAMERA_INVERT_X = checked;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("gamepadCameraInvertX", checked).apply();
        });
        invertY.setOnCheckedChangeListener((button, checked) -> {
            LauncherPreferences.PREF_GAMEPAD_CAMERA_INVERT_Y = checked;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("gamepadCameraInvertY", checked).apply();
        });
    }

    private void createGamepad(View mainView, InputDevice inputDevice) {
        mLastInputDevice = inputDevice;
        updateControllerStyle(inputDevice);
        mGamepad = new Gamepad(mainView, inputDevice, mMapperAdapter, false, mRequestedStyle) {
            @Override
            public void handleGamepadInput(int keycode, float value) {
                int normalizedCode = ControllerTypeResolver.normalizeKeyCode(mResolvedStyle, keycode);
                if (keycode == MotionEvent.AXIS_Z || keycode == MotionEvent.AXIS_RZ
                        || keycode == MotionEvent.AXIS_RX || keycode == MotionEvent.AXIS_RY) {
                    mControllerVisual.showRightStick(keycode, value);
                } else {
                    mControllerVisual.showInput(normalizedCode, value);
                }
                if(normalizedCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
                    handleExitButton(value > 0.5);
                }
                super.handleGamepadInput(keycode, value);
            }
        };
    }

    private RemapperManager getInputManager(InputDevice device) {
        String descriptor = device == null ? "" : device.getDescriptor();
        if (mInputManager == null || !descriptor.equals(mInputManagerDescriptor)) {
            ControllerRemapperBridge.ensureProfile(requireContext(), device);
            mInputManager = new RemapperManager(requireContext(), mRemapperViewBuilder);
            mInputManagerDescriptor = descriptor;
        }
        return mInputManager;
    }

    private void updateControllerStyle(InputDevice device) {
        mResolvedStyle = ControllerTypeResolver.resolve(mRequestedStyle, device);
        mMapperAdapter.setControllerStyle(mResolvedStyle);
        if (mControllerVisual != null) {
            mControllerVisual.setControllerDevice(device);
            mControllerVisual.setControllerStyle(mResolvedStyle);
        }
        refreshScrollSelectors();
        if (device == null) {
            mDeviceLabel.setText(mRequestedStyle == ControllerTypeResolver.Style.AUTO
                    ? R.string.controller_device_waiting
                    : R.string.controller_device_manual);
        } else {
            mDeviceLabel.setText(getString(R.string.controller_device_detected, device.getName()));
        }
    }

    private void handleExitButton(boolean isPressed) {
        if(isPressed) mExitHandler.postDelayed(mExitRunnable, 400);
        else mExitHandler.removeCallbacks(mExitRunnable);
    }

    @Override
    public boolean onKey(View view, int i, KeyEvent keyEvent) {
        View mainView = getView();
        if(!Gamepad.isGamepadEvent(keyEvent) || mainView == null) return false;
        if(mGamepad == null) createGamepad(mainView, keyEvent.getDevice());
        getInputManager(keyEvent.getDevice()).handleKeyEventInput(
                mainView.getContext(), keyEvent, mGamepad);
        return true;
    }

    @Override
    public boolean onGenericMotion(View view, MotionEvent motionEvent) {
        View mainView = getView();
        if(!Gamepad.isGamepadEvent(motionEvent) || mainView == null) return false;
        if (mCalibrating) sampleCalibration(motionEvent);
        if(mGamepad == null) createGamepad(mainView, motionEvent.getDevice());
        getInputManager(motionEvent.getDevice()).handleMotionEventInput(
                mainView.getContext(), motionEvent, mGamepad);
        return true;
    }

    private void startSetupAssistant() {
        InputDevice device = mLastInputDevice != null ? mLastInputDevice : findConnectedGamepad();
        launchSetupAssistant(device);
    }

    private InputDevice findConnectedGamepad() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                return device;
            }
        }
        return null;
    }

    private void launchSetupAssistant(InputDevice device) {
        if (!isAdded()) return;
        mSetupAssistant = new ControllerSetupWizardDialog(requireContext(), device, mRequestedStyle,
                new ControllerSetupWizardDialog.Listener() {
                    @Override
                    public void onCompleted(fr.spse.gamepad_remapper.Remapper remapper, InputDevice configuredDevice,
                                            ControllerTypeResolver.Style configuredStyle) {
                        mSetupAssistant = null;
                        if (!isAdded() || remapper == null || configuredDevice == null) return;
                        mLastInputDevice = configuredDevice;
                        updateControllerStyle(configuredDevice);
                        remapper.save(requireContext(), configuredDevice.getDescriptor());
                        mInputManager = new RemapperManager(requireContext(), mRemapperViewBuilder);
                        mInputManagerDescriptor = configuredDevice.getDescriptor();
                        LauncherPreferences.PREF_GAMEPAD_INPUT_MODE = ControllerInputSettings.MODE_BATTLY;
                        LauncherPreferences.DEFAULT_PREF.edit()
                                .putString("gamepadInputMode", ControllerInputSettings.MODE_BATTLY).apply();
                        mInputModeSpinner.setSelection(0);
                        GamepadMapStore.applyMinecraftDefaults(configuredStyle);
                        saveProfile();
                        mMapperAdapter.refreshMappings();
                        refreshScrollSelectors();
                        if (mGamepad != null) {
                            mGamepad.removeSelf();
                            mGamepad = null;
                        }
                        Toast.makeText(requireContext(), R.string.controller_setup_complete, Toast.LENGTH_LONG).show();
                        requestMapperFocus();
                    }

                    @Override
                    public void onCancelled() {
                        mSetupAssistant = null;
                        requestMapperFocus();
                    }
                });
        mSetupAssistant.show();
    }

    private void requestMapperFocus() {
        if (mMappingRecycler != null) mMappingRecycler.post(mMappingRecycler::requestFocus);
    }

    private void startCalibration() {
        mCalibrating = true;
        mRestingAxis = 0f;
        mReportedFlat = 0.05f;
        Toast.makeText(requireContext(), R.string.controller_calibrate_release, Toast.LENGTH_LONG).show();
        new Handler(Looper.getMainLooper()).postDelayed(this::finishCalibration, 2500);
    }

    private void sampleCalibration(MotionEvent event) {
        int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_RX, MotionEvent.AXIS_RY};
        InputDevice device = event.getDevice();
        for (int axis : axes) {
            mRestingAxis = Math.max(mRestingAxis, Math.abs(event.getAxisValue(axis)));
            if (device != null) {
                InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
                if (range != null && range.getFlat() > 0) mReportedFlat = Math.max(mReportedFlat, range.getFlat());
            }
        }
    }

    private void finishCalibration() {
        if (!mCalibrating || !isAdded()) return;
        mCalibrating = false;
        int scale = Math.max(50, Math.min(200,
                (int) Math.ceil(Math.max(mReportedFlat, mRestingAxis * 1.35f) / mReportedFlat * 100f)));
        LauncherPreferences.DEFAULT_PREF.edit().putInt("gamepad_deadzone_scale", scale).apply();
        LauncherPreferences.loadPreferences(requireContext());
        Toast.makeText(requireContext(), getString(R.string.controller_calibrate_done, scale), Toast.LENGTH_LONG).show();
    }

    private void saveProfile() {
        try {
            GamepadMapStore.getGameMap();
            GamepadMapStore.save();
            ControllerProfileManager.save(LauncherProfiles.getCurrentProfile());
            Toast.makeText(requireContext(), R.string.controller_profile_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (adapterView == mStyleSpinner) {
            ControllerTypeResolver.Style selected = ControllerTypeResolver.Style.values()[i];
            if (mRequestedStyle != selected) {
                mRequestedStyle = selected;
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(ControllerTypeResolver.PREFERENCE_KEY, selected.preferenceValue).apply();
                if (mGamepad != null) {
                    mGamepad.removeSelf();
                    mGamepad = null;
                }
                updateControllerStyle(mLastInputDevice);
            }
            return;
        }
        if (adapterView == mInputModeSpinner) {
            String mode = i == 1 ? ControllerInputSettings.MODE_NATIVE : ControllerInputSettings.MODE_BATTLY;
            LauncherPreferences.PREF_GAMEPAD_INPUT_MODE = mode;
            LauncherPreferences.DEFAULT_PREF.edit().putString("gamepadInputMode", mode).apply();
            return;
        }
        boolean grab = i == 1;
        mMapperAdapter.setGrabState(grab);
        refreshScrollSelectors();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void onDestroyView() {
        mExitHandler.removeCallbacksAndMessages(null);
        if (mGamepad != null) {
            mGamepad.removeSelf();
            mGamepad = null;
        }
        mControllerVisual = null;
        mMappingRecycler = null;
        if (mSetupAssistant != null) {
            mSetupAssistant.dismiss();
            mSetupAssistant = null;
        }
        mScrollUpSpinner = null;
        mScrollDownSpinner = null;
        super.onDestroyView();
    }
}
