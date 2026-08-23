package net.kdt.pojavlaunch.modloaders.modpacks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.nativead.NativeAd;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.BattlyNativeAdHelper;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;

public class ModItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements TaskCountListener {
    private static final ModItem[] MOD_ITEMS_EMPTY = new ModItem[0];
    private static final int VIEW_TYPE_MOD_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;
    private static final int VIEW_TYPE_NATIVE_AD = 2;

    private interface ChoiceListener {
        void onChoice(int position);
    }

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
    private final List<NativeAd> mNativeAds = new ArrayList<>();
    private List<Integer> mAdPositions = Collections.emptyList();
    private long mAdSeed;
    private int mAdGeneration;
    private WeakReference<Activity> mAdActivity = new WeakReference<>(null);
    private String mAdUnitId;
    private int mLoadedPageCount;
    private int mRequestedAdBatches;


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
        clearNativeAds();
        mLoadedPageCount = 0;
        mRequestedAdBatches = 0;
        notifyDataSetChanged();
        mAdSeed = (searchFilters.name == null ? 0 : searchFilters.name.hashCode())
                * 31L + searchFilters.contentType;
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
            case VIEW_TYPE_NATIVE_AD:
                FrameLayout container = new FrameLayout(viewGroup.getContext());
                container.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                container.setPadding(0, 0, 0, Math.round(
                        8 * viewGroup.getResources().getDisplayMetrics().density));
                return new NativeAdViewHolder(container);
            default:
                throw new RuntimeException("Unimplemented view type!");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case VIEW_TYPE_MOD_ITEM:
                ((ModItemAdapter.ViewHolder)holder).setStateLimited(mModItems[toContentPosition(position)]);
                break;
            case VIEW_TYPE_LOADING:
                loadMoreResults();
                break;
            case VIEW_TYPE_NATIVE_AD:
                ((NativeAdViewHolder) holder).bind(mNativeAds.get(mAdPositions.indexOf(position)));
                break;
            default:
                throw new RuntimeException("Unimplemented view type!");
        }
    }

    @Override
    public int getItemCount() {
        int count = mModItems.length + mAdPositions.size();
        if(mLastPage || mModItems.length == 0) return count;
        return count + 1;
    }

    private void loadMoreResults() {
        if(mTaskInProgress != null) return;
        mTaskInProgress = new SelfReferencingFuture(new SearchApiTask(mSearchFilters, mCurrentResult))
                .startOnExecutor(PojavApplication.sExecutorService);
    }

    @Override
    public int getItemViewType(int position) {
        if (mAdPositions.contains(position)) return VIEW_TYPE_NATIVE_AD;
        if(position < mModItems.length + mAdPositions.size()) return VIEW_TYPE_MOD_ITEM;
        return VIEW_TYPE_LOADING;
    }

    public boolean isFullSpanPosition(int position) {
        return getItemViewType(position) == VIEW_TYPE_LOADING;
    }

    public void loadNativeAds(Activity activity, String unitId) {
        mAdActivity = new WeakReference<>(activity);
        mAdUnitId = unitId;
        requestAdsForLoadedPages();
    }

    private void requestAdsForLoadedPages() {
        Activity activity = mAdActivity.get();
        if (activity == null || mAdUnitId == null || mAdUnitId.isEmpty()) return;
        while (mRequestedAdBatches < mLoadedPageCount) {
            mRequestedAdBatches++;
            requestNativeAdBatch(activity, mAdUnitId, mAdGeneration);
        }
    }

    private void requestNativeAdBatch(Activity activity, String unitId, int generation) {
        Log.d("BattlyAds", "Requesting Workspace ad batch " + mRequestedAdBatches
                + " for " + mLoadedPageCount + " loaded page(s)");
        for (int i = 0; i < 2; i++) {
            BattlyNativeAdHelper.load(activity, unitId, new BattlyNativeAdHelper.Callback() {
                @Override
                public void onLoaded(NativeAd ad) {
                    if (generation != mAdGeneration) {
                        ad.destroy();
                        return;
                    }
                    mNativeAds.add(ad);
                    Log.d("BattlyAds", "Workspace native ad inserted; total=" + mNativeAds.size());
                    rebuildAdPositions();
                    notifyDataSetChanged();
                }

                @Override
                public void onFailed() {
                }
            });
        }
    }

    public void clearNativeAds() {
        mAdGeneration++;
        for (NativeAd ad : mNativeAds) ad.destroy();
        mNativeAds.clear();
        mAdPositions = Collections.emptyList();
    }

    private void rebuildAdPositions() {
        mAdPositions = BattlyNativeAdHelper.randomAdPositions(
                mModItems.length, mNativeAds.size(), mAdSeed);
    }

    private int toContentPosition(int adapterPosition) {
        int offset = 0;
        for (int adPosition : mAdPositions) {
            if (adPosition < adapterPosition) offset++;
        }
        return adapterPosition - offset;
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
        private TextView mExtendedLoaderSelector;
        private TextView mExtendedMinecraftSelector;
        private TextView mExtendedVersionSelector;
        private Button mExtendedButton;
        private Button mExtendedDependenciesButton;
        private TextView mExtendedErrorTextView;
        private TextView mExtendedDependencyTextView;
        private Future<?> mExtensionFuture;
        private Bitmap mThumbnailBitmap;
        private ImageReceiver mImageReceiver;
        private boolean mInstallEnabled;
        private String mSelectedLoader;
        private String mSelectedMinecraftVersion;
        private int mSelectedVersionIndex = -1;
        private final ArrayList<Integer> mAvailableVersionIndices = new ArrayList<>();

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
                    mExtendedLoaderSelector = dialog.findViewById(R.id.dialog_mod_loader_selector);
                    mExtendedMinecraftSelector = dialog.findViewById(R.id.dialog_mod_minecraft_selector);
                    mExtendedVersionSelector = dialog.findViewById(R.id.dialog_mod_version_selector);
                    mExtendedErrorTextView = dialog.findViewById(R.id.dialog_mod_error);
                    mExtendedDependenciesButton = dialog.findViewById(R.id.dialog_mod_deps_button);
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
                    mExtendedLoaderSelector.setOnClickListener(v14 -> showLoaderChooser());
                    mExtendedMinecraftSelector.setOnClickListener(v15 -> showMinecraftVersionChooser());
                    mExtendedVersionSelector.setOnClickListener(v16 -> showContentVersionChooser());
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
                // ImageView/RenderThread may still hold the drawable in a display list
                // after this holder is rebound. Recycling here causes
                // "Canvas: trying to use a recycled bitmap" during dispatchDraw.
                mIconView.setImageDrawable(null);
                mThumbnailBitmap = null;
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
            resetVersionSelection();

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
                initializeVersionSelection();
            } else {
                closeDetailedView();
                setInstallEnabled(false);
                mExtendedErrorTextView.setVisibility(View.VISIBLE);
                resetVersionSelection();
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
            mExtendedErrorTextView.setVisibility(View.GONE);
            mExtendedButton.setText(getInstallString(mModItem.contentType));
            if (mExtendedLoaderSelector != null) mExtendedLoaderSelector.setText(R.string.mod_version_loading);
            if (mExtendedMinecraftSelector != null) mExtendedMinecraftSelector.setText(R.string.mod_version_loading);
            if (mExtendedVersionSelector != null) mExtendedVersionSelector.setText(R.string.mod_version_loading);
            if (mExtendedDependencyTextView != null) {
                mExtendedDependencyTextView.setVisibility(View.VISIBLE);
                mExtendedDependencyTextView.setText(R.string.mod_dependency_loading);
            }
            if (mExtendedDependenciesButton != null) {
                mExtendedDependenciesButton.setEnabled(false);
            }
            resetVersionSelection();
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
                if (mModDetail != null && mSelectedVersionIndex >= 0) {
                    hasDependencies = getRequiredDependencyCount(getSelectedDetailVersionIndex()) > 0;
                }
                mExtendedDependenciesButton.setEnabled(hasDependencies && !mTasksRunning);
            }
        }

        private void updateExtendedVersionState() {
            int selectedPosition = getSelectedDetailVersionIndex();
            if (mModDetail == null || selectedPosition < 0) {
                return;
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
            return mSelectedVersionIndex;
        }

        private void initializeVersionSelection() {
            List<String> loaders = WorkspaceVersionSelection.collectLoaders(mModDetail);
            mSelectedLoader = loaders.isEmpty() ? null : loaders.get(0);
            mExtendedLoaderSelector.setText(loaders.isEmpty()
                    ? itemView.getContext().getString(R.string.mod_detail_any_loader)
                    : toLoaderLabel(mSelectedLoader));
            bindMinecraftVersions();
        }

        private void bindMinecraftVersions() {
            List<String> versions = WorkspaceVersionSelection.collectMinecraftVersions(mModDetail, mSelectedLoader);
            if (versions.isEmpty()) {
                mSelectedMinecraftVersion = null;
                mExtendedMinecraftSelector.setText(R.string.mod_detail_choose_minecraft);
                mExtendedMinecraftSelector.setEnabled(false);
            } else {
                if (!versions.contains(mSelectedMinecraftVersion)) mSelectedMinecraftVersion = versions.get(0);
                mExtendedMinecraftSelector.setText(mSelectedMinecraftVersion);
                mExtendedMinecraftSelector.setEnabled(true);
            }
            bindContentVersions();
        }

        private void bindContentVersions() {
            mAvailableVersionIndices.clear();
            mAvailableVersionIndices.addAll(WorkspaceVersionSelection.collectReleaseIndices(
                    mModDetail, mSelectedLoader, mSelectedMinecraftVersion));
            mSelectedVersionIndex = mAvailableVersionIndices.isEmpty() ? -1 : mAvailableVersionIndices.get(0);
            if (mSelectedVersionIndex < 0) {
                mExtendedVersionSelector.setText(R.string.mod_detail_choose_content_version);
                mExtendedVersionSelector.setEnabled(false);
                setInstallEnabled(false);
            } else {
                mExtendedVersionSelector.setText(getVersionDisplayLabel(mSelectedVersionIndex, true));
                mExtendedVersionSelector.setEnabled(true);
                setInstallEnabled(true);
            }
            updateExtendedVersionState();
        }

        private String getVersionDisplayLabel(int detailIndex, boolean recommended) {
            String label = mModDetail.versionNames[detailIndex];
            return recommended
                    ? label + " · " + itemView.getContext().getString(R.string.mod_detail_recommended)
                    : label;
        }

        private void resetVersionSelection() {
            mSelectedLoader = null;
            mSelectedMinecraftVersion = null;
            mSelectedVersionIndex = -1;
            mAvailableVersionIndices.clear();
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

        private void showLoaderChooser() {
            if (mModDetail == null) return;
            List<String> loaders = WorkspaceVersionSelection.collectLoaders(mModDetail);
            if (loaders.isEmpty()) return;
            String[] labels = new String[loaders.size()];
            int checked = 0;
            for (int i = 0; i < loaders.size(); i++) {
                labels[i] = toLoaderLabel(loaders.get(i));
                if (loaders.get(i).equalsIgnoreCase(mSelectedLoader)) checked = i;
            }
            new AlertDialog.Builder(itemView.getContext(), R.style.BattlyDialog)
                    .setTitle(R.string.mod_detail_step_loader)
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        mSelectedLoader = loaders.get(which);
                        mExtendedLoaderSelector.setText(labels[which]);
                        mSelectedMinecraftVersion = null;
                        bindMinecraftVersions();
                        dialog.dismiss();
                    })
                    .show();
        }

        private void showMinecraftVersionChooser() {
            List<String> versions = WorkspaceVersionSelection.collectMinecraftVersions(mModDetail, mSelectedLoader);
            showSearchableChooser(R.string.mod_detail_step_minecraft,
                    R.string.mod_detail_search_minecraft, versions,
                    versions.indexOf(mSelectedMinecraftVersion), position -> {
                        mSelectedMinecraftVersion = versions.get(position);
                        mExtendedMinecraftSelector.setText(mSelectedMinecraftVersion);
                        bindContentVersions();
                    });
        }

        private void showContentVersionChooser() {
            if (mModDetail == null || mAvailableVersionIndices.isEmpty()) return;
            ArrayList<String> labels = new ArrayList<>();
            int selectedPosition = 0;
            for (int i = 0; i < mAvailableVersionIndices.size(); i++) {
                int detailIndex = mAvailableVersionIndices.get(i);
                labels.add(getVersionDisplayLabel(detailIndex, i == 0));
                if (detailIndex == mSelectedVersionIndex) selectedPosition = i;
            }
            showSearchableChooser(R.string.mod_detail_step_content_version,
                    R.string.mod_detail_search_content_version, labels, selectedPosition, position -> {
                        mSelectedVersionIndex = mAvailableVersionIndices.get(position);
                        mExtendedVersionSelector.setText(labels.get(position));
                        updateExtendedVersionState();
                    });
        }

        private void showSearchableChooser(int titleRes, int hintRes, List<String> labels,
                                           int selectedPosition, ChoiceListener listener) {
            if (labels == null || labels.isEmpty()) return;
            Context context = itemView.getContext();
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            int padding = dp(context, 16);
            root.setPadding(padding, dp(context, 6), padding, dp(context, 8));

            EditText search = new EditText(context);
            search.setSingleLine(true);
            search.setHint(hintRes);
            search.setTextColor(0xFFFFFFFF);
            search.setHintTextColor(0x889FB8C5);
            search.setTextSize(14);
            search.setPadding(dp(context, 14), 0, dp(context, 14), 0);
            search.setBackgroundResource(R.drawable.bg_battly_form_panel);
            root.addView(search, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46)));

            GridView grid = new GridView(context);
            grid.setNumColumns(2);
            grid.setHorizontalSpacing(dp(context, 8));
            grid.setVerticalSpacing(dp(context, 6));
            grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setSelector(new ColorDrawable(0x00000000));
            grid.setFastScrollEnabled(labels.size() > 20);
            grid.setClipToPadding(false);
            grid.setPadding(0, 0, dp(context, 2), 0);
            ChoiceAdapter adapter = new ChoiceAdapter(context, labels, selectedPosition);
            grid.setAdapter(adapter);
            boolean landscape = context.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, landscape ? 150 : 260));
            gridParams.setMargins(0, dp(context, 10), 0, 0);
            root.addView(grid, gridParams);

            AlertDialog dialog = new AlertDialog.Builder(context, R.style.BattlyDialog)
                    .setTitle(titleRes)
                    .setView(root)
                    .create();
            grid.setOnItemClickListener((parent, view, position, id) -> {
                int sourcePosition = adapter.getSourcePosition(position);
                if (sourcePosition < 0) return;
                listener.onChoice(sourcePosition);
                dialog.dismiss();
            });
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            search.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) {
                    return false;
                }
                Tools.hideKeyboard(v);
                return true;
            });
            dialog.show();
        }

        private int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        private class ChoiceAdapter extends BaseAdapter {
            private final Context context;
            private final List<String> labels;
            private final int selectedPosition;
            private List<Integer> visiblePositions;

            ChoiceAdapter(Context context, List<String> labels, int selectedPosition) {
                this.context = context;
                this.labels = labels;
                this.selectedPosition = selectedPosition;
                this.visiblePositions = WorkspaceVersionSelection.filterPositions(labels, "");
            }

            void filter(String query) {
                visiblePositions = WorkspaceVersionSelection.filterPositions(labels, query);
                notifyDataSetChanged();
            }

            int getSourcePosition(int position) {
                return position >= 0 && position < visiblePositions.size()
                        ? visiblePositions.get(position) : -1;
            }

            @Override public int getCount() { return visiblePositions.size(); }
            @Override public String getItem(int position) { return labels.get(getSourcePosition(position)); }
            @Override public long getItemId(int position) { return getSourcePosition(position); }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = convertView instanceof TextView ? (TextView) convertView : new TextView(context);
                int sourcePosition = getSourcePosition(position);
                row.setText(labels.get(sourcePosition));
                row.setTextColor(sourcePosition == selectedPosition ? 0xFF8DEEDC : 0xFFFFFFFF);
                row.setTextSize(14);
                row.setTypeface(Typeface.DEFAULT, sourcePosition == selectedPosition
                        ? Typeface.BOLD : Typeface.NORMAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setMaxLines(2);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setPadding(dp(context, 14), 0, dp(context, 14), 0);
                row.setBackgroundResource(R.drawable.bg_battly_form_field);
                row.setLayoutParams(new GridView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46)));
                return row;
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

    }

    /**
     * The view holder used to hold the progress bar at the end of the list
     */
    private static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(View view) {
            super(view);
        }
    }

    private static class NativeAdViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout mContainer;

        NativeAdViewHolder(FrameLayout container) {
            super(container);
            mContainer = container;
        }

        void bind(NativeAd ad) {
            mContainer.removeAllViews();
            mContainer.addView(BattlyNativeAdHelper.createCatalogCardView(
                    mContainer.getContext(), ad));
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
                mLoadedPageCount++;
                rebuildAdPositions();
                requestAdsForLoadedPages();
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
