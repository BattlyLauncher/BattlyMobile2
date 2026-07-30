package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;

import com.kdt.mcgui.BattlyProgressTaskView;
import com.kdt.mcgui.ProgressLayout;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;

import java.util.concurrent.CountDownLatch;

public final class InstallProgressDialogController {
    private final Activity mActivity;
    private final AlertDialog mDialog;
    private final BattlyProgressTaskView mTaskView;
    private final ProgressListener mListener;
    private NativeAd mNativeAd;

    public static InstallProgressDialogController show(Context context, boolean showNativeAd) {
        if (!(context instanceof Activity)) {
            return null;
        }
        Activity activity = (Activity) context;
        if (activity.isFinishing()) {
            return null;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return new InstallProgressDialogController(activity, showNativeAd);
        }
        InstallProgressDialogController[] holder = new InstallProgressDialogController[1];
        CountDownLatch latch = new CountDownLatch(1);
        Tools.runOnUiThread(() -> {
            holder[0] = new InstallProgressDialogController(activity, showNativeAd);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return holder[0];
    }

    private InstallProgressDialogController(Activity activity, boolean showNativeAd) {
        mActivity = activity;
        ProgressLayout.setProgressMuted(ProgressLayout.INSTALL_MODPACK, true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackground(makeRound(18, 0xF0182A36, 0x558DEEDC));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.logo);
        logo.setPadding(dp(8), dp(8), dp(8), dp(8));
        logo.setBackground(makeRound(12, 0x2637E9C5, 0x338DEEDC));
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titleBlock = new LinearLayout(activity);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBlockParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBlockParams.setMargins(dp(14), 0, 0, 0);
        header.addView(titleBlock, titleBlockParams);

        TextView title = text(activity.getString(R.string.battly_plus_install_block_title), 18, true, 0xFFFFFFFF);
        titleBlock.addView(title);

        TextView desc = text(activity.getString(R.string.battly_plus_install_block_message), 13, false, 0xFFC7D4DF);
        desc.setLineSpacing(dp(2), 1f);
        titleBlock.addView(desc);

        mTaskView = new BattlyProgressTaskView(activity);
        mTaskView.setTaskIcon(R.drawable.ic_menu_install_jar);
        mTaskView.update(-1, activity.getString(R.string.global_waiting));
        LinearLayout.LayoutParams taskParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        taskParams.setMargins(0, dp(16), 0, 0);
        root.addView(mTaskView, taskParams);

        LinearLayout adContainer = new LinearLayout(activity);
        adContainer.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        adParams.setMargins(0, dp(10), 0, 0);
        root.addView(adContainer, adParams);
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
            if (mNativeAd != null) {
                mNativeAd.destroy();
                mNativeAd = null;
            }
        });
        ProgressKeeper.addListener(ProgressLayout.INSTALL_MODPACK, mListener);
        mDialog.show();
        applyDialogWidth();
        if (showNativeAd) {
            loadNativeAd(adContainer);
        }
    }

    private void loadNativeAd(LinearLayout container) {
        MobileAds.initialize(mActivity, status -> {
            if (mActivity.isFinishing()) {
                return;
            }
            AdLoader loader = new AdLoader.Builder(
                    mActivity,
                    mActivity.getString(R.string.battly_modpack_native_ad_unit_id))
                    .forNativeAd(nativeAd -> Tools.runOnUiThread(() -> {
                        if (mActivity.isFinishing() || !mDialog.isShowing()) {
                            nativeAd.destroy();
                            return;
                        }
                        if (mNativeAd != null) {
                            mNativeAd.destroy();
                        }
                        mNativeAd = nativeAd;
                        container.removeAllViews();
                        container.addView(createNativeAdView(nativeAd));
                        container.setVisibility(android.view.View.VISIBLE);
                        applyDialogWidth();
                    }))
                    .withAdListener(new AdListener() {
                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError error) {
                            container.setVisibility(android.view.View.GONE);
                        }
                    })
                    .build();
            loader.loadAd(new AdRequest.Builder().build());
        });
    }

    private NativeAdView createNativeAdView(NativeAd nativeAd) {
        NativeAdView adView = new NativeAdView(mActivity);
        adView.setBackground(makeRound(14, 0xB5122430, 0x338DEEDC));
        LinearLayout row = new LinearLayout(mActivity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        adView.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(mActivity);
        if (nativeAd.getIcon() != null) {
            icon.setImageDrawable(nativeAd.getIcon().getDrawable());
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        adView.setIconView(icon);

        LinearLayout copy = new LinearLayout(mActivity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(10), 0, dp(10), 0);
        row.addView(copy, copyParams);

        TextView headline = text(nativeAd.getHeadline(), 14, true, 0xFFFFFFFF);
        headline.setMaxLines(1);
        headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(headline);
        adView.setHeadlineView(headline);

        TextView body = text(nativeAd.getBody(), 11, false, 0xFFC7D4DF);
        body.setMaxLines(2);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(body);
        adView.setBodyView(body);

        Button action = new Button(mActivity);
        action.setMinWidth(0);
        action.setMinimumWidth(0);
        action.setAllCaps(false);
        action.setText(nativeAd.getCallToAction());
        action.setTextSize(11);
        action.setTextColor(0xFF0E1B24);
        action.setBackground(makeRound(12, 0xFF8DEEDC, 0));
        row.addView(action, new LinearLayout.LayoutParams(dp(94), dp(40)));
        adView.setCallToActionView(action);
        adView.setNativeAd(nativeAd);
        return adView;
    }

    private void applyDialogWidth() {
        Window window = mDialog.getWindow();
        if (window == null) {
            return;
        }
        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        int horizontalMargin = dp(24);
        int maxWidth = dp(560);
        int width = Math.min(maxWidth, Math.max(dp(300), screenWidth - horizontalMargin * 2));
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    public void dismiss() {
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
            String visibleStatus = Tools.isValidString(status)
                    ? status
                    : mActivity.getString(R.string.global_waiting);
            mTaskView.update(progress, visibleStatus);
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
