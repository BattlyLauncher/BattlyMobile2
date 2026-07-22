package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class DownloadCenterFragment extends Fragment {
    public static final String TAG = "DownloadCenterFragment";

    public DownloadCenterFragment() {
        super(R.layout.fragment_download_center);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.download_panel_minecraft_versions)
                .setOnClickListener(v -> Tools.swapFragment(requireActivity(), DownloadVersionSelectorFragment.class,
                        DownloadVersionSelectorFragment.TAG, null));
        view.findViewById(R.id.download_panel_install_jar).setOnClickListener(v -> runInstallerWithConfirmation());
        view.findViewById(R.id.download_panel_open_directory).setOnClickListener(v -> openCurrentProfileDirectory());
        view.findViewById(R.id.download_panel_share_logs).setOnClickListener(
                v -> Tools.swapFragment(requireActivity(), LogViewerFragment.class, LogViewerFragment.TAG, null));
    }

    private void openCurrentProfileDirectory() {
        if (Tools.isDemoProfile(requireContext())) {
            hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported),
                    getString(R.string.change_account));
        } else {
            Tools.swapFragment(requireActivity(), BattlyFileManagerFragment.class,
                    BattlyFileManagerFragment.TAG,
                    BattlyFileManagerFragment.createArguments(getCurrentProfileDirectory()));
        }
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                null);
        if (!Tools.isValidString(currentProfile))
            return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if (profileObject == null)
            return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            Tools.installMod(requireActivity(), false);
        } else {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
        }
    }
}
