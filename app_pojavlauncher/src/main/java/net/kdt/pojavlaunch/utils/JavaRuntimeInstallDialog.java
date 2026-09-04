package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;

import java.util.concurrent.atomic.AtomicBoolean;

public final class JavaRuntimeInstallDialog {
    private JavaRuntimeInstallDialog() {
    }

    public static boolean isJava8Ready() {
        return JREAutoDownloader.isJavaVersionInstalled(8);
    }

    public static void ensureJava8(Activity activity, Runnable afterInstalled) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (isJava8Ready()) {
            String jreName = JREAutoDownloader.getCompatibleInstalledJreName(8);
            if (jreName != null) {
                JREAutoDownloader.promoteDefaultRuntimeIfMissing(jreName);
            }
            if (afterInstalled != null) {
                afterInstalled.run();
            }
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            new Controller(activity, afterInstalled).startInstall();
        } else {
            Tools.runOnUiThread(() -> ensureJava8(activity, afterInstalled));
        }
    }

    private static final class Controller {
        private final Activity mActivity;
        private final Runnable mAfterInstalled;
        private final AlertDialog mDialog;
        private final ProgressBar mProgressBar;
        private final TextView mStatusText;
        private final TextView mRetryButton;
        private final ProgressListener mListener;
        private final AtomicBoolean mCleanedUp = new AtomicBoolean();
        private boolean mInstalling;

        private Controller(Activity activity, Runnable afterInstalled) {
            mActivity = activity;
            mAfterInstalled = afterInstalled;
            ProgressLayout.setProgressMuted(ProgressLayout.UNPACK_RUNTIME, true);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(24), dp(22), dp(24), dp(22));
            root.setBackground(makeRound(28, 0xF0182A36, 0x558DEEDC));

            LinearLayout header = new LinearLayout(activity);
            header.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView logo = new ImageView(activity);
            logo.setImageResource(R.drawable.logo);
            logo.setPadding(dp(10), dp(10), dp(10), dp(10));
            logo.setBackground(makeRound(18, 0x2637E9C5, 0x338DEEDC));
            header.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

            LinearLayout titleBlock = new LinearLayout(activity);
            titleBlock.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleParams.setMargins(dp(14), 0, 0, 0);
            header.addView(titleBlock, titleParams);

            titleBlock.addView(text(activity.getString(R.string.java_runtime_download_title), 20, true, 0xFFFFFFFF));
            TextView desc = text(activity.getString(R.string.java_runtime_download_message), 13, false, 0xFFC7D4DF);
            desc.setLineSpacing(dp(2), 1f);
            titleBlock.addView(desc);

            mProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            mProgressBar.setMax(100);
            mProgressBar.setIndeterminate(true);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
            barParams.setMargins(0, dp(20), 0, 0);
            root.addView(mProgressBar, barParams);

            mStatusText = text(activity.getString(R.string.java_runtime_download_waiting), 14, true, 0xFF8DEEDC);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            statusParams.setMargins(0, dp(12), 0, 0);
            root.addView(mStatusText, statusParams);

            mRetryButton = text(activity.getString(R.string.global_retry), 14, true, 0xFF0A2D34);
            mRetryButton.setGravity(Gravity.CENTER);
            mRetryButton.setBackground(makeRound(18, 0xFF8DEEDC, 0));
            mRetryButton.setVisibility(View.GONE);
            mRetryButton.setOnClickListener(v -> {
                if (!mInstalling) {
                    startInstall();
                }
            });
            LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            retryParams.gravity = Gravity.END;
            retryParams.setMargins(0, dp(18), 0, 0);
            retryParams.width = dp(132);
            root.addView(mRetryButton, retryParams);

            mDialog = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                    .setView(root)
                    .create();
            mDialog.setCancelable(false);
            mDialog.setCanceledOnTouchOutside(false);

            mListener = new ProgressListener() {
                @Override
                public void onProgressStarted() {
                    update(0, activity.getString(R.string.java_runtime_download_waiting));
                }

                @Override
                public void onProgressUpdated(int progress, int resid, Object... va) {
                    update(progress, formatStatus(resid, va));
                }

                @Override
                public void onProgressEnded() {
                }
            };
            mDialog.setOnDismissListener(dialog -> cleanup());
            ProgressKeeper.addListener(ProgressLayout.UNPACK_RUNTIME, mListener);
            mDialog.show();
        }

        private void startInstall() {
            mInstalling = true;
            mRetryButton.setVisibility(View.GONE);
            update(-1, mActivity.getString(R.string.java_runtime_download_waiting));
            JREAutoDownloader.downloadJREAsync(8, new JREAutoDownloader.DownloadCallback() {
                @Override
                public void onSuccess(String jreName) {
                    JREAutoDownloader.promoteDefaultRuntimeIfMissing(jreName);
                    Tools.runOnUiThread(() -> {
                        mInstalling = false;
                        dismiss();
                        if (mAfterInstalled != null && !mActivity.isFinishing()
                                && !mActivity.isDestroyed()) {
                            mAfterInstalled.run();
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
                    Tools.runOnUiThread(() -> {
                        mInstalling = false;
                        mProgressBar.setIndeterminate(false);
                        mProgressBar.setProgress(0);
                        mStatusText.setText(mActivity.getString(R.string.java_runtime_download_failed,
                                e == null || e.getMessage() == null
                                        ? mActivity.getString(R.string.global_error)
                                        : e.getMessage()));
                        mRetryButton.setVisibility(View.VISIBLE);
                    });
                }
            });
        }

        private void dismiss() {
            try {
                if (!mActivity.isFinishing() && !mActivity.isDestroyed() && mDialog.isShowing()) {
                    mDialog.dismiss();
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                android.util.Log.w("BattlyJavaDialog", "Dialog window was already detached", exception);
            } finally {
                cleanup();
            }
        }

        private void cleanup() {
            if (!mCleanedUp.compareAndSet(false, true)) return;
            ProgressKeeper.removeListener(ProgressLayout.UNPACK_RUNTIME, mListener);
            ProgressLayout.setProgressMuted(ProgressLayout.UNPACK_RUNTIME, false);
        }

        private void update(int progress, String status) {
            Tools.runOnUiThread(() -> {
                if (mCleanedUp.get() || mActivity.isFinishing() || mActivity.isDestroyed()) return;
                boolean determinate = progress >= 0;
                mProgressBar.setIndeterminate(!determinate);
                if (determinate) {
                    mProgressBar.setProgress(Math.min(100, Math.max(0, progress)));
                }
                mStatusText.setText(Tools.isValidString(status)
                        ? status
                        : mActivity.getString(R.string.java_runtime_download_waiting));
            });
        }

        private String formatStatus(int resid, Object... va) {
            if (resid != -1) {
                try {
                    return mActivity.getString(resid, va);
                } catch (Throwable ignored) {
                    return mActivity.getString(resid);
                }
            }
            if (va != null && va.length > 0 && va[0] instanceof String) {
                return (String) va[0];
            }
            return mActivity.getString(R.string.java_runtime_download_waiting);
        }

        private TextView text(String value, int sp, boolean bold, int color) {
            TextView textView = new TextView(mActivity);
            textView.setText(value);
            textView.setTextSize(sp);
            textView.setTextColor(color);
            if (bold) {
                textView.setTypeface(Typeface.DEFAULT_BOLD);
            }
            return textView;
        }

        private GradientDrawable makeRound(int radiusDp, int fill, int stroke) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(fill);
            drawable.setCornerRadius(dp(radiusDp));
            if (stroke != 0) {
                drawable.setStroke(dp(1), stroke);
            }
            return drawable;
        }

        private int dp(int value) {
            return Math.round(value * mActivity.getResources().getDisplayMetrics().density);
        }
    }
}
