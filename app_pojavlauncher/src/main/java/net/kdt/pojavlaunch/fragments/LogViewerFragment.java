package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.MclogsUploader;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class LogViewerFragment extends Fragment {
    public static final String TAG = "LogViewerFragment";

    private final ArrayList<File> mLogFiles = new ArrayList<>();
    private ArrayAdapter<String> mAdapter;
    private Spinner mLogSelector;
    private TextView mLogContentView;
    private TextView mLogMetaView;
    private TextView mUploadButton;

    public LogViewerFragment() {
        super(R.layout.fragment_log_viewer);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mLogSelector = view.findViewById(R.id.log_viewer_selector);
        mLogContentView = view.findViewById(R.id.log_viewer_content);
        mLogMetaView = view.findViewById(R.id.log_viewer_meta);
        mUploadButton = view.findViewById(R.id.log_viewer_upload);

        mAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_simple_list_1, new ArrayList<>());
        mAdapter.setDropDownViewResource(R.layout.item_simple_list_1);
        mLogSelector.setAdapter(mAdapter);
        mLogSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View selectedView, int position, long id) {
                loadSelectedLog();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                mLogContentView.setText(R.string.log_viewer_empty);
                mLogMetaView.setText("");
            }
        });

        view.findViewById(R.id.log_viewer_refresh).setOnClickListener(v -> refreshLogs(true));
        view.findViewById(R.id.log_viewer_copy).setOnClickListener(v -> copySelectedLog());
        view.findViewById(R.id.log_viewer_share).setOnClickListener(v -> shareSelectedLog());
        mUploadButton.setOnClickListener(v -> uploadSelectedLog());

        refreshLogs(false);
    }

    private void refreshLogs(boolean preserveSelection) {
        File previousSelection = preserveSelection ? getSelectedLogFile() : null;
        mLogFiles.clear();
        mLogFiles.addAll(discoverLogFiles());
        mAdapter.clear();
        for (File logFile : mLogFiles) {
            mAdapter.add(buildDisplayName(logFile));
        }
        mAdapter.notifyDataSetChanged();

        if (mLogFiles.isEmpty()) {
            mLogContentView.setText(R.string.log_viewer_empty);
            mLogMetaView.setText("");
            return;
        }

        int selectedIndex = 0;
        if (previousSelection != null) {
            for (int i = 0; i < mLogFiles.size(); i++) {
                if (mLogFiles.get(i).equals(previousSelection)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        mLogSelector.setSelection(selectedIndex);
        loadSelectedLog();
    }

    private List<File> discoverLogFiles() {
        ArrayList<File> logFiles = new ArrayList<>();
        addIfReadable(logFiles, new File(Tools.DIR_GAME_HOME, "latestlog.txt"));
        addIfReadable(logFiles, new File(Tools.DIR_GAME_HOME, "latestcrash.txt"));
        addIfReadable(logFiles, new File(Tools.DIR_DATA, "latestcrash.txt"));

        File installerLogDir = new File(Tools.DIR_CACHE, "installer_logs");
        File[] installerLogs = installerLogDir.listFiles(file -> file.isFile() && file.getName().endsWith(".log"));
        if (installerLogs != null) {
            Arrays.sort(installerLogs, Comparator.comparingLong(File::lastModified).reversed());
            logFiles.addAll(Arrays.asList(installerLogs));
        }

        logFiles.sort(Comparator.comparingLong(File::lastModified).reversed());
        return logFiles;
    }

    private void loadSelectedLog() {
        File selectedLog = getSelectedLogFile();
        if (selectedLog == null) {
            mLogContentView.setText(R.string.log_viewer_empty);
            mLogMetaView.setText("");
            return;
        }

        mLogContentView.setText(R.string.log_viewer_loading);
        mLogMetaView.setText(buildMeta(selectedLog));
        PojavApplication.sExecutorService.execute(() -> {
            String content;
            try {
                content = Tools.read(selectedLog);
                if (!Tools.isValidString(content)) {
                    content = getString(R.string.log_viewer_empty_file);
                }
            } catch (IOException e) {
                content = getString(R.string.log_viewer_read_failed, e);
            }

            if (!isAdded()) return;
            String finalContent = content;
            requireActivity().runOnUiThread(() -> mLogContentView.setText(finalContent));
        });
    }

    private void copySelectedLog() {
        File selectedLog = getSelectedLogFile();
        if (selectedLog == null) {
            Toast.makeText(requireContext(), R.string.log_viewer_no_selection, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(selectedLog.getName(), Tools.read(selectedLog)));
                Toast.makeText(requireContext(), R.string.log_viewer_copied, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Tools.showError(requireContext(), getString(R.string.log_viewer_read_failed, e), e);
        }
    }

    private void shareSelectedLog() {
        File selectedLog = getSelectedLogFile();
        if (selectedLog == null) {
            Toast.makeText(requireContext(), R.string.log_viewer_no_selection, Toast.LENGTH_SHORT).show();
            return;
        }
        openPath(requireContext(), selectedLog, true);
    }

    private void uploadSelectedLog() {
        File selectedLog = getSelectedLogFile();
        if (selectedLog == null) {
            Toast.makeText(requireContext(), R.string.log_viewer_no_selection, Toast.LENGTH_SHORT).show();
            return;
        }
        mUploadButton.setEnabled(false);
        mUploadButton.setAlpha(0.6f);
        mUploadButton.setText(R.string.log_viewer_uploading);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                MclogsUploader.UploadResult result = MclogsUploader.upload(selectedLog);
                android.app.Activity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (isAdded()) showUploadResult(result);
                });
            } catch (Exception exception) {
                android.app.Activity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    resetUploadButton();
                    Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                            .setTitle(R.string.log_viewer_upload_failed_title)
                            .setMessage(getString(R.string.log_viewer_upload_failed, exception.getMessage()))
                            .setPositiveButton(android.R.string.ok, null));
                });
            }
        });
    }

    private void showUploadResult(MclogsUploader.UploadResult result) {
        resetUploadButton();
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.log_viewer_upload_complete)
                .setMessage(getString(R.string.log_viewer_upload_result, result.url, result.lines, result.errors))
                .setPositiveButton(R.string.log_viewer_share_link, (dialog, which) -> {
                    Intent share = new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, result.url);
                    startActivity(Intent.createChooser(share, getString(R.string.log_viewer_share_link)));
                })
                .setNeutralButton(R.string.log_viewer_copy_link, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) requireContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("mclo.gs", result.url));
                        Toast.makeText(requireContext(), R.string.log_viewer_link_copied,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.log_viewer_open_link, (dialog, which) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))));
    }

    private void resetUploadButton() {
        mUploadButton.setEnabled(true);
        mUploadButton.setAlpha(1f);
        mUploadButton.setText(R.string.log_viewer_upload);
    }

    private File getSelectedLogFile() {
        int position = mLogSelector.getSelectedItemPosition();
        if (position < 0 || position >= mLogFiles.size()) {
            return null;
        }
        return mLogFiles.get(position);
    }

    private String buildDisplayName(File logFile) {
        String prefix;
        if ("latestlog.txt".equals(logFile.getName())) {
            prefix = getString(R.string.log_viewer_latest_log);
        } else if ("latestcrash.txt".equals(logFile.getName())) {
            prefix = getString(R.string.log_viewer_latest_crash);
        } else if (logFile.getParentFile() != null && "installer_logs".equals(logFile.getParentFile().getName())) {
            prefix = getString(R.string.log_viewer_installer_log);
        } else {
            prefix = getString(R.string.log_viewer_log_file);
        }
        return prefix + " - " + logFile.getName();
    }

    private String buildMeta(File logFile) {
        String modifiedAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                .format(new Date(logFile.lastModified()));
        long sizeKb = Math.max(1L, (logFile.length() + 1023L) / 1024L);
        return getString(R.string.log_viewer_meta, logFile.getAbsolutePath(), sizeKb, modifiedAt);
    }

    private static void addIfReadable(List<File> files, File file) {
        if (file.isFile() && file.canRead()) {
            files.add(file);
        }
    }
}
