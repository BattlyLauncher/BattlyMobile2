package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.ForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.ForgeUtils;
import net.kdt.pojavlaunch.modloaders.ForgeVersionListAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ForgeInstallFragment extends ModVersionListFragment<List<String>> {
    public static final String TAG = "ForgeInstallFragment";
    public ForgeInstallFragment() {
        super(TAG);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public int getTitleText() {
        return R.string.forge_dl_select_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.forge_dl_no_installer;
    }

    @Override
    public List<String> loadVersionList() throws IOException {
        return ForgeUtils.downloadForgeVersions();
    }

    @Override
    public ExpandableListAdapter createAdapter(List<String> versionList, LayoutInflater layoutInflater) {
        return new ForgeVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    protected List<String> filterVersionList(List<String> versionList, String query) {
        if (query == null || query.trim().isEmpty()) {
            return versionList;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> filtered = new ArrayList<>();
        for (String version : versionList) {
            if (version != null && version.toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(version);
            }
        }
        return filtered;
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderDownloadListener listener) {
        return new ForgeDownloadTask(requireContext(), listener, (String) selectedVersion);
    }

    @Override
    protected String extractVanillaVersion(Object selectedVersion) {
        // Forge version format: "1.21.8-47.3.0" → MC version is the part before the dash
        String version = (String) selectedVersion;
        int dashIndex = version.indexOf('-');
        return dashIndex > 0 ? version.substring(0, dashIndex) : null;
    }

    @Override
    protected String getSuccessMessageLabel(Object selectedVersion) {
        return "Forge";
    }
}
