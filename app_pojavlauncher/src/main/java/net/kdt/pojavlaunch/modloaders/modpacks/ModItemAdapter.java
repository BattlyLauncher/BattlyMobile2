package net.kdt.pojavlaunch.modloaders.modpacks;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.SimpleArrayAdapter;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDependency;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

public class ModItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements TaskCountListener {
    private static final ModItem[] MOD_ITEMS_EMPTY = new ModItem[0];
    private static final int VIEW_TYPE_MOD_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    /* Used when versions haven't loaded yet, default text to reduce layout shifting */
    private final SimpleArrayAdapter<String> mLoadingAdapter = new SimpleArrayAdapter<>(Collections.singletonList("Loading"));
    /* This my seem horribly inefficient but it is in fact the most efficient way without effectively writing a weak collection from scratch */
    private final Set<ViewHolder> mViewHolderSet = Collections.newSetFromMap(new WeakHashMap<>());
    private final ModIconCache mIconCache = new ModIconCache();
    private final SearchResultCallback mSearchResultCallback;
    private ModItem[] mModItems;
    private final ModpackApi mModpackApi;

    private Future<?> mTaskInProgress;
    private SearchFilters mSearchFilters;
    private SearchResult mCurrentResult;
    private boolean mLastPage;
    private boolean mTasksRunning;


    public ModItemAdapter(Resources resources, ModpackApi api, SearchResultCallback callback) {
        mModpackApi = api;
        mModItems = new ModItem[]{};
        mSearchResultCallback = callback;
    }

    public void performSearchQuery(SearchFilters searchFilters) {
        if(mTaskInProgress != null) {
            mTaskInProgress.cancel(true);
            mTaskInProgress = null;
        }
        this.mSearchFilters = searchFilters;
        this.mLastPage = false;
        mTaskInProgress = new SelfReferencingFuture(new SearchApiTask(mSearchFilters, null))
                .startOnExecutor(PojavApplication.sExecutorService);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        View view;
        switch (viewType) {
            case VIEW_TYPE_MOD_ITEM:
                // Create a new view, which defines the UI of the list item
                view = layoutInflater.inflate(R.layout.view_mod, viewGroup, false);
                return new ViewHolder(view);
            case VIEW_TYPE_LOADING:
                // Create a new view, which is actually just the progress bar
                view = layoutInflater.inflate(R.layout.view_loading, viewGroup, false);
                return new LoadingViewHolder(view);
            default:
                throw new RuntimeException("Unimplemented view type!");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case VIEW_TYPE_MOD_ITEM:
                ((ModItemAdapter.ViewHolder)holder).setStateLimited(mModItems[position]);
                break;
            case VIEW_TYPE_LOADING:
                loadMoreResults();
                break;
            default:
                throw new RuntimeException("Unimplemented view type!");
        }
    }

    @Override
    public int getItemCount() {
        if(mLastPage || mModItems.length == 0) return mModItems.length;
        return mModItems.length+1;
    }

    private void loadMoreResults() {
        if(mTaskInProgress != null) return;
        mTaskInProgress = new SelfReferencingFuture(new SearchApiTask(mSearchFilters, mCurrentResult))
                .startOnExecutor(PojavApplication.sExecutorService);
    }

    @Override
    public int getItemViewType(int position) {
        if(position < mModItems.length) return VIEW_TYPE_MOD_ITEM;
        return VIEW_TYPE_LOADING;
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        Tools.runOnUiThread(()->{
            mTasksRunning = taskCount != 0;
            for(ViewHolder viewHolder : mViewHolderSet) {
                viewHolder.updateInstallButtonState();
            }
        });
    }

    private interface ProfileSelectionCallback {
        void onProfileSelected(MinecraftProfile profile);
    }

    /**
     * Basic viewholder with expension capabilities
     */
    public class ViewHolder extends RecyclerView.ViewHolder {

        private ModDetail mModDetail = null;
        private ModItem mModItem = null;
        private final TextView mTitle, mDescription, mMeta;
        private final ImageView mIconView, mSourceView;
        private View mExtendedLayout;
        private Spinner mExtendedSpinner;
        private Button mExtendedButton;
        private Button mExtendedDependenciesButton;
        private TextView mExtendedErrorTextView;
        private TextView mExtendedInfoTextView;
        private TextView mExtendedDependencyTextView;
        private Future<?> mExtensionFuture;
        private Bitmap mThumbnailBitmap;
        private ImageReceiver mImageReceiver;
        private boolean mInstallEnabled;
        private CompatibilityOption mSelectedCompatibility;

