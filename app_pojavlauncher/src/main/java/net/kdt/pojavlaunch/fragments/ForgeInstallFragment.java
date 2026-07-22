package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ExpandableListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.ForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.ForgeUtils;
import net.kdt.pojavlaunch.modloaders.ForgeVersionListAdapter;
import net.kdt.pojavlaunch.modloaders.OptiFineDownloadTask;
import net.kdt.pojavlaunch.modloaders.OptiFineUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForgeInstallFragment extends ModVersionListFragment<List<String>> {
    public static final String TAG = "ForgeInstallFragment";
    private static final Pattern MC_VERSION_PATTERN = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private CheckBox mForgeOptiFineCheckBox;

    public ForgeInstallFragment() {
        super(TAG);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View extraOptions = view.findViewById(R.id.mod_dl_extra_options);
        mForgeOptiFineCheckBox = view.findViewById(R.id.mod_dl_forge_optifine);
        if (extraOptions != null && mForgeOptiFineCheckBox != null) {
            extraOptions.setVisibility(View.VISIBLE);
            mForgeOptiFineCheckBox.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getTitleText() {
        return R.string.forge_dl_select_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.forge_dl_no_installer;
    }

    @Override
    public List<String> loadVersionList() throws IOException {
        return ForgeUtils.downloadForgeVersions();
    }

    @Override
    public ExpandableListAdapter createAdapter(List<String> versionList, LayoutInflater layoutInflater) {
        return new ForgeVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    protected List<String> filterVersionList(List<String> versionList, String query) {
        if (query == null || query.trim().isEmpty()) {
            return versionList;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> filtered = new ArrayList<>();
        for (String version : versionList) {
            if (version != null && version.toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(version);
            }
        }
        return filtered;
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderDownloadListener listener) {
        boolean installOptiFine = mForgeOptiFineCheckBox != null && mForgeOptiFineCheckBox.isChecked();
        if (!installOptiFine) {
            return new ForgeDownloadTask(requireContext(), listener, (String) selectedVersion);
        }
        return new ForgeOptiFineDownloadTask(requireContext(), requireActivity(), listener, (String) selectedVersion);
    }

    @Override
    protected String extractVanillaVersion(Object selectedVersion) {
        // Forge version format: "1.21.8-47.3.0"; MC version is the part before the dash.
        String version = (String) selectedVersion;
        int dashIndex = version.indexOf('-');
        return dashIndex > 0 ? version.substring(0, dashIndex) : null;
    }

    @Override
    protected String getSuccessMessageLabel(Object selectedVersion) {
        return mForgeOptiFineCheckBox != null && mForgeOptiFineCheckBox.isChecked()
                ? "Forge + OptiFine"
                : "Forge";
    }

    private static class ForgeOptiFineDownloadTask implements Runnable {
        private final Context context;
        private final android.app.Activity activity;
        private final ModloaderDownloadListener listener;
        private final String forgeVersion;

        ForgeOptiFineDownloadTask(Context context, android.app.Activity activity,
                                  ModloaderDownloadListener listener, String forgeVersion) {
            this.context = context.getApplicationContext();
            this.activity = activity;
            this.listener = listener;
            this.forgeVersion = forgeVersion;
        }

        @Override
        public void run() {
            CapturingListener forgeListener = new CapturingListener();
            new ForgeDownloadTask(context, forgeListener, forgeVersion).run();
            if (forgeListener.failed) {
                forgeListener.forward(listener);
                return;
            }

            OptiFineUtils.OptiFineVersion optiFineVersion;
            try {
                optiFineVersion = findMatchingOptiFineVersion();
            } catch (IOException e) {
                listener.onDownloadError(e);
                return;
            }
            if (optiFineVersion == null) {
                listener.onDataNotAvailable();
                return;
            }

            new OptiFineDownloadTask(optiFineVersion, listener, activity, true).run();
        }

        @Nullable
        private OptiFineUtils.OptiFineVersion findMatchingOptiFineVersion() throws IOException {
            String minecraftVersion = extractMinecraftVersion(forgeVersion);
            if (minecraftVersion == null) {
                return null;
            }
            OptiFineUtils.OptiFineVersions versions = OptiFineUtils.downloadOptiFineVersions();
            if (versions == null || versions.minecraftVersions == null || versions.optifineVersions == null) {
                return null;
            }
            for (int i = 0; i < versions.minecraftVersions.size() && i < versions.optifineVersions.size(); i++) {
                String optiFineMinecraftVersion = normalizeMinecraftVersion(versions.minecraftVersions.get(i));
                if (!minecraftVersion.equals(optiFineMinecraftVersion)) {
                    continue;
                }
                List<OptiFineUtils.OptiFineVersion> candidates = versions.optifineVersions.get(i);
                return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
            }
            return null;
        }

        @Nullable
        private static String extractMinecraftVersion(String forgeVersion) {
            if (forgeVersion == null) {
                return null;
            }
            int dashIndex = forgeVersion.indexOf('-');
            return dashIndex > 0 ? forgeVersion.substring(0, dashIndex) : null;
        }

        @Nullable
        private static String normalizeMinecraftVersion(String value) {
            if (value == null) {
                return null;
            }
            Matcher matcher = MC_VERSION_PATTERN.matcher(value);
            if (!matcher.find()) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            builder.append(matcher.group(1)).append('.').append(matcher.group(2));
            String patch = matcher.group(3);
            if (patch != null && !patch.isEmpty() && !"0".equals(patch)) {
                builder.append('.').append(patch);
            }
            return builder.toString();
        }
    }

    private static class CapturingListener implements ModloaderDownloadListener {
        private boolean failed;
        private boolean noData;
        private Exception error;

        @Override
        public void onDownloadFinished(File downloadedFile) {
        }

        @Override
        public void onDataNotAvailable() {
            failed = true;
            noData = true;
        }

        @Override
        public void onDownloadError(Exception e) {
            failed = true;
            error = e;
        }

        void forward(ModloaderDownloadListener listener) {
            if (error != null) {
                listener.onDownloadError(error);
            } else if (noData) {
                listener.onDataNotAvailable();
            }
        }
    }
}
