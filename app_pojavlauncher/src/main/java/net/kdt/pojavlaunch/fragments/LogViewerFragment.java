package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.DefocusableScrollView;

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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class LogViewerFragment extends Fragment {
    public static final String TAG = "LogViewerFragment";

    private static final int MAX_VISIBLE_LINES = 5000;
    private static final long LIVE_REFRESH_INTERVAL_MS = 1500L;
    private static final int COLOR_ERROR = Color.rgb(255, 142, 154);
    private static final int COLOR_WARNING = Color.rgb(255, 200, 87);
    private static final int COLOR_INFO = Color.rgb(220, 230, 235);
    private static final int COLOR_QUERY = Color.argb(105, 62, 142, 208);

    private enum LogFilter {
        ALL,
        ERRORS,
        WARNINGS
    }

    private final ArrayList<File> mLogFiles = new ArrayList<>();
    private final AtomicInteger mRenderGeneration = new AtomicInteger();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private ArrayAdapter<String> mAdapter;
    private Spinner mLogSelector;
    private TextView mLogContentView;
    private TextView mLogMetaView;
    private TextView mUploadButton;
    private TextView mLiveButton;
    private TextView mLinesStat;
    private TextView mWarningsStat;
    private TextView mErrorsStat;
    private TextView mAllFilterButton;
    private TextView mErrorsFilterButton;
    private TextView mWarningsFilterButton;
    private DefocusableScrollView mScrollView;

    private String mRawContent = "";
    private LogFilter mFilter = LogFilter.ALL;
    private boolean mLiveEnabled;
    private long mObservedModified;
    private long mObservedLength;

    private final Runnable mLiveRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mLiveEnabled || !isAdded()) return;
            File selectedLog = getSelectedLogFile();
            if (selectedLog != null
                    && (selectedLog.lastModified() != mObservedModified
                    || selectedLog.length() != mObservedLength)) {
                loadSelectedLog(true);
            }
            mHandler.postDelayed(this, LIVE_REFRESH_INTERVAL_MS);
        }
    };

    public LogViewerFragment() {
        super(R.layout.fragment_log_viewer);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mLogSelector = view.findViewById(R.id.log_viewer_selector);
        mLogContentView = view.findViewById(R.id.log_viewer_content);
        mLogMetaView = view.findViewById(R.id.log_viewer_meta);
        mUploadButton = view.findViewById(R.id.log_viewer_upload);
        mLiveButton = view.findViewById(R.id.log_viewer_live);
        mLinesStat = view.findViewById(R.id.log_viewer_stat_lines);
        mWarningsStat = view.findViewById(R.id.log_viewer_stat_warnings);
        mErrorsStat = view.findViewById(R.id.log_viewer_stat_errors);
        mAllFilterButton = view.findViewById(R.id.log_filter_all);
        mErrorsFilterButton = view.findViewById(R.id.log_filter_errors);
        mWarningsFilterButton = view.findViewById(R.id.log_filter_warnings);
        mScrollView = view.findViewById(R.id.log_viewer_scroll);
        mScrollView.setKeepFocusing(false);

        mAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_simple_list_1, new ArrayList<>());
        mAdapter.setDropDownViewResource(R.layout.item_simple_list_1);
        mLogSelector.setAdapter(mAdapter);
        mLogSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View selectedView,
                                       int position, long id) {
                loadSelectedLog(false);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                showEmptyState();
            }
        });

        EditText searchView = view.findViewById(R.id.log_viewer_search);
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                renderCurrentLog(text == null ? "" : text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            net.kdt.pojavlaunch.Tools.hideKeyboard(v);
            return true;
        });

        view.findViewById(R.id.log_viewer_refresh).setOnClickListener(v -> refreshLogs(true));
        view.findViewById(R.id.log_viewer_copy).setOnClickListener(v -> copySelectedLog());
        view.findViewById(R.id.log_viewer_share).setOnClickListener(v -> shareSelectedLog());
        mUploadButton.setOnClickListener(v -> uploadSelectedLog());
        mLiveButton.setOnClickListener(v -> setLiveEnabled(!mLiveEnabled));
        mAllFilterButton.setOnClickListener(v -> setFilter(LogFilter.ALL));
        mErrorsFilterButton.setOnClickListener(v -> setFilter(LogFilter.ERRORS));
        mWarningsFilterButton.setOnClickListener(v -> setFilter(LogFilter.WARNINGS));

        updateFilterButtons();
        updateStats(0, 0, 0);
        refreshLogs(false);
    }

    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mLiveRefreshRunnable);
        mRenderGeneration.incrementAndGet();
        super.onDestroyView();
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
            showEmptyState();
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
        loadSelectedLog(false);
    }

    private List<File> discoverLogFiles() {
        ArrayList<File> logFiles = new ArrayList<>();
        addIfReadable(logFiles, new File(Tools.DIR_GAME_HOME, "latestlog.txt"));
        addIfReadable(logFiles, new File(Tools.DIR_GAME_HOME, "latestcrash.txt"));
        addIfReadable(logFiles, new File(Tools.DIR_DATA, "latestcrash.txt"));

        File installerLogDir = new File(Tools.DIR_CACHE, "installer_logs");
        File[] installerLogs = installerLogDir.listFiles(
                file -> file.isFile() && file.getName().endsWith(".log"));
        if (installerLogs != null) {
            Arrays.sort(installerLogs, Comparator.comparingLong(File::lastModified).reversed());
            logFiles.addAll(Arrays.asList(installerLogs));
        }

        logFiles.sort(Comparator.comparingLong(File::lastModified).reversed());
        return logFiles;
    }

    private void loadSelectedLog(boolean followLatest) {
        File selectedLog = getSelectedLogFile();
        if (selectedLog == null) {
            showEmptyState();
            return;
        }

        final int generation = mRenderGeneration.incrementAndGet();
        mLogContentView.setText(R.string.log_viewer_loading);
        mLogMetaView.setText(buildMeta(selectedLog));
        mObservedModified = selectedLog.lastModified();
        mObservedLength = selectedLog.length();

        PojavApplication.sExecutorService.execute(() -> {
            String content;
            try {
                content = Tools.read(selectedLog);
            } catch (IOException exception) {
                content = getStringSafely(R.string.log_viewer_read_failed, exception);
            }

            Activity activity = getActivity();
            if (activity == null || generation != mRenderGeneration.get()) return;
            String finalContent = content;
            activity.runOnUiThread(() -> {
                if (!isAdded() || generation != mRenderGeneration.get()) return;
                mRawContent = finalContent == null ? "" : finalContent;
                renderCurrentLog(getCurrentSearch());
                if (followLatest && mLiveEnabled) {
                    mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
                }
            });
        });
    }

    private void renderCurrentLog(String query) {
        if (mLogContentView == null) return;
        final int generation = mRenderGeneration.incrementAndGet();
        final String content = mRawContent;
        final String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        final LogFilter selectedFilter = mFilter;

        PojavApplication.sExecutorService.execute(() -> {
            RenderResult result = buildRenderResult(content, normalizedQuery, selectedFilter);
            Activity activity = getActivity();
            if (activity == null || generation != mRenderGeneration.get()) return;
            activity.runOnUiThread(() -> {
                if (!isAdded() || generation != mRenderGeneration.get()) return;
                mLogContentView.setText(result.text);
                updateStats(result.totalLines, result.warnings, result.errors);
            });
        });
    }

    private RenderResult buildRenderResult(String content, String query, LogFilter filter) {
        if (!Tools.isValidString(content)) {
            return new RenderResult(getStringSafely(R.string.log_viewer_empty_file), 0, 0, 0);
        }

        String[] lines = content.split("\\r?\\n", -1);
        int errors = 0;
        int warnings = 0;
        ArrayList<RenderedLine> visibleLines = new ArrayList<>();

        for (String line : lines) {
            int severity = getSeverity(line);
            if (severity == 2) errors++;
            if (severity == 1) warnings++;

            boolean matchesFilter = filter == LogFilter.ALL
                    || (filter == LogFilter.ERRORS && severity == 2)
                    || (filter == LogFilter.WARNINGS && severity == 1);
            boolean matchesSearch = query.isEmpty()
                    || line.toLowerCase(Locale.ROOT).contains(query);
            if (matchesFilter && matchesSearch) {
                visibleLines.add(new RenderedLine(line, severity));
            }
        }

        int omitted = Math.max(0, visibleLines.size() - MAX_VISIBLE_LINES);
        int firstVisible = Math.max(0, visibleLines.size() - MAX_VISIBLE_LINES);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (omitted > 0) {
            builder.append(getStringSafely(R.string.log_viewer_truncated, omitted)).append("\n\n");
        }

        for (int index = firstVisible; index < visibleLines.size(); index++) {
            RenderedLine renderedLine = visibleLines.get(index);
            int lineStart = builder.length();
            builder.append(renderedLine.text);
            int lineEnd = builder.length();
            builder.setSpan(new ForegroundColorSpan(colorForSeverity(renderedLine.severity)),
                    lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!query.isEmpty()) {
                applyQueryHighlights(builder, renderedLine.text, query, lineStart);
            }
            if (index < visibleLines.size() - 1) builder.append('\n');
        }

        if (builder.length() == 0) {
            builder.append(getStringSafely(R.string.log_viewer_no_matches));
        }
        return new RenderResult(builder, lines.length, warnings, errors);
    }

    private void applyQueryHighlights(SpannableStringBuilder builder, String line, String query,
                                      int offset) {
        String normalizedLine = line.toLowerCase(Locale.ROOT);
        int match = normalizedLine.indexOf(query);
        while (match >= 0) {
            builder.setSpan(new BackgroundColorSpan(COLOR_QUERY),
                    offset + match, offset + match + query.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            match = normalizedLine.indexOf(query, match + query.length());
        }
    }

    private int getSeverity(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        if (normalized.contains("error")
                || normalized.contains("exception")
                || normalized.contains("fatal")
                || normalized.contains("crash")
                || normalized.contains("failed")
                || normalized.contains("caused by")) {
            return 2;
        }
        if (normalized.contains("warn")) return 1;
        return 0;
    }

    private int colorForSeverity(int severity) {
        if (severity == 2) return COLOR_ERROR;
        if (severity == 1) return COLOR_WARNING;
        return COLOR_INFO;
    }

    private void setFilter(LogFilter filter) {
        if (mFilter == filter) return;
        mFilter = filter;
        updateFilterButtons();
        renderCurrentLog(getCurrentSearch());
    }

    private void updateFilterButtons() {
        if (mAllFilterButton == null) return;
        styleFilterButton(mAllFilterButton, mFilter == LogFilter.ALL);
        styleFilterButton(mErrorsFilterButton, mFilter == LogFilter.ERRORS);
        styleFilterButton(mWarningsFilterButton, mFilter == LogFilter.WARNINGS);
    }

    private void styleFilterButton(TextView button, boolean selected) {
        button.setBackgroundResource(selected
                ? R.drawable.bg_battly_button_primary
                : R.drawable.bg_battly_button_secondary);
        button.setAlpha(selected ? 1f : 0.82f);
    }

    private void setLiveEnabled(boolean enabled) {
        mLiveEnabled = enabled;
        mHandler.removeCallbacks(mLiveRefreshRunnable);
        mLiveButton.setText(enabled ? R.string.log_viewer_live_on : R.string.log_viewer_live_off);
        mLiveButton.setBackgroundResource(enabled
                ? R.drawable.bg_battly_button_primary
                : R.drawable.bg_battly_button_secondary);
        if (enabled) {
            mHandler.post(mLiveRefreshRunnable);
        }
    }

    private void updateStats(int lines, int warnings, int errors) {
        if (mLinesStat == null) return;
        mLinesStat.setText(getString(R.string.log_viewer_lines, lines));
        mWarningsStat.setText(getString(R.string.log_viewer_warnings, warnings));
        mErrorsStat.setText(getString(R.string.log_viewer_errors, errors));
    }

    private void showEmptyState() {
        mRawContent = "";
        mLogContentView.setText(R.string.log_viewer_empty);
        mLogMetaView.setText("");
        updateStats(0, 0, 0);
    }

    private String getCurrentSearch() {
        View root = getView();
        if (root == null) return "";
        EditText search = root.findViewById(R.id.log_viewer_search);
        return search.getText() == null ? "" : search.getText().toString();
    }

    private void copySelectedLog() {
        File selectedLog = getSelectedLogFile();
        if (selectedLog == null) {
            Toast.makeText(requireContext(), R.string.log_viewer_no_selection, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        selectedLog.getName(), Tools.read(selectedLog)));
                Toast.makeText(requireContext(), R.string.log_viewer_copied, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException exception) {
            Tools.showError(requireContext(), getString(R.string.log_viewer_read_failed, exception),
                    exception);
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
                Activity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (isAdded()) showUploadResult(result);
                });
            } catch (Exception exception) {
                Activity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    resetUploadButton();
                    Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                            .setTitle(R.string.log_viewer_upload_failed_title)
                            .setMessage(getString(R.string.log_viewer_upload_failed,
                                    exception.getMessage()))
                            .setPositiveButton(android.R.string.ok, null));
                });
            }
        });
    }

    private void showUploadResult(MclogsUploader.UploadResult result) {
        resetUploadButton();
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.log_viewer_upload_complete)
                .setMessage(getString(R.string.log_viewer_upload_result,
                        result.url, result.lines, result.errors))
                .setPositiveButton(R.string.log_viewer_share_link, (dialog, which) -> {
                    Intent share = new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, result.url);
                    startActivity(Intent.createChooser(
                            share, getString(R.string.log_viewer_share_link)));
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
        if (mLogSelector == null) return null;
        int position = mLogSelector.getSelectedItemPosition();
        if (position < 0 || position >= mLogFiles.size()) return null;
        return mLogFiles.get(position);
    }

    private String buildDisplayName(File logFile) {
        String prefix;
        if ("latestlog.txt".equals(logFile.getName())) {
            prefix = getString(R.string.log_viewer_latest_log);
        } else if ("latestcrash.txt".equals(logFile.getName())) {
            prefix = getString(R.string.log_viewer_latest_crash);
        } else if (logFile.getParentFile() != null
                && "installer_logs".equals(logFile.getParentFile().getName())) {
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
        return getString(R.string.log_viewer_meta,
                logFile.getAbsolutePath(), sizeKb, modifiedAt)
                .replace('\n', ' ');
    }

    private String getStringSafely(int resource, Object... arguments) {
        Context context = getContext();
        if (context == null) return "";
        return context.getString(resource, arguments);
    }

    private static void addIfReadable(List<File> files, File file) {
        if (file.isFile() && file.canRead()) files.add(file);
    }

    private static final class RenderedLine {
        final String text;
        final int severity;

        RenderedLine(String text, int severity) {
            this.text = text;
            this.severity = severity;
        }
    }

    private static final class RenderResult {
        final CharSequence text;
        final int totalLines;
        final int warnings;
        final int errors;

        RenderResult(CharSequence text, int totalLines, int warnings, int errors) {
            this.text = text;
            this.totalLines = totalLines;
            this.warnings = warnings;
            this.errors = errors;
        }
    }
}
