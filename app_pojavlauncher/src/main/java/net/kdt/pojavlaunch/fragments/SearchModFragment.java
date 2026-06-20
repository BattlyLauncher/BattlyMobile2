package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.SimpleArrayAdapter;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchCategory;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    public static final String ARG_CONTENT_TYPE = "content_type";
    private View mOverlay;
    private EditText mSearchEditText;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private TextView mSubtitleTextView;
    private ColorStateList mDefaultTextColor;

    private ModpackApi modpackApi;

    private final SearchFilters mSearchFilters;

    public SearchModFragment() {
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.contentType = SearchFilters.TYPE_MODPACK;
    }

    public static Bundle createArguments(int contentType) {
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_CONTENT_TYPE, contentType);
        return bundle;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        modpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if (arguments != null) {
            mSearchFilters.contentType = arguments.getInt(ARG_CONTENT_TYPE, SearchFilters.TYPE_MODPACK);
        }

        // You can only access resources after attaching to current context
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mSubtitleTextView = view.findViewById(R.id.search_mod_subtitle);

        mDefaultTextColor = mStatusTextView.getTextColors();
        if (mSubtitleTextView != null) mSubtitleTextView.setText(getSubtitleRes());
        mSearchEditText.setHint(getHintRes());

        mRecyclerview.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mRecyclerview.setAdapter(mModItemAdapter);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        setupFilters(view);

        ImageButton filterButton = view.findViewById(R.id.search_mod_filter_button);
        androidx.drawerlayout.widget.DrawerLayout drawer = view.findViewById(R.id.search_mod_drawer);
        if (filterButton != null && drawer != null) {
            filterButton.setOnClickListener(v -> drawer.openDrawer(androidx.core.view.GravityCompat.END));
        }

        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(getErrorRes());
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(getNoResultsRes());
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void setupFilters(View view) {
        Spinner mSourceSpinner = view.findViewById(R.id.search_mod_source_spinner);
        Spinner mLoaderSpinner = view.findViewById(R.id.search_mod_loader_spinner);
        Spinner mCategorySpinner = view.findViewById(R.id.search_mod_category_spinner);
        TextView mSelectedVersion = view.findViewById(R.id.search_mod_selected_mc_version_textview);
        Button mSelectVersionButton = view.findViewById(R.id.search_mod_mc_version_button);
        Button mApplyButton = view.findViewById(R.id.search_mod_apply_filters);
        Button mResetButton = view.findViewById(R.id.search_mod_reset_filters);

        if (mSourceSpinner == null)
            return; // safeguard

        SimpleArrayAdapter<FilterOption<Integer>> sourceAdapter = new SimpleArrayAdapter<>(
                java.util.Arrays.asList(getSourceOptions()));
        SimpleArrayAdapter<FilterOption<String>> loaderAdapter = new SimpleArrayAdapter<>(
                java.util.Arrays.asList(getLoaderOptions()));
        SimpleArrayAdapter<SearchCategory> categoryAdapter = new SimpleArrayAdapter<>(new java.util.ArrayList<>());

        mSourceSpinner.setAdapter(sourceAdapter);
        mLoaderSpinner.setAdapter(loaderAdapter);
        mCategorySpinner.setAdapter(categoryAdapter);

        mSelectVersionButton.setOnClickListener(
                v -> VersionSelectorDialog.open(v.getContext(), true, (id, snapshot) -> mSelectedVersion.setText(id)));

        mSelectedVersion.setText(mSearchFilters.mcVersion);
        setSpinnerSelection(mSourceSpinner, sourceAdapter,
                option -> option.value != null && option.value.equals(mSearchFilters.source));
        setSpinnerSelection(mLoaderSpinner, loaderAdapter, option -> option.value
                .equals(mSearchFilters.loader == null ? SearchFilters.LOADER_ANY : mSearchFilters.loader));
        bindCategories(categoryAdapter, mSourceSpinner, mCategorySpinner, mSearchFilters.category);
        mSourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                bindCategories(categoryAdapter, mSourceSpinner, mCategorySpinner, mSearchFilters.category);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mApplyButton.setOnClickListener(v -> {
            FilterOption<Integer> sourceOption = getSelectedOption(mSourceSpinner);
            FilterOption<String> loaderOption = getSelectedOption(mLoaderSpinner);
            SearchCategory selectedCategory = getSelectedCategory(mCategorySpinner);

            mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
            mSearchFilters.source = sourceOption == null ? SearchFilters.SOURCE_ANY : sourceOption.value;
            mSearchFilters.loader = loaderOption == null ? SearchFilters.LOADER_ANY : loaderOption.value;
            mSearchFilters.category = selectedCategory;
            searchMods(mSearchEditText.getText().toString());
        });

        mResetButton.setOnClickListener(v -> {
            mSearchFilters.mcVersion = null;
            mSearchFilters.source = SearchFilters.SOURCE_ANY;
            mSearchFilters.loader = SearchFilters.LOADER_ANY;
            mSearchFilters.category = null;
            mSelectedVersion.setText(null);
            mSourceSpinner.setSelection(0);
            mLoaderSpinner.setSelection(0);
            bindCategories(categoryAdapter, mSourceSpinner, mCategorySpinner, null);
            searchMods(mSearchEditText.getText().toString());
        });
    }

    private void bindCategories(SimpleArrayAdapter<SearchCategory> categoryAdapter, Spinner sourceSpinner,
            Spinner categorySpinner, @Nullable SearchCategory currentCategory) {
        FilterOption<Integer> selectedSource = getSelectedOption(sourceSpinner);
        if (selectedSource == null || selectedSource.value == SearchFilters.SOURCE_ANY) {
            categoryAdapter.setObjects(java.util.Collections.singletonList(new SearchCategory(SearchFilters.SOURCE_ANY,
                    "", getString(R.string.search_filter_all_categories))));
            categorySpinner.setEnabled(false);
            categorySpinner.setSelection(0);
            return;
        }

        categorySpinner.setEnabled(false);
        categoryAdapter.setObjects(java.util.Collections.singletonList(
                new SearchCategory(selectedSource.value, "", getString(R.string.search_filter_loading_categories))));
        PojavApplication.sExecutorService.execute(() -> {
            SearchFilters categoryFilters = new SearchFilters();
            categoryFilters.contentType = mSearchFilters.contentType;
            categoryFilters.source = selectedSource.value;
            SearchCategory[] fetchedCategories = modpackApi.getCategories(categoryFilters);
            Tools.runOnUiThread(() -> {
                java.util.ArrayList<SearchCategory> categories = new java.util.ArrayList<>();
                categories.add(
                        new SearchCategory(selectedSource.value, "", getString(R.string.search_filter_all_categories)));
                categories.addAll(java.util.Arrays.asList(fetchedCategories));
                categoryAdapter.setObjects(categories);
                categorySpinner.setEnabled(true);
                if (currentCategory != null && currentCategory.source == selectedSource.value) {
                    setSpinnerSelection(categorySpinner, categoryAdapter,
                            option -> option.id.equals(currentCategory.id));
                } else {
                    categorySpinner.setSelection(0);
                }
            });
        });
    }

    private FilterOption<Integer>[] getSourceOptions() {
        return new FilterOption[] {
                new FilterOption<>(getString(R.string.search_filter_source_any), SearchFilters.SOURCE_ANY),
                new FilterOption<>(getString(R.string.search_filter_source_modrinth), Constants.SOURCE_MODRINTH),
                new FilterOption<>(getString(R.string.search_filter_source_curseforge), Constants.SOURCE_CURSEFORGE)
        };
    }

    private FilterOption<String>[] getLoaderOptions() {
        if (mSearchFilters.isResourcepack() || mSearchFilters.isShader() || mSearchFilters.isDatapack()) {
            return new FilterOption[] {
                    new FilterOption<>(getString(R.string.search_filter_loader_any), SearchFilters.LOADER_ANY) };
        }
        return new FilterOption[] {
                new FilterOption<>(getString(R.string.search_filter_loader_any), SearchFilters.LOADER_ANY),
                new FilterOption<>("Forge", "forge"),
                new FilterOption<>("Fabric", "fabric"),
                new FilterOption<>("Quilt", "quilt"),
                new FilterOption<>("NeoForge", "neoforge")
        };
    }

    private String toLoaderLabel(String loader) {
        if ("forge".equalsIgnoreCase(loader))
            return "Forge";
        if ("fabric".equalsIgnoreCase(loader))
            return "Fabric";
        if ("quilt".equalsIgnoreCase(loader))
            return "Quilt";
        if ("neoforge".equalsIgnoreCase(loader))
            return "NeoForge";
        return loader;
    }

    @SuppressWarnings("unchecked")
    private <T> T getSelectedOption(Spinner spinner) {
        return (T) spinner.getSelectedItem();
    }

    private SearchCategory getSelectedCategory(Spinner spinner) {
        SearchCategory category = (SearchCategory) spinner.getSelectedItem();
        if (category == null || !Tools.isValidString(category.id)) {
            return null;
        }
        return category;
    }

    private <T> void setSpinnerSelection(Spinner spinner, SimpleArrayAdapter<T> adapter,
            SelectorPredicate<T> predicate) {
        for (int i = 0; i < adapter.getCount(); i++) {
            T item = adapter.getItem(i);
            if (item != null && predicate.matches(item)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private int getTitleRes() {
        switch (mSearchFilters.contentType) {
            case SearchFilters.TYPE_MOD:
                return R.string.search_mod_title;
            case SearchFilters.TYPE_RESOURCEPACK:
                return R.string.search_resourcepack_title;
            case SearchFilters.TYPE_SHADER:
                return R.string.search_shader_title;
            case SearchFilters.TYPE_DATAPACK:
                return R.string.search_datapack_title;
            case SearchFilters.TYPE_MODPACK:
            default:
                return R.string.search_modpack_title;
        }
    }

    private int getSubtitleRes() {
        switch (mSearchFilters.contentType) {
            case SearchFilters.TYPE_MOD:
                return R.string.search_mod_subtitle;
            case SearchFilters.TYPE_RESOURCEPACK:
                return R.string.search_resourcepack_subtitle;
            case SearchFilters.TYPE_SHADER:
                return R.string.search_shader_subtitle;
            case SearchFilters.TYPE_DATAPACK:
                return R.string.search_datapack_subtitle;
            case SearchFilters.TYPE_MODPACK:
            default:
                return R.string.search_modpack_subtitle;
        }
    }

    private int getHintRes() {
        switch (mSearchFilters.contentType) {
            case SearchFilters.TYPE_MOD:
                return R.string.hint_search_mod;
            case SearchFilters.TYPE_RESOURCEPACK:
                return R.string.hint_search_resourcepack;
            case SearchFilters.TYPE_SHADER:
                return R.string.hint_search_shader;
            case SearchFilters.TYPE_DATAPACK:
                return R.string.hint_search_datapack;
            case SearchFilters.TYPE_MODPACK:
            default:
                return R.string.hint_search_modpack;
        }
    }

    private int getNoResultsRes() {
        switch (mSearchFilters.contentType) {
            case SearchFilters.TYPE_MOD:
                return R.string.search_mod_no_result;
            case SearchFilters.TYPE_RESOURCEPACK:
                return R.string.search_resourcepack_no_result;
            case SearchFilters.TYPE_SHADER:
                return R.string.search_shader_no_result;
            case SearchFilters.TYPE_DATAPACK:
                return R.string.search_datapack_no_result;
            case SearchFilters.TYPE_MODPACK:
            default:
                return R.string.search_modpack_no_result;
        }
    }

    private int getErrorRes() {
        switch (mSearchFilters.contentType) {
            case SearchFilters.TYPE_MOD:
                return R.string.search_mod_error;
            case SearchFilters.TYPE_RESOURCEPACK:
                return R.string.search_resourcepack_error;
            case SearchFilters.TYPE_SHADER:
                return R.string.search_shader_error;
            case SearchFilters.TYPE_DATAPACK:
                return R.string.search_datapack_error;
            case SearchFilters.TYPE_MODPACK:
            default:
                return R.string.search_modpack_error;
        }
    }

    private interface SelectorPredicate<T> {
        boolean matches(T item);
    }

    private static final class FilterOption<T> {
        private final String label;
        private final T value;

        private FilterOption(String label, T value) {
            this.label = label;
            this.value = value;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }
}
