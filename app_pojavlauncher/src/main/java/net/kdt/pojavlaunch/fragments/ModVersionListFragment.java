package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.DetachedModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;

import java.io.File;
import java.io.IOException;

public abstract class ModVersionListFragment<T> extends Fragment implements Runnable, View.OnClickListener, ExpandableListView.OnChildClickListener {
    private ExpandableListView mExpandableListView;
    private ProgressBar mProgressBar;
    private EditText mSearchField;
    private LayoutInflater mInflater;
    private View mRetryView;
    private T mLoadedVersions;

    public ModVersionListFragment(String mFragmentTag) {
        super(R.layout.fragment_mod_version_list);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.mInflater = LayoutInflater.from(context);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mProgressBar = view.findViewById(R.id.mod_dl_list_progress);
        mSearchField = view.findViewById(R.id.mod_dl_version_search);
        mExpandableListView = view.findViewById(R.id.mod_dl_expandable_version_list);
        mExpandableListView.setOnChildClickListener(this);
        mRetryView = view.findViewById(R.id.mod_dl_retry_layout);
        view.findViewById(R.id.forge_installer_retry_button).setOnClickListener(this);
        mSearchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAdapter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {
            T versions = loadVersionList();
            Tools.runOnUiThread(()->{
                if(versions != null) {
                    mLoadedVersions = versions;
                    updateAdapter(mSearchField == null ? "" : mSearchField.getText().toString());
                }else{
                    mRetryView.setVisibility(View.VISIBLE);
                }
                mProgressBar.setVisibility(View.GONE);
            });
        }catch (IOException e) {
            Tools.runOnUiThread(()-> {
                if (getContext() != null) {
                    Tools.showError(getContext(), e);
                    mRetryView.setVisibility(View.VISIBLE);
                    mProgressBar.setVisibility(View.GONE);
                }
            });
        }
    }

    @Override
    public void onClick(View view) {
        mRetryView.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        new Thread(this).start();
    }

    private void updateAdapter(String query) {
        if (mLoadedVersions == null || mExpandableListView == null || mInflater == null) {
            return;
        }
        T filteredVersions = filterVersionList(mLoadedVersions, query == null ? "" : query.trim());
        ExpandableListAdapter adapter = createAdapter(filteredVersions, mInflater);
        mExpandableListView.setAdapter(adapter);
        if (query != null && !query.trim().isEmpty()) {
            for (int i = 0; i < adapter.getGroupCount(); i++) {
                mExpandableListView.expandGroup(i);
            }
        } else if (adapter.getGroupCount() > 0) {
            mExpandableListView.expandGroup(0);
        }
    }

    @Override
    public boolean onChildClick(ExpandableListView expandableListView, View view, int i, int i1, long l) {
        if(ProgressKeeper.hasOngoingTasks()) {
            Toast.makeText(expandableListView.getContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return true;
        }
        Object selectedVersion = expandableListView.getExpandableListAdapter().getChild(i, i1);
        ModloaderDownloadListener listener = new DetachedModloaderDownloadListener(
                requireContext(),
                getString(getNoDataMsg()),
                getSuccessMessageLabel(selectedVersion)
        );

        String vanillaVersion = extractVanillaVersion(selectedVersion);
        if (vanillaVersion != null) {
            JMinecraftVersionList.Version versionInfo = AsyncMinecraftDownloader.getListedVersion(vanillaVersion);
            if (requireActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).showMainMenuProgress();
            }
            Runnable modloaderTask = createDownloadTask(selectedVersion, listener);
            new MinecraftDownloader().start(requireActivity(), versionInfo, vanillaVersion,
                    new AsyncMinecraftDownloader.DoneListener() {
                        @Override
                        public void onDownloadDone() {
                            new Thread(modloaderTask).start();
                        }
                        @Override
                        public void onDownloadFailed(Throwable throwable) {
                            Tools.showError(requireContext(),
                                    throwable instanceof Exception ? (Exception) throwable : new Exception(throwable));
                        }
                    });
        } else {
            Runnable downloadTask = createDownloadTask(selectedVersion, listener);
            new Thread(downloadTask).start();
            if (requireActivity() instanceof LauncherActivity) {
                ((LauncherActivity) requireActivity()).showMainMenuProgress();
            }
        }
        return true;
    }

    /**
     * Override to return the vanilla Minecraft version that must be pre-downloaded
     * before the modloader installer runs. Return null to skip pre-download.
     */
    @Nullable
    protected String extractVanillaVersion(Object selectedVersion) {
        return null;
    }

    public abstract int getTitleText();
    public abstract int getNoDataMsg();

    public abstract T loadVersionList() throws IOException;

    public abstract ExpandableListAdapter createAdapter(T versionList, LayoutInflater layoutInflater);
    public abstract Runnable createDownloadTask(Object selectedVersion, ModloaderDownloadListener listener);

    protected T filterVersionList(T versionList, String query) {
        return versionList;
    }

    @Nullable
    protected String getSuccessMessageLabel(Object selectedVersion) {
        return null;
    }
}
