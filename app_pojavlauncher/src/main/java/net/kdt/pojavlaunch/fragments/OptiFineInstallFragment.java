package net.kdt.pojavlaunch.fragments;

import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.OptiFineDownloadTask;
import net.kdt.pojavlaunch.modloaders.OptiFineUtils;
import net.kdt.pojavlaunch.modloaders.OptiFineVersionListAdapter;

import java.io.IOException;

public class OptiFineInstallFragment extends ModVersionListFragment<OptiFineUtils.OptiFineVersions> {
    public static final String TAG = "OptiFineInstallFragment";
    public OptiFineInstallFragment() {
        super(TAG);
    }
    @Override
    public int getTitleText() {
        return R.string.of_dl_select_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.of_dl_failed_to_scrape;
    }
    @Override
    public OptiFineUtils.OptiFineVersions loadVersionList() throws IOException {
        return OptiFineUtils.downloadOptiFineVersions();
    }

    @Override
    public ExpandableListAdapter createAdapter(OptiFineUtils.OptiFineVersions versionList, LayoutInflater layoutInflater) {
        return new OptiFineVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderDownloadListener listener) {
        return new OptiFineDownloadTask((OptiFineUtils.OptiFineVersion) selectedVersion, listener, requireActivity());
    }

    @Override
    protected String getSuccessMessageLabel(Object selectedVersion) {
        return "OptiFine";
    }
}
