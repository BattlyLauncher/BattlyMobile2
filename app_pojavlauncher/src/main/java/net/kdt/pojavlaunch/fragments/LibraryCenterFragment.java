package net.kdt.pojavlaunch.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    private LinearLayout mInstalledContentContainer;

    public LibraryCenterFragment() {
        super(R.layout.fragment_library_center);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mInstalledContentContainer = view.findViewById(R.id.library_installed_content_container);
        view.findViewById(R.id.download_panel_import_modpack)
                .setOnClickListener(v -> ((LauncherActivity) requireActivity()).modpackImportLauncher.launch(null));
        view.findViewById(R.id.download_panel_browse_modpacks).setOnClickListener(v -> openSearch(SearchFilters.TYPE_MODPACK));
        view.findViewById(R.id.download_panel_browse_mods).setOnClickListener(v -> openSearch(SearchFilters.TYPE_MOD));
        view.findViewById(R.id.download_panel_browse_resourcepacks).setOnClickListener(v -> openSearch(SearchFilters.TYPE_RESOURCEPACK));
        view.findViewById(R.id.download_panel_browse_shaders).setOnClickListener(v -> openSearch(SearchFilters.TYPE_SHADER));
        view.findViewById(R.id.download_panel_browse_datapacks).setOnClickListener(v -> openSearch(SearchFilters.TYPE_DATAPACK));
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
        File gameDir = Tools.getGameDirPath(LauncherProfiles.getCurrentProfile());
        addInstalledSection(R.string.library_installed_mods, new File(gameDir, "mods"));
        addInstalledSection(R.string.library_installed_resourcepacks, new File(gameDir, "resourcepacks"));
        addInstalledSection(R.string.library_installed_shaders, new File(gameDir, "shaderpacks"));
        addInstalledSection(R.string.library_installed_datapacks, new File(gameDir, "datapacks"));
    }

    private void addInstalledSection(int titleRes, File directory) {
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
        title.setText(titleRes);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView count = new TextView(requireContext());
        count.setText(getResources().getQuantityString(R.plurals.library_installed_count, files.length, files.length));
        count.setTextColor(0xFFC7D4DF);
        count.setTextSize(12);
        header.addView(count);
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(v -> Tools.openPath(requireContext(), directory, false));
        card.addView(header);

        TextView detail = new TextView(requireContext());
        detail.setText(buildInstalledDetail(files));
        detail.setTextColor(0xFF93AEBB);
        detail.setTextSize(12);
        detail.setPadding(0, dp(8), 0, 0);
        card.addView(detail);

        if (entries.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.library_installed_empty);
            empty.setTextColor(0xFFC7D4DF);
            empty.setTextSize(12);
            empty.setPadding(0, dp(10), 0, 0);
            card.addView(empty);
        } else {
            int visibleCount = Math.min(entries.size(), 5);
            for (int i = 0; i < visibleCount; i++) {
                card.addView(createContentRow(entries.get(i)));
            }
            if (entries.size() > visibleCount) {
                TextView more = new TextView(requireContext());
                more.setText(getString(R.string.library_installed_more, entries.size() - visibleCount));
                more.setTextColor(0xFF8DEEDC);
                more.setTypeface(Typeface.DEFAULT_BOLD);
                more.setTextSize(12);
                more.setPadding(0, dp(8), 0, 0);
                card.addView(more);
            }
        }

        mInstalledContentContainer.addView(card);
    }

    private String buildInstalledDetail(File[] files) {
        if (files.length == 0) {
            return "";
        }
        long totalBytes = 0L;
        int folders = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                folders++;
            }
            totalBytes += getSize(file);
        }
        String size = formatSize(totalBytes);
        if (folders > 0) {
            return getString(R.string.library_installed_detail_with_folders, size, folders);
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
        row.addView(top);

        TextView meta = new TextView(requireContext());
        meta.setText(info.metaLine());
        meta.setTextColor(0xFF9FB8C5);
        meta.setTextSize(11);
        meta.setPadding(0, dp(2), 0, 0);
        row.addView(meta);

        if (Tools.isValidString(info.description)) {
            TextView description = new TextView(requireContext());
            description.setText(info.description);
            description.setTextColor(0xFFC7D4DF);
            description.setTextSize(12);
            description.setMaxLines(2);
            description.setPadding(0, dp(6), 0, 0);
            row.addView(description);
        }
        return row;
    }

    private ContentInfo readContentInfo(int titleRes, File file) {
        ContentInfo info = new ContentInfo();
        info.name = cleanFileName(file.getName());
        info.version = "";
        info.description = "";
        info.size = formatSize(getSize(file));
        info.enabled = !file.getName().toLowerCase(Locale.ROOT).endsWith(".disabledmod");
        info.badge = titleRes == R.string.library_installed_mods ? "MOD"
                : titleRes == R.string.library_installed_resourcepacks ? "PACK"
                : titleRes == R.string.library_installed_shaders ? "SHADER" : "DATA";

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
        boolean enabled;

        String metaLine() {
            StringBuilder builder = new StringBuilder();
            if (Tools.isValidString(version)) builder.append(version);
            if (Tools.isValidString(loader)) append(builder, loader);
            if (Tools.isValidString(minecraft)) append(builder, "MC " + minecraft);
            append(builder, size);
            append(builder, enabled ? "Activo" : "Desactivado");
            return builder.toString();
        }

        private static void append(StringBuilder builder, String value) {
            if (!Tools.isValidString(value)) return;
            if (builder.length() > 0) builder.append(" · ");
            builder.append(value);
        }
    }
}
