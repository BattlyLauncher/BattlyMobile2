package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.Gravity;
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

import java.util.concurrent.CountDownLatch;

final class InstallProgressDialogController {
    private final Activity mActivity;
    private final AlertDialog mDialog;
    private final ProgressBar mProgressBar;
    private final TextView mStatusText;
    private final ProgressListener mListener;

    static InstallProgressDialogController show(Context context) {
        if (!(context instanceof Activity)) {
            return null;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing()) {
            return null;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return new InstallProgressDialogController(activity);
        }
        InstallProgressDialogController[] holder = new InstallProgressDialogController[1];
        CountDownLatch latch = new CountDownLatch(1);
        Tools.runOnUiThread(() -> {
            holder[0] = new InstallProgressDialogController(activity);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return holder[0];
    }

    private InstallProgressDialogController(Activity activity) {
        mActivity = activity;
        ProgressLayout.setProgressMuted(ProgressLayout.INSTALL_MODPACK, true);

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
        LinearLayout.LayoutParams titleBlockParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBlockParams.setMargins(dp(14), 0, 0, 0);
        header.addView(titleBlock, titleBlockParams);

        TextView title = text(activity.getString(R.string.battly_plus_install_block_title), 20, true, 0xFFFFFFFF);
        titleBlock.addView(title);

        TextView desc = text(activity.getString(R.string.battly_plus_install_block_message), 13, false, 0xFFC7D4DF);
        desc.setLineSpacing(dp(2), 1f);
        titleBlock.addView(desc);

        mProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        mProgressBar.setMax(100);
        mProgressBar.setIndeterminate(true);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        barParams.setMargins(0, dp(20), 0, 0);
        root.addView(mProgressBar, barParams);

        mStatusText = text(activity.getString(R.string.global_waiting), 14, true, 0xFF8DEEDC);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(12), 0, 0);
        root.addView(mStatusText, statusParams);

        mDialog = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setView(root)
                .create();
        mDialog.setCancelable(false);
        mDialog.setCanceledOnTouchOutside(false);

        mListener = new ProgressListener() {
            private boolean mStarted;

            @Override
            public void onProgressStarted() {
                mStarted = true;
                update(0, R.string.global_waiting);
            }

            @Override
            public void onProgressUpdated(int progress, int resid, Object... va) {
                update(progress, formatStatus(resid, va));
            }

            @Override
            public void onProgressEnded() {
                if (mStarted) {
                    dismiss();
                }
            }
        };
        mDialog.setOnDismissListener(dialog -> {
            ProgressKeeper.removeListener(ProgressLayout.INSTALL_MODPACK, mListener);
            ProgressLayout.setProgressMuted(ProgressLayout.INSTALL_MODPACK, false);
        });
        ProgressKeeper.addListener(ProgressLayout.INSTALL_MODPACK, mListener);
        mDialog.show();
    }

    void dismiss() {
        Tools.runOnUiThread(() -> {
            if (!mActivity.isFinishing() && mDialog.isShowing()) {
                mDialog.dismiss();
            } else {
                ProgressKeeper.removeListener(ProgressLayout.INSTALL_MODPACK, mListener);
                ProgressLayout.setProgressMuted(ProgressLayout.INSTALL_MODPACK, false);
            }
        });
    }

    private void update(int progress, int fallbackString) {
        update(progress, mActivity.getString(fallbackString));
    }

    private void update(int progress, String status) {
        Tools.runOnUiThread(() -> {
            boolean determinate = progress >= 0;
            mProgressBar.setIndeterminate(!determinate);
            if (determinate) {
                mProgressBar.setProgress(Math.min(100, Math.max(0, progress)));
            }
            mStatusText.setText(Tools.isValidString(status) ? status : mActivity.getString(R.string.global_waiting));
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
        return mActivity.getString(R.string.global_waiting);
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
