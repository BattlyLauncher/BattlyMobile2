package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class DownloadVersionSelectorFragment extends Fragment {
    public static final String TAG = "DownloadVersionSelector";

    public DownloadVersionSelectorFragment() {
        super(R.layout.fragment_download_version_selector);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindInstall(view, R.id.download_version_vanilla, VanillaInstallFragment.class, VanillaInstallFragment.TAG, null);
        bindInstall(view, R.id.download_version_forge, ForgeInstallFragment.class, ForgeInstallFragment.TAG, null);
        bindInstall(view, R.id.download_version_fabric, FabricInstallFragment.class, FabricInstallFragment.TAG, null);
        bindInstall(view, R.id.download_version_quilt, QuiltInstallFragment.class, QuiltInstallFragment.TAG, null);
        bindInstall(view, R.id.download_version_optifine, OptiFineInstallFragment.class, OptiFineInstallFragment.TAG, null);
        bindInstall(view, R.id.download_version_neoforge, NeoForgeInstallFragment.class, NeoForgeInstallFragment.TAG, null);
        bindInstall(view, R.id.download_version_legacyfabric, LegacyFabricInstallFragment.class, LegacyFabricInstallFragment.TAG, null);

        View clients = view.findViewById(R.id.download_version_clients);
        clients.setOnClickListener(v -> Tools.swapFragment(requireActivity(), BattlyClientInstallFragment.class, BattlyClientInstallFragment.TAG, null));
    }

    private void bindInstall(@NonNull View root, int id, @NonNull Class<? extends Fragment> fragmentClass, @NonNull String tag, @Nullable Bundle arguments) {
        root.findViewById(id).setOnClickListener(v -> Tools.swapFragment(requireActivity(), fragmentClass, tag, arguments));
    }
}
