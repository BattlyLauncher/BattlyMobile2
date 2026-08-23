package com.kdt;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Displays a bounded, searchable live view of Minecraft output.
 */
public class LoggerView extends ConstraintLayout {
    private static final int MAX_BUFFERED_LINES = 2500;
    private static final long RENDER_BATCH_DELAY_MS = 120L;
    private static final int COLOR_ERROR = Color.rgb(255, 142, 154);
    private static final int COLOR_WARNING = Color.rgb(255, 200, 87);
    private static final int COLOR_INFO = Color.rgb(220, 230, 235);
    private static final int COLOR_QUERY = Color.argb(105, 62, 142, 208);

    private enum LogFilter {
        ALL,
        ERRORS,
        WARNINGS
    }

    private final ArrayDeque<String> mLines = new ArrayDeque<>();

    private Logger.eventLogListener mLogListener;
    private DefocusableScrollView mScrollView;
    private TextView mLogTextView;
    private TextView mPauseButton;
    private TextView mFollowButton;
    private TextView mCounterView;
    private TextView mAllFilterButton;
    private TextView mErrorsFilterButton;
    private TextView mWarningsFilterButton;
    private EditText mSearchView;
    private LogFilter mFilter = LogFilter.ALL;
    private boolean mPaused;
    private boolean mFollow = true;
    private boolean mListenerRegistered;
    private boolean mRenderScheduled;
    private boolean mInitialized;
    private final Runnable mRenderRunnable = () -> {
        mRenderScheduled = false;
        if (!mPaused) renderBuffer();
    };

    public LoggerView(@NonNull Context context) {
        this(context, null);
    }

    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (!mInitialized) return;
        if (visibility == VISIBLE) {
            registerListener();
            renderBuffer();
        } else {
            unregisterListener();
            removeCallbacks(mRenderRunnable);
            mRenderScheduled = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        unregisterListener();
        removeCallbacks(mRenderRunnable);
        super.onDetachedFromWindow();
    }

    private void init() {
        inflate(getContext(), R.layout.view_logger, this);
        mLogTextView = findViewById(R.id.content_log_view);
        mLogTextView.setTypeface(Typeface.MONOSPACE);
        mLogTextView.setMaxLines(Integer.MAX_VALUE);
        mLogTextView.setEllipsize(null);

        mScrollView = findViewById(R.id.content_log_scroll);
        mScrollView.setKeepFocusing(true);

        mPauseButton = findViewById(R.id.content_log_toggle_log);
        mFollowButton = findViewById(R.id.content_log_toggle_autoscroll);
        mCounterView = findViewById(R.id.log_live_counter);
        mSearchView = findViewById(R.id.log_live_search);
        mAllFilterButton = findViewById(R.id.log_live_filter_all);
        mErrorsFilterButton = findViewById(R.id.log_live_filter_errors);
        mWarningsFilterButton = findViewById(R.id.log_live_filter_warnings);

        ImageButton cancelButton = findViewById(R.id.log_view_cancel);
        cancelButton.setOnClickListener(view -> setVisibility(GONE));
        mPauseButton.setOnClickListener(view -> setPaused(!mPaused));
        mFollowButton.setOnClickListener(view -> setFollow(!mFollow));
        findViewById(R.id.log_live_copy).setOnClickListener(view -> copyVisibleOutput());
        findViewById(R.id.log_live_clear).setOnClickListener(view -> clearOutput());
        mAllFilterButton.setOnClickListener(view -> setFilter(LogFilter.ALL));
        mErrorsFilterButton.setOnClickListener(view -> setFilter(LogFilter.ERRORS));
        mWarningsFilterButton.setOnClickListener(view -> setFilter(LogFilter.WARNINGS));

        mSearchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                scheduleRender();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mSearchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            Tools.hideKeyboard(v);
            return true;
        });

        mLogListener = text -> {
            if (text == null) return;
            post(() -> {
                appendLines(text);
                scheduleRender();
            });
        };

        updateFilterButtons();
        setFollow(true);
        setPaused(false);
        updateCounter();
        mInitialized = true;
        if (getVisibility() == VISIBLE) registerListener();
    }

    private void appendLines(String text) {
        String[] incomingLines = text.split("\\r?\\n", -1);
        for (String line : incomingLines) {
            mLines.addLast(line);
            while (mLines.size() > MAX_BUFFERED_LINES) {
                mLines.removeFirst();
            }
        }
        updateCounter();
    }

    private void scheduleRender() {
        if (mPaused || mRenderScheduled || getVisibility() != VISIBLE) return;
        mRenderScheduled = true;
        postDelayed(mRenderRunnable, RENDER_BATCH_DELAY_MS);
    }

    private void renderBuffer() {
        String query = mSearchView.getText() == null
                ? ""
                : mSearchView.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<String> snapshot = new ArrayList<>(mLines);
        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (String line : snapshot) {
            int severity = getSeverity(line);
            if (!matchesFilter(severity)
                    || (!query.isEmpty() && !line.toLowerCase(Locale.ROOT).contains(query))) {
                continue;
            }
            if (builder.length() > 0) builder.append('\n');
            int lineStart = builder.length();
            builder.append(line);
            int lineEnd = builder.length();
            builder.setSpan(new ForegroundColorSpan(colorForSeverity(severity)),
                    lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!query.isEmpty()) applyQueryHighlights(builder, line, query, lineStart);
        }

        mLogTextView.setText(builder);
        if (mFollow) {
            mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
        }
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

    private boolean matchesFilter(int severity) {
        return mFilter == LogFilter.ALL
                || (mFilter == LogFilter.ERRORS && severity == 2)
                || (mFilter == LogFilter.WARNINGS && severity == 1);
    }

    private int colorForSeverity(int severity) {
        if (severity == 2) return COLOR_ERROR;
        if (severity == 1) return COLOR_WARNING;
        return COLOR_INFO;
    }

    private void setPaused(boolean paused) {
        mPaused = paused;
        mPauseButton.setText(paused ? R.string.log_live_resume : R.string.log_live_pause);
        mPauseButton.setBackgroundResource(paused
                ? R.drawable.bg_battly_button_secondary
                : R.drawable.bg_battly_button_primary);
        if (!paused) renderBuffer();
    }

    private void setFollow(boolean follow) {
        mFollow = follow;
        mFollowButton.setBackgroundResource(follow
                ? R.drawable.bg_battly_button_primary
                : R.drawable.bg_battly_button_secondary);
        mFollowButton.setAlpha(follow ? 1f : 0.82f);
        mScrollView.setKeepFocusing(follow);
        if (follow) mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void setFilter(LogFilter filter) {
        if (mFilter == filter) return;
        mFilter = filter;
        updateFilterButtons();
        renderBuffer();
    }

    private void updateFilterButtons() {
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

    private void copyVisibleOutput() {
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Battly live console", mLogTextView.getText()));
        Toast.makeText(getContext(), R.string.log_live_copied, Toast.LENGTH_SHORT).show();
    }

    private void clearOutput() {
        mLines.clear();
        removeCallbacks(mRenderRunnable);
        mRenderScheduled = false;
        mLogTextView.setText("");
        updateCounter();
    }

    private void updateCounter() {
        mCounterView.setText(getContext().getString(R.string.log_viewer_lines, mLines.size()));
    }

    private void registerListener() {
        if (mListenerRegistered || mLogListener == null) return;
        Logger.addLogListener(mLogListener);
        mListenerRegistered = true;
    }

    private void unregisterListener() {
        if (!mListenerRegistered || mLogListener == null) return;
        Logger.removeLogListener(mLogListener);
        mListenerRegistered = false;
    }
}
