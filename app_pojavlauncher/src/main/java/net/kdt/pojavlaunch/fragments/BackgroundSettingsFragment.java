package net.kdt.pojavlaunch.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.utils.BattlyBackgrounds;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;

public class BackgroundSettingsFragment extends Fragment {
    public static final String TAG = "BackgroundSettingsFragment";

    private GridLayout mGrid;
    private TextView mPlusStatus;
    private final ActivityResultLauncher<String[]> mPickBackgroundLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onCustomBackgroundPicked);

    public BackgroundSettingsFragment() {
        super(R.layout.fragment_background_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mGrid = view.findViewById(R.id.background_settings_grid);
        mPlusStatus = view.findViewById(R.id.background_plus_status);
        bindPlusStatus(BattlyPlusManager.isPlus(requireContext()));
        populateGrid();
        BattlyPlusManager.refreshAsync(requireContext(), plus -> {
            if (!isAdded()) {
                return;
            }
            bindPlusStatus(plus);
            populateGrid();
        });
    }

    private void populateGrid() {
        BattlyBackgrounds.populateOptions(
                requireContext(),
                mGrid,
                requireActivity().findViewById(R.id.launcher_background),
                this::applyLauncherBackground,
                true,
                () -> {
                    if (!BattlyPlusManager.isPlus(requireContext())) {
                        Toast.makeText(requireContext(), R.string.battly_plus_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mPickBackgroundLauncher.launch(new String[]{
                            "image/*",
                            "image/gif",
                            "video/*",
                            "video/mp4",
                            "video/webm"
                    });
                });
    }

    private void onCustomBackgroundPicked(@Nullable Uri uri) {
        if (uri == null || !isAdded()) {
            return;
        }
        try {
            BattlyBackgrounds.saveCustomBackground(requireContext(), uri);
            applyLauncherBackground();
            populateGrid();
            Toast.makeText(requireContext(), R.string.battly_background_applied, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Tools.showError(requireContext(), getString(R.string.battly_background_custom_failed), e);
        }
    }

    private void bindPlusStatus(boolean plus) {
        if (mPlusStatus == null) {
            return;
        }
        mPlusStatus.setText(plus
                ? R.string.battly_background_plus_active
                : R.string.battly_background_plus_locked_desc);
        mPlusStatus.setTextColor(plus ? 0xFF8BE7D4 : 0xFFFFD65A);
    }

    private void applyLauncherBackground() {
        if (requireActivity() instanceof LauncherActivity) {
            ((LauncherActivity) requireActivity()).applyLauncherBackground();
        } else {
            BattlyBackgrounds.applySelectedBackground(requireContext(), requireActivity().findViewById(R.id.launcher_background));
        }
    }
}