        /* Used to display available versions of the mod(pack) */
        private final SimpleArrayAdapter<String> mVersionAdapter = new SimpleArrayAdapter<>(null);
        private final ArrayList<Integer> mVersionIndexMap = new ArrayList<>();

        public ViewHolder(View view) {
            super(view);
            mViewHolderSet.add(this);
            view.setOnClickListener(v -> {
                if(!hasExtended()){
                    android.app.Dialog dialog = new android.app.Dialog(v.getContext(), R.style.Theme_AppCompat_Dialog);
                    dialog.setContentView(R.layout.dialog_mod_details);
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    // Center on screen: 92% width, 85% height
                    android.util.DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
                    boolean landscape = dm.widthPixels > dm.heightPixels;
                    int dialogWidth = (int)(dm.widthPixels * (landscape ? 0.65f : 0.92f));
                    int dialogHeight = (int)(dm.heightPixels * (landscape ? 0.90f : 0.85f));
                    dialog.getWindow().setLayout(dialogWidth, dialogHeight);
                    dialog.getWindow().setGravity(android.view.Gravity.CENTER);
                    mExtendedLayout = dialog.findViewById(android.R.id.content);
                    mExtendedLayout.setTag(dialog);

                    mExtendedButton = dialog.findViewById(R.id.dialog_mod_install_button);
                    mExtendedSpinner = dialog.findViewById(R.id.dialog_mod_version_spinner);
                    mExtendedErrorTextView = dialog.findViewById(R.id.dialog_mod_error);
                    mExtendedDependenciesButton = dialog.findViewById(R.id.dialog_mod_deps_button);
                    mExtendedInfoTextView = dialog.findViewById(R.id.dialog_mod_info);
                    mExtendedDependencyTextView = dialog.findViewById(R.id.dialog_mod_dependency);

                    // Setup dialog basic info
                    ImageView dIcon = dialog.findViewById(R.id.dialog_mod_icon);
                    if(mThumbnailBitmap != null) {
                        dIcon.setImageDrawable(new BitmapDrawable(v.getResources(), mThumbnailBitmap));
                    }
                    ((TextView)dialog.findViewById(R.id.dialog_mod_title)).setText(mModItem.title);
                    ((TextView)dialog.findViewById(R.id.dialog_mod_meta)).setText(buildMeta(mModItem));
                    String desc = mModItem.description;
                    ((TextView)dialog.findViewById(R.id.dialog_mod_description)).setText(
                            (desc != null && !desc.isEmpty()) ? desc : v.getContext().getString(R.string.mod_detail_no_description));
                    ((ImageView)dialog.findViewById(R.id.dialog_mod_source)).setImageResource(getSourceDrawable(mModItem.apiSource));

                    // Stats row: downloads, follows, loader badge
                    bindStatsRow(dialog, mModItem);

                    dialog.findViewById(R.id.dialog_mod_close_button).setOnClickListener(v13 -> dialog.dismiss());
                    dialog.setOnDismissListener(d -> closeDetailedView());

                    mExtendedButton.setOnClickListener(v1 -> {
                        int selectedVersion = getSelectedDetailVersionIndex();
                        if (selectedVersion >= 0) {
                            installSelectedVersion(selectedVersion, false);
                        }
                    });
                    mExtendedDependenciesButton.setOnClickListener(v12 -> {
                        int selectedVersion = getSelectedDetailVersionIndex();
                        if (selectedVersion >= 0) {
                            installSelectedVersion(selectedVersion, true);
                        }
                    });
                    mExtendedInfoTextView.setOnClickListener(v14 -> showCompatibilityChooser());
                    mExtendedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view2, int position, long id) {
                            updateExtendedVersionState();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });
                    mExtendedSpinner.setAdapter(mLoadingAdapter);
                }

                if(!isExtended()) {
                    openDetailedView();
                }

                if(isExtended() && mModDetail == null && mExtensionFuture == null) { // only reload if no reloads are in progress
                    setDetailedStateDefault();
                    /*
                     * Why do we do this?
                     * The reason is simple: multithreading is difficult as hell to manage
                     * Let me explain:
                     */
                    mExtensionFuture = new SelfReferencingFuture(myFuture -> {
                        /*
                         * While we are sitting in the function below doing networking, the view might have already gotten recycled.
                         * If we didn't use a Future, we would have extended a ViewHolder with completely unrelated content
                         * or with an error that has never actually happened
                         */
                        mModDetail = mModpackApi.getModDetails(mModItem);
                        System.out.println(mModDetail);
                        Tools.runOnUiThread(() -> {
                            /*
                             * Once we enter here, the state we're in is already defined - no view shuffling can happen on the UI
                             * thread while we are on the UI thread ourselves. If we were cancelled, this means that the future
                             * we were supposed to have no longer makes sense, so we return and do not alter the state (since we might
                             * alter the state of an unrelated item otherwise)
                             */
                            if(myFuture.isCancelled()) return;
                            /*
                             * We do not null the future before returning since this field might already belong to a different item with its
                             * own Future, which we don't want to interfere with.
                             * But if the future is not cancelled, it is the right one for this ViewHolder, and we don't need it anymore, so
                             * let's help GC clean it up once we exit!
                             */
                            mExtensionFuture = null;
                            setStateDetailed(mModDetail);
                        });
                    }).startOnExecutor(PojavApplication.sExecutorService);
                }
            });

