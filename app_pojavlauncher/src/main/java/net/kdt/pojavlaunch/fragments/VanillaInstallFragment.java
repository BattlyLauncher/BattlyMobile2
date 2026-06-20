package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

public class VanillaInstallFragment extends Fragment {
    public static final String TAG = "VanillaInstallFragment";

    private TextView mSelectedVersionText;
    private EditText mProfileNameEdit;
    private String mSelectedVersion = null;

    public VanillaInstallFragment() {
        super(R.layout.fragment_vanilla_install);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mSelectedVersionText = view.findViewById(R.id.vanilla_selected_version);
        mProfileNameEdit = view.findViewById(R.id.vanilla_profile_name);
        Button mCreateButton = view.findViewById(R.id.vanilla_create_button);

        view.findViewById(R.id.vanilla_version_selector).setOnClickListener(v ->
                VersionSelectorDialog.open(requireContext(), true, (id, snapshot) -> {
                    mSelectedVersion = id;
                    mSelectedVersionText.setText(id);
                    if (TextUtils.isEmpty(mProfileNameEdit.getText())) {
                        mProfileNameEdit.setText(id);
                    }
                }));

        mCreateButton.setOnClickListener(v -> {
            if (mSelectedVersion == null) {
                Toast.makeText(requireContext(), R.string.vanilla_install_pick_version, Toast.LENGTH_SHORT).show();
                return;
            }
            String name = mProfileNameEdit.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                name = mSelectedVersion;
            }

            MinecraftProfile profile = MinecraftProfile.createTemplate();
            profile.name = name;
            profile.lastVersionId = mSelectedVersion;

            String key = LauncherProfiles.getFreeProfileKey();
            LauncherProfiles.mainProfileJson.profiles.put(key, profile);
            LauncherProfiles.write();
            ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, key);

            // Trigger actual download of the selected version
            final androidx.fragment.app.FragmentActivity activity = requireActivity();
            final String versionToDownload = mSelectedVersion;
            final net.kdt.pojavlaunch.JMinecraftVersionList.Version versionInfo =
                    AsyncMinecraftDownloader.getListedVersion(versionToDownload);
            Toast.makeText(requireContext(), R.string.vanilla_install_downloading, Toast.LENGTH_SHORT).show();
            new MinecraftDownloader().start(activity, versionInfo, versionToDownload, new AsyncMinecraftDownloader.DoneListener() {
                @Override
                public void onDownloadDone() {
                    // Download finished in background — ProgressLayout handles UI feedback
                }
                @Override
                public void onDownloadFailed(Throwable throwable) {
                    Tools.showErrorRemote(throwable.getMessage(), throwable);
                }
            });
            Tools.backToMainMenu(activity);
        });
    }
}
