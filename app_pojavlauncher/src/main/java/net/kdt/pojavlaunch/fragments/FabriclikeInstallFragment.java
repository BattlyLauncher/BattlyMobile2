package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.DetachedModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.FabriclikeDownloadTask;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.FabricVersion;
import net.kdt.pojavlaunch.modloaders.modpacks.SelfReferencingFuture;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Future;

public abstract class FabriclikeInstallFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {
    private final FabriclikeUtils mFabriclikeUtils;
    private Spinner mGameVersionSpinner;
    private FabricVersion[] mGameVersionArray;
    private Future<?> mGameVersionFuture;
    private String mSelectedGameVersion;
    private Spinner mLoaderVersionSpinner;
    private FabricVersion[] mLoaderVersionArray;
    private Future<?> mLoaderVersionFuture;
    private String mSelectedLoaderVersion;
    private ProgressBar mProgressBar;
    private Button mStartButton;
    private View mRetryView;
    private CheckBox mOnlyStableCheckbox;
    protected FabriclikeInstallFragment(FabriclikeUtils mFabriclikeUtils, String mFragmentTag) {
        super(R.layout.fragment_fabric_install);
        this.mFabriclikeUtils = mFabriclikeUtils;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStartButton = view.findViewById(R.id.fabric_installer_start_button);
        mStartButton.setOnClickListener(this::onClickStart);
        mGameVersionSpinner = view.findViewById(R.id.fabric_installer_game_ver_spinner);
        mGameVersionSpinner.setOnItemSelectedListener(new GameVersionSelectedListener());
        mLoaderVersionSpinner = view.findViewById(R.id.fabric_installer_loader_ver_spinner);
        mLoaderVersionSpinner.setOnItemSelectedListener(new LoaderVersionSelectedListener());
        mProgressBar = view.findViewById(R.id.fabric_installer_progress_bar);
        mRetryView = view.findViewById(R.id.fabric_installer_retry_layout);
        mOnlyStableCheckbox = view.findViewById(R.id.fabric_installer_only_stable_checkbox);
        mOnlyStableCheckbox.setOnCheckedChangeListener(this);
        view.findViewById(R.id.fabric_installer_retry_button).setOnClickListener(this::onClickRetry);
        ((TextView)view.findViewById(R.id.fabric_installer_label_loader_ver)).setText(getString(R.string.fabric_dl_loader_version, mFabriclikeUtils.getName()));
        updateGameVersions();
    }

    @Override
    public void onStop() {
        cancelFutureChecked(mGameVersionFuture);
        cancelFutureChecked(mLoaderVersionFuture);
        super.onStop();
    }

