package net.kdt.pojavlaunch.profiles;

import static net.kdt.pojavlaunch.extra.ExtraCore.getValue;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class VersionSelectorDialog {
    public static void open(Context context, boolean hideCustomVersions, VersionSelectorListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.BattlyDialog);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 18);
        root.setPadding(padding, padding, padding, padding);
        root.setMinimumHeight(0);

        TextView title = new TextView(context);
        title.setText(R.string.download_action_versions_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.download_version_subtitle);
        subtitle.setTextColor(0xFFC7D4DF);
        subtitle.setTextSize(12);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(context, 4), 0, dp(context, 12));
        root.addView(subtitle, subtitleParams);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(R.string.download_version_search_hint);
        search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0x99FFFFFF);
        search.setTextSize(14);
        search.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        search.setBackgroundResource(R.drawable.bg_battly_form_panel);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                0, dp(context, 48), 1f);
        searchParams.setMargins(0, 0, dp(context, 10), 0);
        toolbar.addView(search, searchParams);

        LinearLayout filters = new LinearLayout(context);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(android.view.Gravity.CENTER_VERTICAL);
        CheckBox releaseFilter = createFilter(context, R.string.mcl_setting_veroption_release, true);
        CheckBox snapshotFilter = createFilter(context, R.string.mcl_setting_veroption_snapshot, true);
        CheckBox betaFilter = createFilter(context, R.string.mcl_setting_veroption_oldbeta, false);
        CheckBox alphaFilter = createFilter(context, R.string.mcl_setting_veroption_oldalpha, false);
        filters.addView(releaseFilter);
        filters.addView(snapshotFilter);
        filters.addView(betaFilter);
        filters.addView(alphaFilter);

        HorizontalScrollView filterScroll = new HorizontalScrollView(context);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(filters);
        toolbar.addView(filterScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 48)));

        GridView listView = new GridView(context);
        listView.setNumColumns(2);
        listView.setHorizontalSpacing(dp(context, 8));
        listView.setVerticalSpacing(dp(context, 8));
        listView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        listView.setSelector(new ColorDrawable(0x00000000));
        listView.setCacheColorHint(0x00000000);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(context, 4), 0, dp(context, 18));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        listParams.setMargins(0, dp(context, 8), 0, 0);
        root.addView(listView, listParams);
        listView.setFastScrollEnabled(true);
        listView.setTextFilterEnabled(true);

        VersionOptionAdapter adapter = new VersionOptionAdapter(context, buildOptions(context, hideCustomVersions));
        listView.setAdapter(adapter);
        adapter.setEnabledTypes(releaseFilter.isChecked(), snapshotFilter.isChecked(),
                betaFilter.isChecked(), alphaFilter.isChecked());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            Tools.hideKeyboard(v);
            return true;
        });
        View.OnClickListener filterListener = v -> adapter.setEnabledTypes(
                releaseFilter.isChecked(),
                snapshotFilter.isChecked(),
                betaFilter.isChecked(),
                alphaFilter.isChecked()
        );
        releaseFilter.setOnClickListener(filterListener);
        snapshotFilter.setOnClickListener(filterListener);
        betaFilter.setOnClickListener(filterListener);
        alphaFilter.setOnClickListener(filterListener);

        builder.setView(root);
        AlertDialog dialog = builder.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            int horizontalMargin = Math.max(dp(context, 24), Math.round(screenWidth * 0.05f));
            int verticalMargin = Math.max(dp(context, 48), Math.round(screenHeight * 0.10f));
            int width = Math.min(screenWidth - horizontalMargin * 2, dp(context, 760));
            int height = Math.max(dp(context, 280), screenHeight - verticalMargin * 2);
            window.setLayout(width, height);
        }
        listView.setOnItemClickListener((parent, view, position, id) -> {
            VersionOption option = adapter.getItem(position);
            if (option == null) return;
            listener.onVersionSelected(option.id, option.snapshot);
            dialog.dismiss();
        });
    }

    private static List<VersionOption> buildOptions(Context context, boolean hideCustomVersions) {
        ArrayList<VersionOption> options = new ArrayList<>();
        if (!hideCustomVersions) {
            String[] installed = new File(Tools.DIR_GAME_NEW + "/versions").list();
            if (installed != null) {
                Arrays.sort(installed, Comparator.reverseOrder());
                for (String id : installed) {
                    options.add(new VersionOption(id, id, context.getString(R.string.mcl_setting_veroption_installed), null, null, false));
                }
            }
        }

        JMinecraftVersionList jMinecraftVersionList = (JMinecraftVersionList) getValue(ExtraConstants.RELEASE_TABLE);
        JMinecraftVersionList.Version[] versionArray = jMinecraftVersionList == null || jMinecraftVersionList.versions == null
                ? new JMinecraftVersionList.Version[0]
                : jMinecraftVersionList.versions;
        ArrayList<JMinecraftVersionList.Version> sorted = new ArrayList<>(Arrays.asList(versionArray));
        sorted.sort((left, right) -> safe(right.releaseTime).compareTo(safe(left.releaseTime)));

        for (JMinecraftVersionList.Version version : sorted) {
            String group = getGroup(context, version.type);
            boolean snapshot = "snapshot".equals(version.type);
            String displayId = "1.0.0".equals(version.id) ? "1.0.0 (1.0)" : version.id;
            options.add(new VersionOption(version.id, displayId, group, version.type, formatDate(version.releaseTime), snapshot));
        }
        return options;
    }

    private static String getGroup(Context context, String type) {
        if ("snapshot".equals(type)) return context.getString(R.string.mcl_setting_veroption_snapshot);
        if ("old_beta".equals(type)) return context.getString(R.string.mcl_setting_veroption_oldbeta);
        if ("old_alpha".equals(type)) return context.getString(R.string.mcl_setting_veroption_oldalpha);
        return context.getString(R.string.mcl_setting_veroption_release);
    }

    private static String formatDate(String value) {
        if (!Tools.isValidString(value)) return null;
        int index = value.indexOf('T');
        return index > 0 ? value.substring(0, index) : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static CheckBox createFilter(Context context, int textRes, boolean checked) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(textRes);
        checkBox.setChecked(checked);
        checkBox.setTextColor(0xFFE8F3F7);
        checkBox.setTextSize(12);
        checkBox.setButtonTintList(ColorStateList.valueOf(0xFF8DEEDC));
        checkBox.setPadding(0, 0, dp(context, 6), 0);
        return checkBox;
    }

    private static class VersionOption {
        final String id;
        final String displayId;
        final String group;
        final String type;
        final String date;
        final boolean snapshot;

        VersionOption(String id, String displayId, String group, String type, String date, boolean snapshot) {
            this.id = id;
            this.displayId = displayId;
            this.group = group;
            this.type = type;
            this.date = date;
            this.snapshot = snapshot;
        }
    }

    private static class VersionOptionAdapter extends BaseAdapter {
        private final Context context;
        private final List<VersionOption> allOptions;
        private final ArrayList<VersionOption> visibleOptions = new ArrayList<>();
        private String currentQuery = "";
        private boolean showRelease = true;
        private boolean showSnapshot = true;
        private boolean showBeta = false;
        private boolean showAlpha = false;

        VersionOptionAdapter(Context context, List<VersionOption> allOptions) {
            this.context = context;
            this.allOptions = allOptions;
            applyFilters();
        }

        void filter(String query) {
            currentQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            applyFilters();
        }

        void setEnabledTypes(boolean release, boolean snapshot, boolean beta, boolean alpha) {
            showRelease = release;
            showSnapshot = snapshot;
            showBeta = beta;
            showAlpha = alpha;
            applyFilters();
        }

        private void applyFilters() {
            visibleOptions.clear();
            for (VersionOption option : allOptions) {
                if (!matchesType(option)) {
                    continue;
                }
                if (currentQuery.isEmpty()
                        || option.id.toLowerCase(Locale.ROOT).contains(currentQuery)
                        || option.displayId.toLowerCase(Locale.ROOT).contains(currentQuery)
                        || option.group.toLowerCase(Locale.ROOT).contains(currentQuery)
                        || (option.date != null && option.date.contains(currentQuery))) {
                    visibleOptions.add(option);
                }
            }
            notifyDataSetChanged();
        }

        private boolean matchesType(VersionOption option) {
            if (option.type == null) {
                return true;
            }
            switch (option.type) {
                case "snapshot":
                    return showSnapshot;
                case "old_beta":
                    return showBeta;
                case "old_alpha":
                    return showAlpha;
                case "release":
                default:
                    return showRelease;
            }
        }

        @Override public int getCount() {
            return Math.max(visibleOptions.size(), 1);
        }

        @Override public VersionOption getItem(int position) {
            if (visibleOptions.isEmpty()) return null;
            return visibleOptions.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public boolean isEnabled(int position) {
            return !visibleOptions.isEmpty();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (visibleOptions.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText(R.string.download_version_empty_search);
                empty.setTextColor(0xFFC7D4DF);
                empty.setTextSize(14);
                empty.setPadding(dp(context, 14), dp(context, 20), dp(context, 14), dp(context, 20));
                return empty;
            }
            VersionOption option = visibleOptions.get(position);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));
            row.setBackgroundResource(R.drawable.bg_battly_form_panel);

            TextView id = new TextView(context);
            id.setText(option.displayId);
            id.setTextColor(0xFFFFFFFF);
            id.setTextSize(15);
            id.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(id);

            TextView meta = new TextView(context);
            meta.setText(option.date == null ? option.group : option.group + " · " + option.date);
            meta.setTextColor(0xFF9FB8C5);
            meta.setTextSize(11);
            row.addView(meta);

            GridView.LayoutParams params = new GridView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 68)
            );
            row.setLayoutParams(params);
            return row;
        }
    }
}
