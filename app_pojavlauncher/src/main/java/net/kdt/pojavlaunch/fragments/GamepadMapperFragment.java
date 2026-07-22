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
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.gamepad.Gamepad;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadMapperAdapter;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadMapStore;
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
    private GamepadMapperAdapter mMapperAdapter;
    private Gamepad mGamepad;
    private boolean mCalibrating;
    private float mRestingAxis;
    private float mReportedFlat = 0.05f;
    public GamepadMapperFragment() {
        super(R.layout.fragment_controller_remapper);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView buttonRecyclerView = view.findViewById(R.id.gamepad_remapper_recycler);
        mMapperAdapter = new GamepadMapperAdapter(view.getContext());
        buttonRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        buttonRecyclerView.setAdapter(mMapperAdapter);
        buttonRecyclerView.setOnKeyListener(this);
        buttonRecyclerView.setOnGenericMotionListener(this);
        buttonRecyclerView.requestFocus();
        mInputManager = new RemapperManager(view.getContext(), mRemapperViewBuilder);
        Spinner grabStateSpinner = view.findViewById(R.id.gamepad_remapper_mode_spinner);
        ArrayAdapter<String> mGrabStateAdapter = new ArrayAdapter<>(view.getContext(), R.layout.support_simple_spinner_dropdown_item);
        mGrabStateAdapter.addAll(getString(R.string.customctrl_visibility_in_menus), getString(R.string.customctrl_visibility_ingame));
        grabStateSpinner.setAdapter(mGrabStateAdapter);
        grabStateSpinner.setSelection(0);
        grabStateSpinner.setOnItemSelectedListener(this);
        view.findViewById(R.id.gamepad_calibrate).setOnClickListener(v -> startCalibration());
        view.findViewById(R.id.gamepad_save_profile).setOnClickListener(v -> saveProfile());
    }

    private void createGamepad(View mainView, InputDevice inputDevice) {
        mGamepad = new Gamepad(mainView, inputDevice, mMapperAdapter, false) {
            @Override
            public void handleGamepadInput(int keycode, float value) {
                if(keycode == KeyEvent.KEYCODE_BUTTON_SELECT) {
                    handleExitButton(value > 0.5);
                }
                super.handleGamepadInput(keycode, value);
            }
        };
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
        mInputManager.handleKeyEventInput(mainView.getContext(), keyEvent, mGamepad);
        return true;
    }

    @Override
    public boolean onGenericMotion(View view, MotionEvent motionEvent) {
        View mainView = getView();
        if(!Gamepad.isGamepadEvent(motionEvent) || mainView == null) return false;
        if (mCalibrating) sampleCalibration(motionEvent);
        if(mGamepad == null) createGamepad(mainView, motionEvent.getDevice());
        mInputManager.handleMotionEventInput(mainView.getContext(), motionEvent, mGamepad);
        return true;
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
        boolean grab = i == 1;
        mMapperAdapter.setGrabState(grab);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}