    private void onClickStart(View v) {
        if(ProgressKeeper.hasOngoingTasks()) {
            Toast.makeText(v.getContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        String metadataError = getString(R.string.fabric_dl_cant_read_meta, mFabriclikeUtils.getName());
        String modloaderName = mFabriclikeUtils.getName();
        if (requireActivity() instanceof LauncherActivity) {
            ((LauncherActivity) requireActivity()).showMainMenuProgress();
        }
        // Download vanilla Minecraft first, then install the modloader on top
        String vanillaVersion = mSelectedGameVersion;
        JMinecraftVersionList.Version vanillaVersionInfo = AsyncMinecraftDownloader.getListedVersion(vanillaVersion);
        new MinecraftDownloader().start(requireActivity(), vanillaVersionInfo, vanillaVersion,
                new AsyncMinecraftDownloader.DoneListener() {
                    @Override
                    public void onDownloadDone() {
                        FabriclikeDownloadTask fabricDownloadTask = new FabriclikeDownloadTask(
                                new DetachedModloaderDownloadListener(
                                        appContext,
                                        metadataError,
                                        modloaderName
                                ),
                                mFabriclikeUtils,
                                mSelectedGameVersion, mSelectedLoaderVersion, true);
                        new Thread(fabricDownloadTask).start();
                    }

                    @Override
                    public void onDownloadFailed(Throwable throwable) {
                        Tools.showError(appContext,
                                throwable instanceof Exception ? (Exception) throwable : new Exception(throwable));
                    }
                });
    }

    private void onClickRetry(View v) {
        mStartButton.setEnabled(false);
        mRetryView.setVisibility(View.GONE);
        mLoaderVersionSpinner.setAdapter(null);
        if(mGameVersionArray == null) {
            mGameVersionSpinner.setAdapter(null);
            updateGameVersions();
            return;
        }
        updateLoaderVersions();
    }

    private void cancelFutureChecked(Future<?> future) {
        if(future != null && !future.isCancelled()) future.cancel(true);
    }

    private void startLoading() {
        mProgressBar.setVisibility(View.VISIBLE);
        mStartButton.setEnabled(false);
    }

    private void stopLoading() {
        mProgressBar.setVisibility(View.GONE);
        // The "visibility on" is managed by the spinners
    }

    private ArrayAdapter<FabricVersion> createAdapter(FabricVersion[] fabricVersions, boolean onlyStable) {
        ArrayList<FabricVersion> filteredVersions = new ArrayList<>(fabricVersions.length);
        for(FabricVersion fabricVersion : fabricVersions) {
            if(!onlyStable || fabricVersion.stable) filteredVersions.add(fabricVersion);
        }
        filteredVersions.trimToSize();
        ArrayAdapter<FabricVersion> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_simple_list_1, filteredVersions);
        adapter.setDropDownViewResource(R.layout.item_simple_list_1);
        return adapter;
    }

    private void onException(Future<?> myFuture, Exception e) {
        Tools.runOnUiThread(()->{
            if(myFuture.isCancelled()) return;
            stopLoading();
            if(e != null) Tools.showError(requireContext(), e);
            mRetryView.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        updateGameSpinner();
        updateLoaderSpinner();
    }

    class LoaderVersionSelectedListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            mSelectedLoaderVersion = ((FabricVersion) adapterView.getAdapter().getItem(i)).version;
            mStartButton.setEnabled(mSelectedGameVersion != null);
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
            mSelectedLoaderVersion = null;
            mStartButton.setEnabled(false);
        }
    }

    class LoadLoaderVersionsTask implements SelfReferencingFuture.FutureInterface {
        @Override
        public void run(Future<?> myFuture) {
            Log.i("LoadLoaderVersions", "Starting...");
            try {
                mLoaderVersionArray = mFabriclikeUtils.downloadLoaderVersions(mSelectedGameVersion);
                if(mLoaderVersionArray != null) onFinished(myFuture);
                else onException(myFuture, null);
            }catch (IOException e) {
                onException(myFuture, e);
            }
        }
        private void onFinished(Future<?> myFuture) {
            Tools.runOnUiThread(()->{
                if(myFuture.isCancelled()) return;
                stopLoading();
                updateLoaderSpinner();
            });
        }
    }

    private void updateLoaderVersions() {
        startLoading();
        mLoaderVersionFuture = new SelfReferencingFuture(new LoadLoaderVersionsTask()).startOnExecutor(PojavApplication.sExecutorService);
    }

    private void updateLoaderSpinner() {
        if(mLoaderVersionArray == null) return;
        mLoaderVersionSpinner.setAdapter(createAdapter(mLoaderVersionArray, mOnlyStableCheckbox.isChecked()));
    }

    class GameVersionSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            mSelectedGameVersion = ((FabricVersion) adapterView.getAdapter().getItem(i)).version;
            cancelFutureChecked(mLoaderVersionFuture);
            updateLoaderVersions();
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
            mSelectedGameVersion = null;
            if(mLoaderVersionFuture != null) mLoaderVersionFuture.cancel(true);
            adapterView.setAdapter(null);
        }

    }

    class LoadGameVersionsTask implements SelfReferencingFuture.FutureInterface {
        @Override
        public void run(Future<?> myFuture) {
            try {
                mGameVersionArray = mFabriclikeUtils.downloadGameVersions();
                if(mGameVersionArray != null) onFinished(myFuture);
                else onException(myFuture, null);
            }catch (IOException e) {
                onException(myFuture, e);
            }
        }
        private void onFinished(Future<?> myFuture) {
            Tools.runOnUiThread(()->{
                if(myFuture.isCancelled()) return;
                stopLoading();
                updateGameSpinner();
            });
        }
    }

    private void updateGameVersions() {
        startLoading();
        mGameVersionFuture = new SelfReferencingFuture(new LoadGameVersionsTask()).startOnExecutor(PojavApplication.sExecutorService);
    }

    private void updateGameSpinner() {
        if(mGameVersionArray == null) return;
        mGameVersionSpinner.setAdapter(createAdapter(mGameVersionArray, mOnlyStableCheckbox.isChecked()));
    }
}
