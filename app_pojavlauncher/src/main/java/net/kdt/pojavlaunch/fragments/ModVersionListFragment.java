package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ExpandableListAdapter;
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
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.DetachedModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;

import java.io.IOException;

public abstract class ModVersionListFragment<T> extends Fragment implements Runnable, View.OnClickListener {
    private ProgressBar progressBar;
    private EditText searchField;
    private RecyclerView versionGrid;
    private TextView stageTitle;
    private ImageButton stageBack;
    private LayoutInflater inflater;
    private View retryView;
    private T loadedVersions;
    private ExpandableListAdapter groupedAdapter;
    private int selectedGroup = -1;

    public ModVersionListFragment(String ignoredFragmentTag) {
        super(R.layout.fragment_mod_version_list);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        inflater = LayoutInflater.from(context);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        progressBar = view.findViewById(R.id.mod_dl_list_progress);
        searchField = view.findViewById(R.id.mod_dl_version_search);
        versionGrid = view.findViewById(R.id.mod_dl_version_grid);
        stageTitle = view.findViewById(R.id.mod_dl_stage_title);
        stageBack = view.findViewById(R.id.mod_dl_stage_back);
        retryView = view.findViewById(R.id.mod_dl_retry_layout);
        versionGrid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        stageBack.setOnClickListener(v -> showMinecraftVersions());
        view.findViewById(R.id.forge_installer_retry_button).setOnClickListener(this);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAdapter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            Tools.hideKeyboard(v);
            return true;
        });
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {
            T versions = loadVersionList();
            Tools.runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                if (versions != null) {
                    loadedVersions = versions;
                    updateAdapter(searchField.getText().toString());
                } else {
                    retryView.setVisibility(View.VISIBLE);
                }
                progressBar.setVisibility(View.GONE);
            });
        } catch (IOException exception) {
            Tools.runOnUiThread(() -> {
                Context context = getContext();
                if (!isAdded() || context == null) return;
                Tools.showError(context, exception);
                retryView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
            });
        }
    }

    @Override
    public void onClick(View view) {
        retryView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        new Thread(this).start();
    }

    private void updateAdapter(String query) {
        if (loadedVersions == null || versionGrid == null || inflater == null) return;
        T filtered = filterVersionList(loadedVersions, query == null ? "" : query.trim());
        groupedAdapter = createAdapter(filtered, inflater);
        selectedGroup = -1;
        showMinecraftVersions();
    }

    private void showMinecraftVersions() {
        selectedGroup = -1;
        stageBack.setVisibility(View.GONE);
        stageTitle.setText(R.string.modloader_select_minecraft_version);
        versionGrid.setAdapter(new StageAdapter(true));
    }

    private void showLoaderVersions(int group) {
        selectedGroup = group;
        stageBack.setVisibility(View.VISIBLE);
        stageTitle.setText(getString(R.string.modloader_select_build_for,
                String.valueOf(groupedAdapter.getGroup(group))));
        versionGrid.setAdapter(new StageAdapter(false));
        versionGrid.scrollToPosition(0);
    }

    private void installSelectedVersion(Object selectedVersion) {
        if (ProgressKeeper.hasOngoingTasks()) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }
        Context context = getContext();
        if (context == null || getActivity() == null) return;
        ModloaderDownloadListener listener = new DetachedModloaderDownloadListener(
                context, getString(getNoDataMsg()), getSuccessMessageLabel(selectedVersion));
        String vanillaVersion = extractVanillaVersion(selectedVersion);
        if (vanillaVersion != null) {
            JMinecraftVersionList.Version versionInfo = AsyncMinecraftDownloader.getListedVersion(vanillaVersion);
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) getActivity()).showMainMenuProgress();
            }
            Runnable modloaderTask = createDownloadTask(selectedVersion, listener);
            new MinecraftDownloader().start(getActivity(), versionInfo, vanillaVersion,
                    new AsyncMinecraftDownloader.DoneListener() {
                        @Override public void onDownloadDone() { new Thread(modloaderTask).start(); }
                        @Override public void onDownloadFailed(Throwable throwable) {
                            Context callbackContext = getContext();
                            if (isAdded() && callbackContext != null) {
                                Tools.showError(callbackContext, throwable instanceof Exception
                                        ? (Exception) throwable : new Exception(throwable));
                            }
                        }
                    });
        } else {
            new Thread(createDownloadTask(selectedVersion, listener)).start();
            if (getActivity() instanceof LauncherActivity) {
                ((LauncherActivity) getActivity()).showMainMenuProgress();
            }
        }
    }

    private final class StageAdapter extends RecyclerView.Adapter<StageHolder> {
        private final boolean minecraftStage;

        StageAdapter(boolean minecraftStage) {
            this.minecraftStage = minecraftStage;
        }

        @NonNull
        @Override
        public StageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new StageHolder(inflater.inflate(R.layout.item_modloader_version_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull StageHolder holder, int position) {
            if (minecraftStage) {
                holder.title.setText(String.valueOf(groupedAdapter.getGroup(position)));
                holder.subtitle.setText(getResources().getQuantityString(
                        R.plurals.modloader_available_builds,
                        groupedAdapter.getChildrenCount(position), groupedAdapter.getChildrenCount(position)));
                holder.itemView.setOnClickListener(v -> showLoaderVersions(position));
            } else {
                Object child = groupedAdapter.getChild(selectedGroup, position);
                View childView = groupedAdapter.getChildView(selectedGroup, position, false, null, holder.container);
                TextView sourceTitle = childView.findViewById(R.id.modloader_child_title);
                holder.title.setText(sourceTitle == null ? String.valueOf(child) : sourceTitle.getText());
                holder.subtitle.setText(getString(R.string.modloader_build_compatible,
                        String.valueOf(groupedAdapter.getGroup(selectedGroup))));
                holder.itemView.setOnClickListener(v -> installSelectedVersion(child));
            }
        }

        @Override
        public int getItemCount() {
            if (groupedAdapter == null) return 0;
            return minecraftStage ? groupedAdapter.getGroupCount()
                    : groupedAdapter.getChildrenCount(selectedGroup);
        }
    }

    private static final class StageHolder extends RecyclerView.ViewHolder {
        final ViewGroup container;
        final TextView title;
        final TextView subtitle;

        StageHolder(@NonNull View itemView) {
            super(itemView);
            container = (ViewGroup) itemView;
            title = itemView.findViewById(R.id.modloader_version_title);
            subtitle = itemView.findViewById(R.id.modloader_version_subtitle);
        }
    }

    @Nullable
    protected String extractVanillaVersion(Object selectedVersion) { return null; }

    public abstract int getTitleText();
    public abstract int getNoDataMsg();
    public abstract T loadVersionList() throws IOException;
    public abstract ExpandableListAdapter createAdapter(T versionList, LayoutInflater layoutInflater);
    public abstract Runnable createDownloadTask(Object selectedVersion, ModloaderDownloadListener listener);
    protected T filterVersionList(T versionList, String query) { return versionList; }
    @Nullable protected String getSuccessMessageLabel(Object selectedVersion) { return null; }
}