            // Define click listener for the ViewHolder's View
            mTitle = view.findViewById(R.id.mod_title_textview);
            mMeta = view.findViewById(R.id.mod_meta_textview);
            mDescription = view.findViewById(R.id.mod_body_textview);
            mIconView = view.findViewById(R.id.mod_thumbnail_imageview);
            mSourceView = view.findViewById(R.id.mod_source_imageview);
        }

        /** Display basic info about the moditem */
        public void setStateLimited(ModItem item) {
            mModDetail = null;
            if(mThumbnailBitmap != null) {
                mIconView.setImageBitmap(null);
                mThumbnailBitmap.recycle();
            }
            if(mImageReceiver != null) {
                mIconCache.cancelImage(mImageReceiver);
            }
            if(mExtensionFuture != null) {
                /*
                 * Since this method reinitializes the ViewHolder for a new mod, this Future stops being ours, so we cancel it
                 * and null it. The rest is handled above
                 */
                mExtensionFuture.cancel(true);
                mExtensionFuture = null;
            }
            mSelectedCompatibility = null;
            mVersionIndexMap.clear();

            mModItem = item;
            if (mExtendedButton != null) {
                mExtendedButton.setText(getInstallString(item.contentType));
            }
            if (mExtendedErrorTextView != null) {
                mExtendedErrorTextView.setText(getMetadataErrorString(item.contentType));
            }
            // here the previous reference to the image receiver will disappear
            mImageReceiver = bm->{
                mImageReceiver = null;
                mThumbnailBitmap = bm;
                mIconView.setImageDrawable(new BitmapDrawable(mIconView.getResources(), bm));
            };
            mIconCache.getImage(mImageReceiver, mModItem.getIconCacheTag(), mModItem.imageUrl);
            mSourceView.setImageResource(getSourceDrawable(item.apiSource));
            mTitle.setText(item.title);
            mMeta.setText(buildMeta(item));
            mDescription.setText(item.description);

            if(hasExtended()){
                closeDetailedView();
            }
        }

        /** Display extended info/interaction about a modpack */
        private void setStateDetailed(ModDetail detailedItem) {
            if(detailedItem != null) {
                setInstallEnabled(true);
                mExtendedErrorTextView.setVisibility(View.GONE);
                // Do not silently hide files behind the first detected Minecraft/loader
                // combination. Resource packs and shaders commonly publish one file for
                // several game versions, so the complete release history is the safest
                // and least surprising initial view.
                mSelectedCompatibility = null;
                mExtendedSpinner.setAdapter(mVersionAdapter);
                bindVersionsForCompatibility();
            } else {
                closeDetailedView();
                setInstallEnabled(false);
                mExtendedErrorTextView.setVisibility(View.VISIBLE);
                mExtendedSpinner.setAdapter(null);
                mVersionAdapter.setObjects(null);
                mSelectedCompatibility = null;
                mVersionIndexMap.clear();
                if (mExtendedDependenciesButton != null) {
                    mExtendedDependenciesButton.setEnabled(false);
                }
            }
        }

        private void openDetailedView() {
            if (mExtendedLayout != null && mExtendedLayout.getTag() instanceof android.app.Dialog) {
                ((android.app.Dialog) mExtendedLayout.getTag()).show();
            }
        }

        private void closeDetailedView(){
            if (mExtendedLayout != null && mExtendedLayout.getTag() instanceof android.app.Dialog) {
                ((android.app.Dialog) mExtendedLayout.getTag()).dismiss();
            }
        }

        private void setDetailedStateDefault() {
            setInstallEnabled(false);
            mExtendedSpinner.setAdapter(mLoadingAdapter);
            mExtendedErrorTextView.setVisibility(View.GONE);
            mExtendedButton.setText(getInstallString(mModItem.contentType));
            if (mExtendedInfoTextView != null) {
                mExtendedInfoTextView.setText(R.string.mod_version_loading);
            }
            if (mExtendedDependencyTextView != null) {
                mExtendedDependencyTextView.setVisibility(View.VISIBLE);
                mExtendedDependencyTextView.setText(R.string.mod_dependency_loading);
            }
            if (mExtendedDependenciesButton != null) {
                mExtendedDependenciesButton.setEnabled(false);
            }
            mSelectedCompatibility = null;
            mVersionIndexMap.clear();
            openDetailedView();
        }

        private boolean hasExtended(){
            return mExtendedLayout != null;
        }

        private boolean isExtended(){
            return hasExtended() && mExtendedLayout.getTag() instanceof android.app.Dialog && ((android.app.Dialog) mExtendedLayout.getTag()).isShowing();
        }

        private int getSourceDrawable(int apiSource) {
            switch (apiSource) {
                case Constants.SOURCE_CURSEFORGE:
                    return R.drawable.ic_curseforge;
                case Constants.SOURCE_MODRINTH:
                    return R.drawable.ic_modrinth;
                default:
                    throw new RuntimeException("Unknown API source");
            }
        }

        private void setInstallEnabled(boolean enabled) {
            mInstallEnabled = enabled;
            updateInstallButtonState();
        }

        private void updateInstallButtonState() {
            if(mExtendedButton != null)
                mExtendedButton.setEnabled(mInstallEnabled && !mTasksRunning);
            if (mExtendedDependenciesButton != null) {
                boolean hasDependencies = false;
                if (mModDetail != null && mExtendedSpinner != null) {
                    hasDependencies = getRequiredDependencyCount(getSelectedDetailVersionIndex()) > 0;
                }
                mExtendedDependenciesButton.setEnabled(hasDependencies && !mTasksRunning);
            }
        }

        private void updateExtendedVersionState() {
            int selectedPosition = getSelectedDetailVersionIndex();
            if (mModDetail == null || mExtendedSpinner == null || selectedPosition < 0) {
                return;
            }
            if (mExtendedInfoTextView != null) {
                if (mSelectedCompatibility == null) {
                    mExtendedInfoTextView.setText(itemView.getContext().getString(
                            R.string.mod_detail_minecraft_versions_count,
                            countMinecraftVersions(mModDetail),
                            buildLoaderSummary(collectLoaders(mModDetail))
                    ));
                } else {
                    mExtendedInfoTextView.setText(mSelectedCompatibility.toString());
                }
            }

            int dependencyCount = getRequiredDependencyCount(selectedPosition);
            if (mExtendedDependencyTextView != null) {
                mExtendedDependencyTextView.setVisibility(View.VISIBLE);
                mExtendedDependencyTextView.setText(dependencyCount == 0
                        ? itemView.getContext().getString(R.string.mod_dependency_none)
                        : itemView.getContext().getString(R.string.mod_dependency_summary, dependencyCount));
            }
            updateInstallButtonState();
        }

        private int getSelectedDetailVersionIndex() {
            if (mExtendedSpinner == null) {
                return -1;
            }
            int selectedPosition = mExtendedSpinner.getSelectedItemPosition();
            if (selectedPosition < 0) {
                return -1;
            }
            if (selectedPosition < mVersionIndexMap.size()) {
                return mVersionIndexMap.get(selectedPosition);
            }
            return selectedPosition;
        }

        private void bindVersionsForCompatibility() {
            if (mModDetail == null || mModDetail.versionNames == null) {
                mVersionAdapter.setObjects(null);
                mVersionIndexMap.clear();
                updateInstallButtonState();
                return;
            }

            ArrayList<String> versionNames = new ArrayList<>();
            mVersionIndexMap.clear();
            for (int i = 0; i < mModDetail.versionNames.length; i++) {
                if (matchesCompatibility(i, mSelectedCompatibility)) {
                    versionNames.add(mModDetail.versionNames[i]);
                    mVersionIndexMap.add(i);
                }
            }

            if (versionNames.isEmpty()) {
                versionNames.addAll(Arrays.asList(mModDetail.versionNames));
                for (int i = 0; i < mModDetail.versionNames.length; i++) {
                    mVersionIndexMap.add(i);
                }
            }

            mVersionAdapter.setObjects(versionNames);
            if (mExtendedSpinner.getAdapter() != mVersionAdapter) {
                mExtendedSpinner.setAdapter(mVersionAdapter);
            }
            if (!versionNames.isEmpty()) {
                mExtendedSpinner.setSelection(0);
            }
            updateExtendedVersionState();
        }

        private void installSelectedVersion(int selectedVersion, boolean dependenciesOnly) {
            if (mModDetail == null) {
                return;
            }

            if (mModDetail.isModpack || mModDetail.contentType == SearchFilters.TYPE_MODPACK) {
                installIntoProfile(selectedVersion, dependenciesOnly, null);
                return;
            }

            showTargetProfileChooser(targetProfile -> installIntoProfile(selectedVersion, dependenciesOnly, targetProfile));
        }

        private void installIntoProfile(int selectedVersion, boolean dependenciesOnly, MinecraftProfile targetProfile) {
            if (dependenciesOnly) {
                mModpackApi.handleDependenciesInstallation(itemView.getContext(), mModDetail, selectedVersion, targetProfile);
            } else {
                mModpackApi.handleInstallation(itemView.getContext(), mModDetail, selectedVersion, targetProfile);
            }
        }

        private void showTargetProfileChooser(ProfileSelectionCallback callback) {
            LauncherProfiles.load();
            if (LauncherProfiles.mainProfileJson == null
                    || LauncherProfiles.mainProfileJson.profiles == null
                    || LauncherProfiles.mainProfileJson.profiles.isEmpty()) {
                android.widget.Toast.makeText(
                        itemView.getContext(),
                        R.string.content_install_no_profiles,
                        android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }

            android.app.Dialog dialog = new android.app.Dialog(itemView.getContext(), R.style.Theme_AppCompat_Dialog);
            View contentView = LayoutInflater.from(itemView.getContext())
                    .inflate(R.layout.dialog_instance_selector, null, false);
            dialog.setContentView(contentView);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            LinearLayout container = contentView.findViewById(R.id.instance_selector_container);
            Button cancelButton = contentView.findViewById(R.id.instance_selector_cancel);
            cancelButton.setOnClickListener(v -> dialog.dismiss());

            List<Map.Entry<String, MinecraftProfile>> profiles =
                    new ArrayList<>(LauncherProfiles.mainProfileJson.profiles.entrySet());
            Collections.sort(profiles, (left, right) ->
                    getProfileDisplayName(left).compareToIgnoreCase(getProfileDisplayName(right)));

            String currentProfileKey = LauncherPreferences.DEFAULT_PREF == null
                    ? null
                    : LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");

            for (Map.Entry<String, MinecraftProfile> entry : profiles) {
                View row = LayoutInflater.from(itemView.getContext())
                        .inflate(R.layout.item_instance_selector, container, false);
                MinecraftProfile profile = entry.getValue();
                TextView name = row.findViewById(R.id.instance_selector_item_name);
                TextView version = row.findViewById(R.id.instance_selector_item_version);
                TextView path = row.findViewById(R.id.instance_selector_item_path);
                TextView current = row.findViewById(R.id.instance_selector_item_current);

                name.setText(getProfileDisplayName(entry));
                version.setText(itemView.getContext().getString(
                        R.string.content_install_profile_version,
                        profile == null || !Tools.isValidString(profile.lastVersionId)
                                ? itemView.getContext().getString(R.string.launcher_version_unknown)
                                : profile.lastVersionId
                ));
                path.setText(Tools.getGameDirPath(profile == null ? LauncherProfiles.getCurrentProfile() : profile).getPath());
                current.setVisibility(entry.getKey().equals(currentProfileKey) ? View.VISIBLE : View.GONE);
                row.setOnClickListener(v -> {
                    callback.onProfileSelected(profile);
                    dialog.dismiss();
                });
                container.addView(row);
            }

            dialog.show();
            if (dialog.getWindow() != null) {
                android.util.DisplayMetrics dm = itemView.getContext().getResources().getDisplayMetrics();
                int dialogWidth = (int) (dm.widthPixels * (dm.widthPixels > dm.heightPixels ? 0.50f : 0.90f));
                dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                dialog.getWindow().setGravity(android.view.Gravity.CENTER);
            }
        }

        private String getProfileDisplayLabel(Map.Entry<String, MinecraftProfile> entry) {
            MinecraftProfile profile = entry.getValue();
            String version = profile == null || !Tools.isValidString(profile.lastVersionId)
                    ? itemView.getContext().getString(R.string.launcher_version_unknown)
                    : profile.lastVersionId;
            return itemView.getContext().getString(
                    R.string.content_install_profile_label,
                    getProfileDisplayName(entry),
                    version
            );
        }

        private String getProfileDisplayName(Map.Entry<String, MinecraftProfile> entry) {
            MinecraftProfile profile = entry.getValue();
            if (profile != null && Tools.isValidString(profile.name)) {
                return profile.name;
            }
            return entry.getKey();
        }

        private void showCompatibilityChooser() {
            if (mModDetail == null) {
                return;
            }
            List<CompatibilityOption> options = buildCompatibilityOptions(mModDetail);
            if (options.isEmpty()) {
                return;
            }

            String[] labels = new String[options.size() + 1];
            labels[0] = itemView.getContext().getString(
                    R.string.mod_detail_all_versions,
                    mModDetail.versionNames == null ? 0 : mModDetail.versionNames.length
            );
            int checkedIndex = 0;
            for (int i = 0; i < options.size(); i++) {
                CompatibilityOption option = options.get(i);
                labels[i + 1] = option.toString();
                if (option.sameAs(mSelectedCompatibility)) {
                    checkedIndex = i + 1;
                }
            }

            new AlertDialog.Builder(itemView.getContext(), R.style.BattlyDialog)
                    .setTitle(R.string.mod_detail_label_compat)
                    .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                        mSelectedCompatibility = which == 0 ? null : options.get(which - 1);
                        bindVersionsForCompatibility();
                        dialog.dismiss();
                    })
                    .show();
        }

        private List<CompatibilityOption> buildCompatibilityOptions(ModDetail detail) {
            LinkedHashMap<String, CompatibilityOption> options = new LinkedHashMap<>();
            if (detail == null || detail.versionNames == null) {
                return new ArrayList<>();
            }

            for (int i = 0; i < detail.versionNames.length; i++) {
                String[] gameVersions = detail.getGameVersions(i);
                String[] loaders = detail.versionLoaders == null || i >= detail.versionLoaders.length
                        ? null : detail.versionLoaders[i];
                if (gameVersions.length == 0) gameVersions = new String[]{null};

                for (String mcVersion : gameVersions) {
                    boolean addedLoader = false;
                    if (loaders != null) {
                        for (String loader : loaders) {
                            if (!Tools.isValidString(loader)) {
                                continue;
                            }
                            addCompatibilityOption(options, mcVersion, loader);
                            addedLoader = true;
                        }
                    }
                    if (!addedLoader) {
                        addCompatibilityOption(options, mcVersion, null);
                    }
                }
            }
            ArrayList<CompatibilityOption> result = new ArrayList<>(options.values());
            Collections.sort(result, (left, right) -> {
                int versionResult = compareVersionDescending(left.mcVersion, right.mcVersion);
                if (versionResult != 0) return versionResult;
                return String.valueOf(left.loader).compareToIgnoreCase(String.valueOf(right.loader));
            });
            return result;
        }

        private void addCompatibilityOption(LinkedHashMap<String, CompatibilityOption> options,
                                            String mcVersion,
                                            String loader) {
            String normalizedMc = Tools.isValidString(mcVersion) ? mcVersion : null;
            String normalizedLoader = Tools.isValidString(loader)
                    ? loader.toLowerCase(java.util.Locale.ROOT)
                    : null;
            String key = String.valueOf(normalizedMc) + "|" + String.valueOf(normalizedLoader);
            if (!options.containsKey(key)) {
                options.put(key, new CompatibilityOption(normalizedMc, normalizedLoader));
            }
        }

        private boolean matchesCompatibility(int versionIndex, CompatibilityOption option) {
            if (option == null) {
                return true;
            }
            if (option.mcVersion != null
                    && !mModDetail.supportsMinecraftVersion(versionIndex, option.mcVersion)) {
                return false;
            }

            String[] loaders = mModDetail.versionLoaders == null || versionIndex >= mModDetail.versionLoaders.length
                    ? null : mModDetail.versionLoaders[versionIndex];
            if (option.loader == null) {
                return loaders == null || loaders.length == 0;
            }
            if (loaders == null) {
                return false;
            }
            for (String loader : loaders) {
                if (Tools.isValidString(loader) && option.loader.equalsIgnoreCase(loader)) {
                    return true;
                }
            }
            return false;
        }

        private int countMinecraftVersions(ModDetail detail) {
            LinkedHashSet<String> versions = new LinkedHashSet<>();
            if (detail != null && detail.versionNames != null) {
                for (int i = 0; i < detail.versionNames.length; i++) {
                    for (String version : detail.getGameVersions(i)) {
                        if (Tools.isValidString(version)) versions.add(version);
                    }
                }
            }
            return versions.size();
        }

        private String[] collectLoaders(ModDetail detail) {
            LinkedHashSet<String> loaders = new LinkedHashSet<>();
            if (detail != null && detail.versionLoaders != null) {
                for (String[] versionLoaders : detail.versionLoaders) {
                    if (versionLoaders == null) continue;
                    for (String loader : versionLoaders) {
                        if (Tools.isValidString(loader)) loaders.add(loader);
                    }
                }
            }
            return loaders.toArray(new String[0]);
        }

        private int compareVersionDescending(String left, String right) {
            if (left == null) return right == null ? 0 : 1;
            if (right == null) return -1;
            String[] leftParts = left.split("[^0-9]+");
            String[] rightParts = right.split("[^0-9]+");
            int max = Math.max(leftParts.length, rightParts.length);
            for (int i = 0; i < max; i++) {
                int leftValue = parseVersionPart(leftParts, i);
                int rightValue = parseVersionPart(rightParts, i);
                if (leftValue != rightValue) return Integer.compare(rightValue, leftValue);
            }
            return right.compareToIgnoreCase(left);
        }

        private int parseVersionPart(String[] parts, int index) {
            if (index >= parts.length || parts[index].isEmpty()) return 0;
            try {
                return Integer.parseInt(parts[index]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private int getRequiredDependencyCount(int selectedPosition) {
            if (mModDetail == null || mModDetail.versionDependencies == null || selectedPosition < 0 || selectedPosition >= mModDetail.versionDependencies.length) {
                return 0;
            }
            int count = 0;
            ModDependency[] dependencies = mModDetail.versionDependencies[selectedPosition];
            if (dependencies == null) return 0;
            for (ModDependency dependency : dependencies) {
                if (dependency != null && dependency.required) {
                    count++;
                }
            }
            return count;
        }

        private String buildMeta(ModItem item) {
            String sourceName = item.apiSource == Constants.SOURCE_MODRINTH ? "Modrinth" : "CurseForge";
            String loaderSummary = buildLoaderSummary(item.loaders);
            return sourceName + " · " + loaderSummary;
        }

        private void bindStatsRow(android.app.Dialog dialog, ModItem item) {
            TextView downloadsView = dialog.findViewById(R.id.dialog_mod_downloads);
            View statsDivider = dialog.findViewById(R.id.dialog_mod_stats_divider);
            TextView followsView = dialog.findViewById(R.id.dialog_mod_follows);
            TextView loaderBadge = dialog.findViewById(R.id.dialog_mod_loader_badge);

            boolean hasDownloads = item.downloadCount > 0;
            boolean hasFollows = item.followCount > 0;
            if (hasDownloads) {
                downloadsView.setText("\u2193 " + formatCount(item.downloadCount));
                downloadsView.setVisibility(View.VISIBLE);
            }
            if (hasFollows) {
                followsView.setText("\u2665 " + formatCount(item.followCount));
                followsView.setVisibility(View.VISIBLE);
            }
            if (hasDownloads && hasFollows) {
                statsDivider.setVisibility(View.VISIBLE);
            }
            // Show primary loader badge (first recognized loader)
            if (item.loaders != null) {
                for (String loader : item.loaders) {
                    if (!Tools.isValidString(loader)) continue;
                    String label = null;
                    if ("forge".equalsIgnoreCase(loader)) label = "Forge";
                    else if ("fabric".equalsIgnoreCase(loader)) label = "Fabric";
                    else if ("quilt".equalsIgnoreCase(loader)) label = "Quilt";
                    else if ("neoforge".equalsIgnoreCase(loader)) label = "NeoForge";
                    if (label != null) {
                        loaderBadge.setText(label);
                        loaderBadge.setVisibility(View.VISIBLE);
                        break;
                    }
                }
            }
        }

        private String formatCount(long count) {
            if (count >= 1_000_000) return String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0);
            if (count >= 1_000) return String.format(java.util.Locale.US, "%.1fK", count / 1_000.0);
            return String.valueOf(count);
        }

        private String buildLoaderSummary(String[] loaders) {
            if (loaders == null || loaders.length == 0) {
                return itemView.getContext().getString(R.string.search_filter_loader_any);
            }
            java.util.ArrayList<String> labels = new java.util.ArrayList<>();
            for (String loader : loaders) {
                if (!Tools.isValidString(loader)) continue;
                String label = toLoaderLabel(loader);
                if (!labels.contains(label)) labels.add(label);
            }
            return labels.isEmpty()
                    ? itemView.getContext().getString(R.string.search_filter_loader_any)
                    : android.text.TextUtils.join(", ", labels);
        }

        private String toLoaderLabel(String loader) {
            if (!Tools.isValidString(loader)) {
                return itemView.getContext().getString(R.string.search_filter_loader_any);
            }
            if ("forge".equalsIgnoreCase(loader)) return "Forge";
            if ("fabric".equalsIgnoreCase(loader)) return "Fabric";
            if ("quilt".equalsIgnoreCase(loader)) return "Quilt";
            if ("neoforge".equalsIgnoreCase(loader)) return "NeoForge";
            if ("iris".equalsIgnoreCase(loader)) return "Iris";
            if ("optifine".equalsIgnoreCase(loader)) return "OptiFine";
            return loader;
        }

        private int getInstallString(int contentType) {
            switch (contentType) {
                case SearchFilters.TYPE_MOD:
                    return R.string.mod_install_button;
                case SearchFilters.TYPE_RESOURCEPACK:
                    return R.string.resourcepack_install_button;
                case SearchFilters.TYPE_SHADER:
                    return R.string.shader_install_button;
                case SearchFilters.TYPE_DATAPACK:
                    return R.string.datapack_install_button;
                case SearchFilters.TYPE_MODPACK:
                default:
                    return R.string.modpack_install_button;
            }
        }

        private int getMetadataErrorString(int contentType) {
            switch (contentType) {
                case SearchFilters.TYPE_MOD:
                    return R.string.search_mod_download_error;
                case SearchFilters.TYPE_RESOURCEPACK:
                    return R.string.search_resourcepack_download_error;
                case SearchFilters.TYPE_SHADER:
                    return R.string.search_shader_download_error;
                case SearchFilters.TYPE_DATAPACK:
                    return R.string.search_datapack_download_error;
                case SearchFilters.TYPE_MODPACK:
                default:
                    return R.string.search_modpack_download_error;
            }
        }

        private class CompatibilityOption {
            private final String mcVersion;
            private final String loader;

            private CompatibilityOption(String mcVersion, String loader) {
                this.mcVersion = mcVersion;
                this.loader = loader;
            }

            private boolean sameAs(CompatibilityOption other) {
                if (other == null) {
                    return false;
                }
                return java.util.Objects.equals(mcVersion, other.mcVersion)
                        && java.util.Objects.equals(loader, other.loader);
            }

            @NonNull
            @Override
            public String toString() {
                return itemView.getContext().getString(
                        R.string.mod_version_summary,
                        Tools.isValidString(mcVersion) ? mcVersion : itemView.getContext().getString(R.string.launcher_version_unknown),
                        toLoaderLabel(loader)
                );
            }
        }
    }

    /**
     * The view holder used to hold the progress bar at the end of the list
     */
    private static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(View view) {
            super(view);
        }
    }

    private class SearchApiTask implements SelfReferencingFuture.FutureInterface {
        private final SearchFilters mSearchFilters;
        private final SearchResult mPreviousResult;

        private SearchApiTask(SearchFilters searchFilters, SearchResult previousResult) {
            this.mSearchFilters = searchFilters;
            this.mPreviousResult = previousResult;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void run(Future<?> myFuture) {
            SearchResult result = mModpackApi.searchMod(mSearchFilters, mPreviousResult);
            ModItem[] resultModItems = result != null ? result.results : null;
            if(resultModItems != null && resultModItems.length != 0 && mPreviousResult != null) {
                ModItem[] newModItems = new ModItem[resultModItems.length + mModItems.length];
                System.arraycopy(mModItems, 0, newModItems, 0, mModItems.length);
                System.arraycopy(resultModItems, 0, newModItems, mModItems.length, resultModItems.length);
                resultModItems = newModItems;
            }
            ModItem[] finalModItems = resultModItems;
            Tools.runOnUiThread(() -> {
                if(myFuture.isCancelled()) return;
                mTaskInProgress = null;
                if(finalModItems == null) {
                    mSearchResultCallback.onSearchError(SearchResultCallback.ERROR_INTERNAL);
                }else if(finalModItems.length == 0) {
                    if(mPreviousResult != null) {
                        mLastPage = true;
                        notifyItemChanged(mModItems.length);
                        mSearchResultCallback.onSearchFinished();
                        return;
                    }
                    mSearchResultCallback.onSearchError(SearchResultCallback.ERROR_NO_RESULTS);
                }else{
                    mSearchResultCallback.onSearchFinished();
                }
                mCurrentResult = result;
                if(finalModItems == null) {
                    mModItems = MOD_ITEMS_EMPTY;
                    notifyDataSetChanged();
                    return;
                }
                mModItems = finalModItems;
                mLastPage = result != null
                        && result.totalResultCount > 0
                        && mModItems.length >= result.totalResultCount;

                // The final loading cell changes position whenever a page is appended.
                // A range notification based on the total list length corrupts RecyclerView's
                // item accounting and can leave later pages unbound. Rebind the small catalog
                // grid from its authoritative array instead.
                notifyDataSetChanged();
            });
        }
    }

    public interface SearchResultCallback {
        int ERROR_INTERNAL = 0;
        int ERROR_NO_RESULTS = 1;
        void onSearchFinished();
        void onSearchError(int error);
    }
}
