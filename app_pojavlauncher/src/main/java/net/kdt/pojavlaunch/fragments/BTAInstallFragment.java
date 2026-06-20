package net.kdt.pojavlaunch.fragments;

import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.BTADownloadTask;
import net.kdt.pojavlaunch.modloaders.BTAUtils;
import net.kdt.pojavlaunch.modloaders.BTAVersionListAdapter;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;

import java.io.IOException;

public class BTAInstallFragment extends ModVersionListFragment<BTAUtils.BTAVersionList> {
    public static final String TAG = "BTAInstallFragment";

    public BTAInstallFragment() {
        super(TAG);
    }

    @Override
    public int getTitleText() {
        return R.string.select_bta_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.modloader_dl_failed_to_load_list;
    }

    @Override
    public BTAUtils.BTAVersionList loadVersionList() throws IOException {
        return BTAUtils.downloadVersionList();
    }

    @Override
    public ExpandableListAdapter createAdapter(BTAUtils.BTAVersionList versionList, LayoutInflater layoutInflater) {
        return new BTAVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderDownloadListener listener) {
        return new BTADownloadTask(listener, (BTAUtils.BTAVersion) selectedVersion);
    }

    @Override
    protected String getSuccessMessageLabel(Object selectedVersion) {
        return "BTA";
    }
}
