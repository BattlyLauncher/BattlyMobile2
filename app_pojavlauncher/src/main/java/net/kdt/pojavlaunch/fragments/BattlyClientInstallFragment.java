package net.kdt.pojavlaunch.fragments;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipFile;

public class BattlyClientInstallFragment extends Fragment {
    public static final String TAG = "BattlyClientInstallFragment";
    private static final String VERSIONS_URL = "https://api.battlylauncher.com/v3/battlylauncher/launcher/config-launcher/versions.json";

    private LinearLayout mList;
    private ProgressBar mProgress;
    private ProgressBar mInstallProgress;
    private TextView mInstallProgressText;
    private EditText mSearch;
    private final ArrayList<ClientEntry> mClients = new ArrayList<>();

    public BattlyClientInstallFragment() {
        super(R.layout.fragment_mod_version_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mProgress = view.findViewById(R.id.mod_dl_list_progress);
        mSearch = view.findViewById(R.id.mod_dl_version_search);
        LinearLayout panel = (LinearLayout) mSearch.getParent();
        TextView subtitle = (TextView) panel.getChildAt(0);
        subtitle.setText(R.string.download_clients_subtitle);
        mInstallProgressText = new TextView(requireContext());
        mInstallProgressText.setTextColor(0xFFC7D4DF);
        mInstallProgressText.setTextSize(12);
        mInstallProgressText.setVisibility(View.GONE);
        LinearLayout.LayoutParams installTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        installTextParams.setMargins(0, dp(12), 0, 0);
        panel.addView(mInstallProgressText, installTextParams);

        mInstallProgress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        mInstallProgress.setMax(1000);
        mInstallProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams installProgressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8));
        installProgressParams.setMargins(0, dp(8), 0, 0);
        panel.addView(mInstallProgress, installProgressParams);
        ViewGroup listParent = view.findViewById(R.id.mod_dl_version_grid);
        listParent.setVisibility(View.GONE);
        mList = new LinearLayout(requireContext());
        mList.setOrientation(LinearLayout.VERTICAL);
        ((ViewGroup) listParent.getParent()).addView(mList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        ((TextView) ((ViewGroup) view.findViewById(R.id.mod_dl_retry_layout)).getChildAt(0))
                .setText(R.string.download_version_clients);
        view.findViewById(R.id.forge_installer_retry_button).setOnClickListener(v -> loadClients());
        mSearch.setHint(R.string.download_version_search_hint);
        mSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                bindList(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        loadClients();
    }

    private void loadClients() {
        mProgress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JSONObject root = new JSONObject(DownloadUtils.downloadString(VERSIONS_URL));
                JSONArray versions = root.optJSONArray("versions");
                ArrayList<ClientEntry> entries = new ArrayList<>();
                if (versions != null) {
                    for (int i = 0; i < versions.length(); i++) {
                        JSONObject item = versions.optJSONObject(i);
                        if (item == null || !"client".equalsIgnoreCase(item.optString("type"))) {
                            continue;
                        }
                        ClientEntry entry = new ClientEntry();
                        entry.name = first(item, "name", "displayName", "version");
                        entry.version = item.optString("version", entry.name);
                        entry.folderName = first(item, "folderName", "folder", "version");
                        entry.downloadUrl = first(item, "downloadUrl", "downlaodUrl", "url");
                        entry.description = first(item, "description", "desc", "realVersion");
                        entries.add(entry);
                    }
                }
                Tools.runOnUiThread(() -> {
                    mClients.clear();
                    mClients.addAll(entries);
                    mProgress.setVisibility(View.GONE);
                    bindList(mSearch.getText().toString());
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    mProgress.setVisibility(View.GONE);
                    viewRetry(true);
                    Tools.showError(requireContext(), e);
                });
            }
        }, "Battly Clients Load").start();
    }

    private void bindList(String query) {
        if (mList == null) return;
        viewRetry(false);
        mList.removeAllViews();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (ClientEntry client : mClients) {
            if (!normalized.isEmpty()
                    && !client.name.toLowerCase(Locale.ROOT).contains(normalized)
                    && !client.version.toLowerCase(Locale.ROOT).contains(normalized)) {
                continue;
            }
            mList.addView(createClientRow(client));
        }
        if (mList.getChildCount() == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.download_version_empty_search);
            empty.setTextColor(0xFFC7D4DF);
            empty.setPadding(dp(14), dp(20), dp(14), dp(20));
            mList.addView(empty);
        }
    }

    private View createClientRow(ClientEntry client) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackgroundResource(R.drawable.bg_battly_version_option);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> installClient(client));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);

        TextView title = new TextView(requireContext());
        title.setText(client.name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title);

        TextView meta = new TextView(requireContext());
        String status = Tools.isValidString(client.downloadUrl)
                ? getString(R.string.download_client_ready, client.version)
                : getString(R.string.download_client_missing_url, client.version);
        if (Tools.isValidString(client.description)) {
            status += " · " + client.description;
        }
        meta.setText(status);
        meta.setTextColor(0xFF9FB8C5);
        meta.setTextSize(11);
        row.addView(meta);
        return row;
    }

    private void installClient(ClientEntry client) {
        if (!Tools.isValidString(client.downloadUrl)) {
            Toast.makeText(requireContext(), R.string.download_client_no_download, Toast.LENGTH_LONG).show();
            return;
        }
        mProgress.setVisibility(View.VISIBLE);
        setInstallProgress(0, 0, getString(R.string.download_client_preparing, client.name));
        new Thread(() -> {
            try {
                File zip = new File(Tools.DIR_CACHE, "battly-clients/" + client.folderName + ".zip");
                DownloadUtils.downloadFileMonitored(client.downloadUrl, zip, null, (curr, max) ->
                        Tools.runOnUiThread(() -> setInstallProgress(curr, max,
                                getString(R.string.download_client_downloading, client.name))));
                Tools.runOnUiThread(() -> setInstallProgress(1, 1,
                        getString(R.string.download_client_installing, client.name)));
                try (ZipFile zipFile = new ZipFile(zip)) {
                    ZipUtils.zipExtract(zipFile, "", new File(Tools.DIR_GAME_NEW));
                }
                File versionJson = new File(Tools.DIR_HOME_VERSION, client.folderName + "/" + client.folderName + ".json");
                if (!versionJson.isFile()) {
                    throw new IllegalStateException("El cliente no incluye " + client.folderName + ".json");
                }
                LauncherProfiles.load();
                MinecraftProfile profile = MinecraftProfile.createTemplate();
                profile.name = client.name;
                profile.lastVersionId = client.folderName;
                profile.icon = "furnace";
                String key = LauncherProfiles.getFreeProfileKey();
                LauncherProfiles.mainProfileJson.profiles.put(key, profile);
                LauncherProfiles.write();
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key)
                        .apply();
                Tools.runOnUiThread(() -> {
                    mProgress.setVisibility(View.GONE);
                    hideInstallProgress();
                    Toast.makeText(requireContext(),
                            getString(R.string.client_install_success, client.name),
                            Toast.LENGTH_LONG).show();
                    Tools.backToMainMenu(requireActivity());
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    mProgress.setVisibility(View.GONE);
                    hideInstallProgress();
                    Tools.showError(requireContext(), e);
                });
            }
        }, "Battly Client Install").start();
    }

    private void viewRetry(boolean visible) {
        View retry = getView() == null ? null : getView().findViewById(R.id.mod_dl_retry_layout);
        if (retry != null) retry.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setInstallProgress(int curr, int max, String message) {
        if (mInstallProgress == null || mInstallProgressText == null) {
            return;
        }
        mInstallProgress.setVisibility(View.VISIBLE);
        mInstallProgressText.setVisibility(View.VISIBLE);
        if (max > 0) {
            int progress = Math.max(0, Math.min(1000, Math.round(curr * 1000f / max)));
            mInstallProgress.setIndeterminate(false);
            mInstallProgress.setProgress(progress);
            double currentMb = curr / 1024d / 1024d;
            double maxMb = max / 1024d / 1024d;
            mInstallProgressText.setText(getString(R.string.download_client_progress, message, currentMb, maxMb));
        } else {
            mInstallProgress.setIndeterminate(true);
            mInstallProgressText.setText(message);
        }
    }

    private void hideInstallProgress() {
        if (mInstallProgress != null) {
            mInstallProgress.setVisibility(View.GONE);
        }
        if (mInstallProgressText != null) {
            mInstallProgressText.setVisibility(View.GONE);
        }
    }

    private static String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (Tools.isValidString(value)) return value;
        }
        return "";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class ClientEntry {
        String name;
        String version;
        String folderName;
        String downloadUrl;
        String description;
    }
}
