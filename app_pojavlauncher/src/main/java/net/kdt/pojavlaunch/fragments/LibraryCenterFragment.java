package net.kdt.pojavlaunch.fragments;

import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.utils.ContentDependencyAnalyzer;
import net.kdt.pojavlaunch.utils.InstalledContentIconLoader;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LibraryCenterFragment extends Fragment {
    public static final String TAG = "LibraryCenterFragment";
    private static final Pattern JSON_NAME = Pattern.compile("\"(?:name|modid|id|title)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JSON_VERSION = Pattern.compile("\"(?:version|mod_version)\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TOML_NAME = Pattern.compile("displayName\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TOML_VERSION = Pattern.compile("version\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TOML_ICON = Pattern.compile("logoFile\\s*=\\s*\"([^\"]+)\"");
    private LinearLayout mInstalledContentContainer;
    private Button mInstanceSelector;
    private MinecraftProfile mSelectedProfile;
    private String mSelectedProfileKey;
    private int mInstalledContentRequestId;

    public LibraryCenterFragment() {
        super(R.layout.fragment_library_center);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mInstalledContentContainer = view.findViewById(R.id.library_installed_content_container);
        mInstanceSelector = view.findViewById(R.id.library_instance_selector);
        selectInitialProfile();
        mInstanceSelector.setOnClickListener(v -> showInstanceSelector());
        view.findViewById(R.id.download_panel_import_modpack)
                .setOnClickListener(v -> ((LauncherActivity) requireActivity()).modpackImportLauncher.launch(null));
        view.findViewById(R.id.download_panel_browse_modpacks).setOnClickListener(v -> openSearch(SearchFilters.TYPE_MODPACK));
        view.findViewById(R.id.download_panel_browse_mods).setOnClickListener(v -> openSearch(SearchFilters.TYPE_MOD));
        view.findViewById(R.id.download_panel_browse_resourcepacks).setOnClickListener(v -> openSearch(SearchFilters.TYPE_RESOURCEPACK));
        view.findViewById(R.id.download_panel_browse_shaders).setOnClickListener(v -> openSearch(SearchFilters.TYPE_SHADER));
        view.findViewById(R.id.download_panel_browse_datapacks).setOnClickListener(v -> openSearch(SearchFilters.TYPE_DATAPACK));
        view.findViewById(R.id.download_panel_browse_worlds).setOnClickListener(v -> openSearch(SearchFilters.TYPE_WORLD));
        view.findViewById(R.id.download_panel_battly_skins).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), BattlySkinManagerFragment.class, BattlySkinManagerFragment.TAG, null));
        view.findViewById(R.id.download_panel_instances).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), InstanceManagerFragment.class, InstanceManagerFragment.TAG, null));
        view.findViewById(R.id.download_panel_file_manager).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), BattlyFileManagerFragment.class,
                        BattlyFileManagerFragment.TAG,
                        BattlyFileManagerFragment.createArguments(new File(Tools.DIR_GAME_HOME))));
        bindInstalledContent();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mInstalledContentContainer != null) {
            bindInstalledContent();
        }
    }

    private void openSearch(int contentType) {
        Tools.swapFragment(
                requireActivity(),
                SearchModFragment.class,
                SearchModFragment.TAG,
                SearchModFragment.createArguments(contentType)
        );
    }

    private void bindInstalledContent() {
        mInstalledContentContainer.removeAllViews();
        if (mSelectedProfile == null) selectInitialProfile();
        File gameDir = Tools.getGameDirPath(mSelectedProfile);
        int requestId = ++mInstalledContentRequestId;
        PojavApplication.sExecutorService.execute(() -> {
            List<InstalledSection> sections = new ArrayList<>();
            sections.add(scanInstalledSection(R.string.library_installed_mods, new File(gameDir, "mods")));
            sections.add(scanInstalledSection(R.string.library_installed_resourcepacks, new File(gameDir, "resourcepacks")));
            sections.add(scanInstalledSection(R.string.library_installed_shaders, new File(gameDir, "shaderpacks")));
            sections.add(scanInstalledSection(R.string.library_installed_datapacks, new File(gameDir, "datapacks")));
            sections.add(scanInstalledSection(R.string.world_manager_title, new File(gameDir, "saves")));
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mInstalledContentContainer == null || requestId != mInstalledContentRequestId) {
                    return;
                }
                mInstalledContentContainer.removeAllViews();
                for (InstalledSection section : sections) {
                    addInstalledSection(section);
                }
            });
        });
    }

    private InstalledSection scanInstalledSection(int titleRes, File directory) {
        File[] files = directory.exists()
                ? directory.listFiles(file -> !file.getName().startsWith(".") && (file.isFile() || file.isDirectory()))
                : null;
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(file -> file.getName().toLowerCase()));
        List<ContentInfo> entries = new ArrayList<>();
        for (File file : files) {
            entries.add(readContentInfo(titleRes, file));
        }
        long totalBytes = 0L;
        int folders = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                folders++;
            }
            totalBytes += getSize(file);
        }
        return new InstalledSection(titleRes, directory, files.length, folders, totalBytes, entries);
    }

    private void addInstalledSection(InstalledSection section) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_battly_form_panel);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardParams);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(requireContext());
        title.setText(section.titleRes);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView count = new TextView(requireContext());
        count.setText(getResources().getQuantityString(R.plurals.library_installed_count, section.fileCount, section.fileCount));
        count.setTextColor(0xFFC7D4DF);
        count.setTextSize(12);
        header.addView(count);
        if (section.titleRes == R.string.library_installed_mods && section.fileCount > 0) {
            Button analyze = actionButton(R.string.library_analyze_mods, android.R.drawable.ic_menu_info_details,
                    v -> analyzeMods(section.directory));
            LinearLayout.LayoutParams analyzeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            analyzeParams.setMargins(dp(8), 0, 0, 0);
            header.addView(analyze, analyzeParams);
        }
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> Tools.openPath(requireContext(), section.directory, false));
        card.addView(header);

        TextView detail = new TextView(requireContext());
        detail.setText(buildInstalledDetail(section));
        detail.setTextColor(0xFF93AEBB);
        detail.setTextSize(12);
        detail.setPadding(0, dp(8), 0, 0);
        card.addView(detail);

        if (section.entries.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.library_installed_empty);
            empty.setTextColor(0xFFC7D4DF);
            empty.setTextSize(12);
            empty.setPadding(0, dp(10), 0, 0);
            card.addView(empty);
        } else {
            int visibleCount = Math.min(section.entries.size(), 5);
            for (int i = 0; i < visibleCount; i++) {
                card.addView(createContentRow(section.entries.get(i)));
            }
            if (section.entries.size() > visibleCount) {
                TextView more = new TextView(requireContext());
                more.setText(getString(R.string.library_installed_more, section.entries.size() - visibleCount));
                more.setTextColor(0xFF8DEEDC);
                more.setTypeface(Typeface.DEFAULT_BOLD);
                more.setTextSize(12);
                more.setPadding(0, dp(8), 0, 0);
                card.addView(more);
            }
        }

        mInstalledContentContainer.addView(card);
    }

    private void analyzeMods(File directory) {
        Toast.makeText(requireContext(), R.string.global_wait, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            ContentDependencyAnalyzer.Report report = ContentDependencyAnalyzer.analyze(directory);
            Tools.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (report.healthy()) {
                    Toast.makeText(requireContext(), getString(R.string.library_mods_healthy, report.mods.size()), Toast.LENGTH_LONG).show();
                    return;
                }
                String[] labels = new String[report.issues.size()];
                for (int i = 0; i < labels.length; i++) {
                    ContentDependencyAnalyzer.Issue issue = report.issues.get(i);
                    labels[i] = issue.severity + " | " + issue.title + "\n" + issue.detail;
                }
                Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                        .setTitle(R.string.library_dependency_report)
                        .setItems(labels, null)
                        .setPositiveButton(android.R.string.ok, null));
            });
        });
    }

    private String buildInstalledDetail(InstalledSection section) {
        if (section.fileCount == 0) {
            return "";
        }
        String size = formatSize(section.totalBytes);
        if (section.folderCount > 0) {
            return getString(R.string.library_installed_detail_with_folders, size, section.folderCount);
        }
        return getString(R.string.library_installed_detail, size);
    }

    private View createContentRow(ContentInfo info) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_battly_form_section);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        row.setLayoutParams(params);

        LinearLayout content = new LinearLayout(requireContext());
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setOrientation(LinearLayout.HORIZONTAL);

        ImageView icon = new ImageView(requireContext());
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageResource(android.R.drawable.ic_menu_gallery);
        icon.setColorFilter(0xFF8DEEDC);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackgroundResource(R.drawable.bg_battly_profile_icon);
        icon.setClipToOutline(true);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        iconParams.setMargins(0, 0, dp(12), 0);
        content.addView(icon, iconParams);

        LinearLayout details = new LinearLayout(requireContext());
        details.setOrientation(LinearLayout.VERTICAL);
        content.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(requireContext());
        title.setText(info.name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView badge = new TextView(requireContext());
        badge.setText(info.badge);
        badge.setTextColor(0xFF8DEEDC);
        badge.setTextSize(11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(badge);
        details.addView(top);

        TextView meta = new TextView(requireContext());
        meta.setText(info.metaLine());
        meta.setTextColor(0xFF9FB8C5);
        meta.setTextSize(11);
        meta.setPadding(0, dp(2), 0, 0);
        details.addView(meta);

        if (Tools.isValidString(info.description)) {
            TextView description = new TextView(requireContext());
            description.setText(info.description);
            description.setTextColor(0xFFC7D4DF);
            description.setTextSize(12);
            description.setMaxLines(2);
            description.setPadding(0, dp(6), 0, 0);
            details.addView(description);
        }
        row.addView(content);
        loadContentIcon(icon, info);
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);
        actions.addView(actionButton(R.string.library_content_open, android.R.drawable.ic_menu_view,
                v -> Tools.openPath(requireContext(), info.file, false)));
        if (info.supportsToggle) {
            actions.addView(actionButton(info.enabled ? R.string.library_content_disable : R.string.library_content_enable,
                    info.enabled ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                    v -> toggleContent(info)));
        }
        actions.addView(actionButton(R.string.library_content_move, android.R.drawable.ic_menu_send,
                v -> showMoveContentDialog(info)));
        actions.addView(actionButton(R.string.library_content_delete, android.R.drawable.ic_menu_delete,
                v -> confirmDelete(info)));
        row.addView(actions);
        return row;
    }

    private void loadContentIcon(ImageView imageView, ContentInfo info) {
        String tag = info.file.getAbsolutePath() + ':' + info.file.lastModified();
        imageView.setTag(tag);
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = InstalledContentIconLoader.load(info.file, info.iconPath);
            if (bitmap == null) return;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || !tag.equals(imageView.getTag())) return;
                imageView.clearColorFilter();
                imageView.setPadding(0, 0, 0, 0);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setImageBitmap(bitmap);
            });
        });
    }

    private void selectInitialProfile() {
        LauncherProfiles.load();
        MinecraftProfile current = LauncherProfiles.getCurrentProfile();
        mSelectedProfile = current;
        mSelectedProfileKey = "";
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            if (entry.getValue() == current) {
                mSelectedProfileKey = entry.getKey();
                break;
            }
        }
        updateInstanceSelectorLabel();
    }

    private void showInstanceSelector() {
        LauncherProfiles.load();
        List<Map.Entry<String, MinecraftProfile>> profiles = new ArrayList<>(
                LauncherProfiles.mainProfileJson.profiles.entrySet());
        profiles.sort(Comparator.comparing(entry -> profileLabel(entry.getValue()).toLowerCase(Locale.ROOT)));
        String[] labels = new String[profiles.size()];
        int selected = -1;
        for (int i = 0; i < profiles.size(); i++) {
            Map.Entry<String, MinecraftProfile> entry = profiles.get(i);
            labels[i] = profileLabel(entry.getValue());
            if (entry.getKey().equals(mSelectedProfileKey)) selected = i;
        }
        AlertDialog dialog = Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.library_instance_selector_title)
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    Map.Entry<String, MinecraftProfile> entry = profiles.get(which);
                    mSelectedProfileKey = entry.getKey();
                    mSelectedProfile = entry.getValue();
                    updateInstanceSelectorLabel();
                    bindInstalledContent();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Tools.styleDialog(dialog);
        dialog.show();
    }

    private void updateInstanceSelectorLabel() {
        if (mInstanceSelector == null || mSelectedProfile == null) return;
        mInstanceSelector.setText(getString(
                R.string.library_instance_selector_value, profileLabel(mSelectedProfile)));
    }

    private String profileLabel(MinecraftProfile profile) {
        String name = profile == null || !Tools.isValidString(profile.name)
                ? getString(R.string.global_default)
                : profile.name;
        String version = profile == null || !Tools.isValidString(profile.lastVersionId)
                ? "-" : profile.lastVersionId;
        return name + " · " + version;
    }

    private Button actionButton(int textRes, int iconRes, View.OnClickListener listener) {
        Button button = new Button(requireContext());
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextColor(0xFF8DEEDC);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundResource(R.drawable.bg_battly_button_secondary);
        Drawable icon = requireContext().getDrawable(iconRes);
        if (icon != null) {
            int size = dp(16);
            icon.setBounds(0, 0, size, size);
            icon.setTint(0xFF8DEEDC);
            button.setCompoundDrawables(icon, null, null, null);
            button.setCompoundDrawablePadding(dp(5));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }

    private ContentInfo readContentInfo(int titleRes, File file) {
        ContentInfo info = new ContentInfo();
        info.name = cleanFileName(file.getName());
        info.version = "";
        info.description = "";
        info.size = formatSize(getSize(file));
        info.file = file;
        info.sectionTitleRes = titleRes;
        info.supportsToggle = titleRes != R.string.world_manager_title;
        info.enabled = !file.getName().toLowerCase(Locale.ROOT).endsWith(".disabledmod");
        if (titleRes == R.string.library_installed_resourcepacks) {
            info.enabled = isResourcePackEnabled(file);
        }
        info.badge = titleRes == R.string.library_installed_mods ? "MOD"
                : titleRes == R.string.library_installed_resourcepacks ? "PACK"
                : titleRes == R.string.library_installed_shaders ? "SHADER"
                : titleRes == R.string.world_manager_title ? "WORLD" : "DATA";

        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        if (titleRes == R.string.library_installed_mods && lowerName.endsWith(".jar")) {
            applyModJson(info, readZipEntry(file, "fabric.mod.json"), "Fabric");
            applyModJson(info, readZipEntry(file, "quilt.mod.json"), "Quilt");
            applyMcModInfo(info, readZipEntry(file, "mcmod.info"));
            applyToml(info, readZipEntry(file, "META-INF/mods.toml"));
        }
        if ((titleRes == R.string.library_installed_resourcepacks
                || titleRes == R.string.library_installed_datapacks)
                && (lowerName.endsWith(".zip") || file.isDirectory())) {
            String packMeta = file.isDirectory()
                    ? readFile(new File(file, "pack.mcmeta"))
                    : readZipEntry(file, "pack.mcmeta");
            applyPackMeta(info, packMeta);
            info.iconPath = "pack.png";
        }
        if (titleRes == R.string.library_installed_shaders) {
            info.iconPath = "pack.png";
        }
        if (titleRes == R.string.world_manager_title) {
            info.iconPath = "icon.png";
        }
        return info;
    }

    private void applyModJson(ContentInfo info, String text, String loader) {
        if (text == null || Tools.isValidString(info.loader)) return;
        try {
            JSONObject object = new JSONObject(text);
            info.name = object.optString("name", object.optString("id", info.name));
            info.version = object.optString("version", info.version);
            info.description = object.optString("description", info.description);
            info.loader = loader;
            info.iconPath = readJsonIcon(object, info.iconPath);
            JSONObject depends = object.optJSONObject("depends");
            if (depends != null) {
                info.minecraft = depends.optString("minecraft", "");
            }
        } catch (Exception ignored) {
            applyJsonRegex(info, text, loader);
        }
    }

    private void applyMcModInfo(ContentInfo info, String text) {
        if (text == null || Tools.isValidString(info.loader)) return;
        try {
            JSONArray array = new JSONArray(text);
            if (array.length() == 0) return;
            JSONObject object = array.optJSONObject(0);
            if (object == null) return;
            info.name = object.optString("name", info.name);
            info.version = object.optString("version", info.version);
            info.description = object.optString("description", info.description);
            info.minecraft = object.optString("mcversion", info.minecraft);
            info.loader = "Forge";
            info.iconPath = object.optString("logoFile", info.iconPath);
        } catch (Exception ignored) {
            applyJsonRegex(info, text, "Forge");
        }
    }

    private void applyJsonRegex(ContentInfo info, String text, String loader) {
        Matcher nameMatcher = JSON_NAME.matcher(text);
        if (nameMatcher.find()) {
            info.name = nameMatcher.group(1);
            Matcher versionMatcher = JSON_VERSION.matcher(text);
            if (versionMatcher.find()) info.version = versionMatcher.group(1);
            info.loader = loader;
        }
    }

    private void applyToml(ContentInfo info, String text) {
        if (text == null || Tools.isValidString(info.loader)) return;
        Matcher nameMatcher = TOML_NAME.matcher(text);
        if (!nameMatcher.find()) return;
        info.name = nameMatcher.group(1);
        Matcher versionMatcher = TOML_VERSION.matcher(text);
        if (versionMatcher.find()) info.version = versionMatcher.group(1);
        Matcher descriptionMatcher = Pattern.compile("description\\s*=\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(text);
        if (descriptionMatcher.find()) info.description = descriptionMatcher.group(1).replace("\\n", " ").trim();
        Matcher loaderMatcher = Pattern.compile("modLoader\\s*=\\s*\"([^\"]+)\"").matcher(text);
        info.loader = loaderMatcher.find() ? loaderMatcher.group(1) : "Forge";
        Matcher iconMatcher = TOML_ICON.matcher(text);
        if (iconMatcher.find()) info.iconPath = iconMatcher.group(1);
    }

    private String readJsonIcon(JSONObject object, String fallback) {
        Object icon = object.opt("icon");
        if (icon instanceof String) return (String) icon;
        if (icon instanceof JSONObject) {
            JSONObject icons = (JSONObject) icon;
            String selected = fallback;
            int selectedSize = -1;
            java.util.Iterator<String> keys = icons.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int size;
                try {
                    size = Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                    size = 0;
                }
                if (size >= selectedSize) {
                    selected = icons.optString(key, selected);
                    selectedSize = size;
                }
            }
            return selected;
        }
        JSONObject quiltLoader = object.optJSONObject("quilt_loader");
        if (quiltLoader != null) {
            JSONObject metadata = quiltLoader.optJSONObject("metadata");
            if (metadata != null) return readJsonIcon(metadata, fallback);
        }
        return fallback;
    }

    private void applyPackMeta(ContentInfo info, String text) {
        if (text == null) return;
        try {
            JSONObject pack = new JSONObject(text).optJSONObject("pack");
            if (pack == null) return;
            Object description = pack.opt("description");
            if (description instanceof JSONObject) {
                info.description = ((JSONObject) description).optString("text", "");
            } else if (description != null) {
                info.description = String.valueOf(description);
            }
            int format = pack.optInt("pack_format", -1);
            if (format >= 0) info.version = "pack_format " + format;
        } catch (Exception ignored) {
            Matcher description = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
            if (description.find()) info.description = description.group(1);
        }
    }

    private String readZipEntry(File file, String entryName) {
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) return null;
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                return Tools.read(inputStream);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readFile(File file) {
        try {
            if (!file.exists() || !file.isFile()) return null;
            return Tools.read(file);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void toggleContent(ContentInfo info) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (info.sectionTitleRes == R.string.library_installed_resourcepacks) {
                    setResourcePackEnabled(info.file, !info.enabled);
                } else {
                    File target = getToggleTarget(info.file, info.sectionTitleRes, !info.enabled);
                    if (target == null || !info.file.renameTo(target)) {
                        throw new IllegalStateException("No se pudo renombrar el archivo");
                    }
                }
                Tools.runOnUiThread(() -> {
                    Toast.makeText(requireContext(), R.string.library_content_updated, Toast.LENGTH_SHORT).show();
                    bindInstalledContent();
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private File getToggleTarget(File file, int sectionTitleRes, boolean enable) {
        String name = file.getName();
        File parent = file.getParentFile();
        if (parent == null) return null;
        if (sectionTitleRes == R.string.library_installed_mods) {
            if (enable && name.endsWith(".disabledmod")) {
                return new File(parent, name.substring(0, name.length() - ".disabledmod".length()) + ".jar");
            }
            if (!enable) {
                return new File(parent, cleanFileName(name) + ".disabledmod");
            }
        }
        String disabledSuffix = sectionTitleRes == R.string.library_installed_shaders ? ".disabledshader" : ".disableddatapack";
        if (enable && name.endsWith(disabledSuffix)) {
            return new File(parent, name.substring(0, name.length() - disabledSuffix.length()) + ".zip");
        }
        if (!enable) {
            return new File(parent, cleanFileName(name) + disabledSuffix);
        }
        return null;
    }

    private boolean isResourcePackEnabled(File file) {
        try {
            File options = new File(Tools.getGameDirPath(LauncherProfiles.getCurrentProfile()), "options.txt");
            if (!options.isFile()) return false;
            String text = Tools.read(options.getAbsolutePath());
            String name = file.getName();
            return text.contains("\"file/" + name + "\"") || text.contains("\"" + name + "\"");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void setResourcePackEnabled(File file, boolean enabled) throws Exception {
        File options = new File(Tools.getGameDirPath(LauncherProfiles.getCurrentProfile()), "options.txt");
        String text = options.isFile() ? Tools.read(options.getAbsolutePath()) : "";
        String name = file.getName();
        text = text.replace("\"file/" + name + "\",", "")
                .replace(",\"file/" + name + "\"", "")
                .replace("\"file/" + name + "\"", "")
                .replace("\"" + name + "\",", "")
                .replace(",\"" + name + "\"", "")
                .replace("\"" + name + "\"", "");
        if (enabled) {
            String entry = "\"file/" + name + "\"";
            if (text.contains("resourcePacks:[")) {
                text = text.replaceFirst("resourcePacks:\\[", "resourcePacks:[" + entry + ",");
                text = text.replace("resourcePacks:[" + entry + ",]", "resourcePacks:[" + entry + "]");
            } else {
                text += (text.endsWith("\n") || text.isEmpty() ? "" : "\n") + "resourcePacks:[" + entry + "]\n";
            }
        }
        Tools.write(options.getAbsolutePath(), text);
    }

    private void confirmDelete(ContentInfo info) {
        AlertDialog dialog = Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.library_content_delete)
                .setMessage(info.file.getName())
                .setPositiveButton(android.R.string.ok, (d, w) -> deleteContent(info.file))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Tools.styleDialog(dialog);
        dialog.show();
    }

    private void deleteContent(File file) {
        PojavApplication.sExecutorService.execute(() -> {
            boolean ok = deleteRecursive(file);
            Tools.runOnUiThread(() -> {
                Toast.makeText(requireContext(), ok ? R.string.library_content_deleted : R.string.global_error, Toast.LENGTH_SHORT).show();
                bindInstalledContent();
            });
        });
    }

    private boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) return false;
                }
            }
        }
        return file.delete();
    }

    private void showMoveContentDialog(ContentInfo info) {
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        List<MinecraftProfile> profiles = new ArrayList<>(LauncherProfiles.mainProfileJson.profiles.values());
        List<String> labels = new ArrayList<>();
        for (MinecraftProfile profile : profiles) {
            labels.add(profile.name);
        }
        AlertDialog dialog = Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.library_content_move)
                .setItems(labels.toArray(new String[0]), (d, which) -> moveContent(info, profiles.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Tools.styleDialog(dialog);
        dialog.show();
    }

    private void moveContent(ContentInfo info, MinecraftProfile targetProfile) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File targetDir = getSectionDirectory(targetProfile, info.sectionTitleRes);
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    throw new IllegalStateException("No se pudo crear la carpeta destino");
                }
                File target = new File(targetDir, info.file.getName());
                copyRecursive(info.file, target);
                deleteRecursive(info.file);
                Tools.runOnUiThread(() -> {
                    Toast.makeText(requireContext(), R.string.library_content_moved, Toast.LENGTH_SHORT).show();
                    bindInstalledContent();
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private File getSectionDirectory(MinecraftProfile profile, int sectionTitleRes) {
        File gameDir = Tools.getGameDirPath(profile);
        if (sectionTitleRes == R.string.library_installed_mods) return new File(gameDir, "mods");
        if (sectionTitleRes == R.string.library_installed_resourcepacks) return new File(gameDir, "resourcepacks");
        if (sectionTitleRes == R.string.library_installed_shaders) return new File(gameDir, "shaderpacks");
        if (sectionTitleRes == R.string.world_manager_title) return new File(gameDir, "saves");
        return new File(gameDir, "datapacks");
    }

    private void copyRecursive(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) throw new IllegalStateException("No se pudo copiar la carpeta");
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) copyRecursive(child, new File(target, child.getName()));
            }
            return;
        }
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private String cleanFileName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private long getSize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        File[] children = file.listFiles();
        if (children == null) return 0L;
        long size = 0L;
        for (File child : children) {
            size += getSize(child);
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024d;
        if (kb < 1024d) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024d;
        if (mb < 1024d) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024d);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class ContentInfo {
        String name;
        String version;
        String loader;
        String minecraft;
        String description;
        String size;
        String badge;
        String iconPath;
        File file;
        int sectionTitleRes;
        boolean enabled;
        boolean supportsToggle;

        String metaLine() {
            StringBuilder builder = new StringBuilder();
            if (Tools.isValidString(version)) builder.append(version);
            if (Tools.isValidString(loader)) append(builder, loader);
            if (Tools.isValidString(minecraft)) append(builder, "MC " + minecraft);
            append(builder, size);
            if (supportsToggle) append(builder, enabled ? "Activo" : "Desactivado");
            return builder.toString();
        }

        private static void append(StringBuilder builder, String value) {
            if (!Tools.isValidString(value)) return;
            if (builder.length() > 0) builder.append(" · ");
            builder.append(value);
        }
    }

    private static class InstalledSection {
        final int titleRes;
        final File directory;
        final int fileCount;
        final int folderCount;
        final long totalBytes;
        final List<ContentInfo> entries;

        InstalledSection(int titleRes, File directory, int fileCount, int folderCount, long totalBytes, List<ContentInfo> entries) {
            this.titleRes = titleRes;
            this.directory = directory;
            this.fileCount = fileCount;
            this.folderCount = folderCount;
            this.totalBytes = totalBytes;
            this.entries = entries;
        }
    }
}
