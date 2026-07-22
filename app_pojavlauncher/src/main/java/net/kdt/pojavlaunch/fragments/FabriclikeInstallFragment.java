package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.DetachedModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.FabriclikeDownloadTask;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.FabricVersion;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public abstract class FabriclikeInstallFragment extends Fragment {
    private final FabriclikeUtils fabriclikeUtils;
    private FabricVersion[] gameVersions;
    private FabricVersion[] loaderVersions;
    private String selectedGameVersion;
    private ProgressBar progressBar;
    private RecyclerView versionGrid;
    private TextView stageTitle;
    private ImageButton stageBack;
    private View retryView;
    private CheckBox stableOnly;
    private Future<?> loadingTask;

    protected FabriclikeInstallFragment(FabriclikeUtils fabriclikeUtils, String ignoredTag) {
        super(R.layout.fragment_fabric_install);
        this.fabriclikeUtils = fabriclikeUtils;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        progressBar = view.findViewById(R.id.fabric_installer_progress_bar);
        versionGrid = view.findViewById(R.id.fabric_installer_version_grid);
        stageTitle = view.findViewById(R.id.fabric_installer_stage_title);
        stageBack = view.findViewById(R.id.fabric_installer_stage_back);
        retryView = view.findViewById(R.id.fabric_installer_retry_layout);
        stableOnly = view.findViewById(R.id.fabric_installer_only_stable_checkbox);
        versionGrid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        stageBack.setOnClickListener(v -> showGameVersions());
        stableOnly.setOnCheckedChangeListener((button, checked) -> refreshVisibleStage());
        view.findViewById(R.id.fabric_installer_retry_button).setOnClickListener(v -> {
            retryView.setVisibility(View.GONE);
            if (selectedGameVersion == null) loadGameVersions(); else loadLoaderVersions();
        });
        loadGameVersions();
    }

    @Override
    public void onStop() {
        if (loadingTask != null) loadingTask.cancel(true);
        super.onStop();
    }

    private void loadGameVersions() {
        startLoading();
        selectedGameVersion = null;
        loadingTask = PojavApplication.sExecutorService.submit(() -> {
            try {
                FabricVersion[] result = fabriclikeUtils.downloadGameVersions();
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    gameVersions = result;
                    stopLoading();
                    if (result == null) retryView.setVisibility(View.VISIBLE);
                    else showGameVersions();
                });
            } catch (IOException exception) {
                showLoadError(exception);
            }
        });
    }

    private void loadLoaderVersions() {
        startLoading();
        String requestedVersion = selectedGameVersion;
        loadingTask = PojavApplication.sExecutorService.submit(() -> {
            try {
                FabricVersion[] result = fabriclikeUtils.downloadLoaderVersions(requestedVersion);
                Tools.runOnUiThread(() -> {
                    if (!isAdded() || !requestedVersion.equals(selectedGameVersion)) return;
                    loaderVersions = result;
                    stopLoading();
                    if (result == null) retryView.setVisibility(View.VISIBLE);
                    else showLoaderVersions();
                });
            } catch (IOException exception) {
                showLoadError(exception);
            }
        });
    }

    private void showLoadError(Exception exception) {
        Tools.runOnUiThread(() -> {
            if (!isAdded()) return;
            stopLoading();
            Tools.showError(requireContext(), exception);
            retryView.setVisibility(View.VISIBLE);
        });
    }

    private void showGameVersions() {
        selectedGameVersion = null;
        loaderVersions = null;
        stageBack.setVisibility(View.GONE);
        stageTitle.setText(R.string.modloader_select_minecraft_version);
        versionGrid.setAdapter(new FabricVersionAdapter(filtered(gameVersions), true));
        versionGrid.scrollToPosition(0);
    }

    private void showLoaderVersions() {
        stageBack.setVisibility(View.VISIBLE);
        stageTitle.setText(getString(R.string.modloader_select_build_for, selectedGameVersion));
        versionGrid.setAdapter(new FabricVersionAdapter(filtered(loaderVersions), false));
        versionGrid.scrollToPosition(0);
    }

    private void refreshVisibleStage() {
        if (selectedGameVersion == null) showGameVersions();
        else if (loaderVersions != null) showLoaderVersions();
    }

    private List<FabricVersion> filtered(FabricVersion[] source) {
        ArrayList<FabricVersion> result = new ArrayList<>();
        if (source == null) return result;
        for (FabricVersion version : source) {
            if (!stableOnly.isChecked() || version.stable) result.add(version);
        }
        return result;
    }

    private void selectGameVersion(FabricVersion version) {
        selectedGameVersion = version.version;
        loaderVersions = null;
        loadLoaderVersions();
    }

    private void installLoader(FabricVersion loaderVersion) {
        if (ProgressKeeper.hasOngoingTasks()) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        String metadataError = getString(R.string.fabric_dl_cant_read_meta, fabriclikeUtils.getName());
        if (requireActivity() instanceof LauncherActivity) {
            ((LauncherActivity) requireActivity()).showMainMenuProgress();
        }
        JMinecraftVersionList.Version vanillaInfo = AsyncMinecraftDownloader.getListedVersion(selectedGameVersion);
        new MinecraftDownloader().start(requireActivity(), vanillaInfo, selectedGameVersion,
                new AsyncMinecraftDownloader.DoneListener() {
                    @Override
                    public void onDownloadDone() {
                        new Thread(new FabriclikeDownloadTask(
                                new DetachedModloaderDownloadListener(appContext, metadataError,
                                        fabriclikeUtils.getName()),
                                fabriclikeUtils, selectedGameVersion, loaderVersion.version, true)).start();
                    }

                    @Override
                    public void onDownloadFailed(Throwable throwable) {
                        Tools.showError(appContext, throwable instanceof Exception
                                ? (Exception) throwable : new Exception(throwable));
                    }
                });
    }

    private void startLoading() {
        progressBar.setVisibility(View.VISIBLE);
        versionGrid.setEnabled(false);
    }

    private void stopLoading() {
        progressBar.setVisibility(View.GONE);
        versionGrid.setEnabled(true);
    }

    private final class FabricVersionAdapter extends RecyclerView.Adapter<VersionHolder> {
        private final List<FabricVersion> versions;
        private final boolean gameStage;

        FabricVersionAdapter(List<FabricVersion> versions, boolean gameStage) {
            this.versions = versions;
            this.gameStage = gameStage;
        }

        @NonNull
        @Override
        public VersionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VersionHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_modloader_version_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VersionHolder holder, int position) {
            FabricVersion version = versions.get(position);
            holder.title.setText(version.version);
            holder.subtitle.setText(gameStage
                    ? fabriclikeUtils.getName()
                    : getString(R.string.modloader_build_compatible, selectedGameVersion));
            holder.itemView.setOnClickListener(v -> {
                if (gameStage) selectGameVersion(version); else installLoader(version);
            });
        }

        @Override public int getItemCount() { return versions.size(); }
    }

    private static final class VersionHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        VersionHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.modloader_version_title);
            subtitle = itemView.findViewById(R.id.modloader_version_subtitle);
        }
    }
}
