package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public final class BattlyUpdateVideoDialog {
    private static final String PREF_UPDATE_VIDEO_SEEN = "battly_update_video_seen_2026_06_v2_t75b_v6";
    private static final long ACTION_AUTO_HIDE_MS = 2600L;
    private static final String VIDEO_URL =
            "https://cdn.battlylauncher.com/public/mobile/onboarding/battly-mobile-v2-update.mp4";

    private BattlyUpdateVideoDialog() {
    }

    public static void showIfNeeded(Activity activity) {
        showIfNeeded(activity, null);
    }

    public static void showIfNeeded(Activity activity, Runnable afterDone) {
        if (activity == null || activity.isFinishing()) {
            runAfterDone(afterDone);
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences(BattlyPlusManager.PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_UPDATE_VIDEO_SEEN, false)) {
            runAfterDone(afterDone);
            return;
        }
        Tools.runOnUiThread(() -> show(activity, prefs, afterDone));
    }

    private static void show(Activity activity, SharedPreferences prefs, Runnable afterDone) {
        if (activity.isFinishing()) {
            runAfterDone(afterDone);
            return;
        }
        if (prefs.getBoolean(PREF_UPDATE_VIDEO_SEEN, false)) {
            runAfterDone(afterDone);
            return;
        }

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);

        VideoView videoView = new VideoView(activity);
        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        ProgressBar progressBar = new ProgressBar(activity);
        root.addView(progressBar, new FrameLayout.LayoutParams(
                dp(activity, 44), dp(activity, 44), Gravity.CENTER));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        actions.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10));
        actions.setBackgroundColor(0xB0000000);
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 72), Gravity.BOTTOM);
        root.addView(actions, actionParams);

        TextView skip = actionButton(activity, activity.getString(R.string.onboarding_action_skip));
        TextView continueButton = actionButton(activity, activity.getString(R.string.battly_update_video_continue));
        actions.addView(skip);
        actions.addView(continueButton);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(false);

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable hideActions = () -> hideActions(actions);
        Runnable showActions = () -> {
            showActions(actions);
            handler.removeCallbacks(hideActions);
            handler.postDelayed(hideActions, ACTION_AUTO_HIDE_MS);
        };
        View.OnTouchListener revealActions = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                showActions.run();
                hideSystemUi(dialog);
            }
            return false;
        };
        root.setOnTouchListener(revealActions);
        videoView.setOnTouchListener(revealActions);

        final boolean[] dismissing = {false};
        skip.setOnClickListener(v -> closeVideoDialog(dialog, root, dismissing, false));
        continueButton.setOnClickListener(v -> closeVideoDialog(dialog, root, dismissing, false));

        videoView.setVideoURI(Uri.parse(VIDEO_URL));
        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            videoView.start();
            showActions.run();
        });
        videoView.setOnCompletionListener(mp -> closeVideoDialog(dialog, root, dismissing, true));
        videoView.setOnErrorListener((mp, what, extra) -> {
            progressBar.setVisibility(View.GONE);
            showActions.run();
            return true;
        });

        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                window.getDecorView().setPadding(0, 0, 0, 0);
                window.setGravity(Gravity.CENTER);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            hideSystemUi(dialog);
        });
        dialog.setOnDismissListener(d -> {
            handler.removeCallbacksAndMessages(null);
            videoView.stopPlayback();
            showWelcomeDialog(activity, afterDone);
        });
        dialog.show();
        hideSystemUi(dialog);
        prefs.edit().putBoolean(PREF_UPDATE_VIDEO_SEEN, true).apply();
    }

    private static void hideSystemUi(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        View decorView = dialog.getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private static void closeVideoDialog(Dialog dialog, View root, boolean[] dismissing, boolean fade) {
        if (dismissing[0]) {
            return;
        }
        dismissing[0] = true;
        if (!fade) {
            dialog.dismiss();
            return;
        }
        root.animate()
                .alpha(0f)
                .setDuration(520)
                .withEndAction(dialog::dismiss)
                .start();
    }

    private static void hideActions(View actions) {
        if (actions.getVisibility() != View.VISIBLE) {
            return;
        }
        actions.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> actions.setVisibility(View.INVISIBLE))
                .start();
    }

    private static void showActions(View actions) {
        actions.animate().cancel();
        actions.setVisibility(View.VISIBLE);
        actions.animate().alpha(1f).setDuration(160).start();
    }

    private static void showWelcomeDialog(Activity activity, Runnable afterDone) {
        if (activity == null || activity.isFinishing()) {
            runAfterDone(afterDone);
            return;
        }

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setPadding(dp(activity, 32), dp(activity, 32), dp(activity, 32), dp(activity, 32));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(activity, 28), dp(activity, 26), dp(activity, 28), dp(activity, 24));
        card.setBackground(makeGradient(activity, 28, 0xF0264D4A, 0xF0101B2A, 0x778DEEDC));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 96), dp(activity, 560)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        overlay.addView(card, cardParams);

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.logo);
        logo.setPadding(dp(activity, 13), dp(activity, 13), dp(activity, 13), dp(activity, 13));
        logo.setBackground(makeRound(activity, 22, 0x3037E9C5, 0x558DEEDC));
        card.addView(logo, new LinearLayout.LayoutParams(dp(activity, 78), dp(activity, 78)));

        TextView title = text(activity, activity.getString(R.string.battly_update_welcome_title), 25, true, 0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(activity, 18), 0, 0);
        card.addView(title, titleParams);

        TextView desc = text(activity, activity.getString(R.string.battly_update_welcome_desc), 14, false, 0xFFD3E4EA);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(dp(activity, 2), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(activity, 10), 0, dp(activity, 22));
        card.addView(desc, descParams);

        TextView start = actionButton(activity, activity.getString(R.string.onboarding_action_enter));
        start.setBackground(makeRound(activity, 22, 0xFF8DEEDC, 0x008DEEDC));
        start.setTextColor(0xFF062321);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 48));
        card.addView(start, startParams);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.setContentView(overlay);
        dialog.setCanceledOnTouchOutside(false);
        start.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> runAfterDone(afterDone));
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.getDecorView().setPadding(0, 0, 0, 0);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            card.setAlpha(0f);
            card.setScaleX(0.88f);
            card.setScaleY(0.88f);
            card.setTranslationY(dp(activity, 22));
            card.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(420)
                    .start();
            logo.animate().rotation(360f).setDuration(680).start();
            title.setAlpha(0f);
            title.setTranslationY(dp(activity, 10));
            title.animate().alpha(1f).translationY(0f).setStartDelay(160).setDuration(300).start();
            desc.setAlpha(0f);
            desc.animate().alpha(1f).setStartDelay(260).setDuration(300).start();
            start.setAlpha(0f);
            start.animate().alpha(1f).setStartDelay(360).setDuration(260).start();
        });
        dialog.show();
    }

    private static void runAfterDone(Runnable afterDone) {
        if (afterDone != null) {
            Tools.runOnUiThread(afterDone);
        }
    }

    private static TextView actionButton(Activity activity, String value) {
        TextView textView = new TextView(activity);
        textView.setText(value.toUpperCase());
        textView.setTextSize(14);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(0xFFFFFFFF);
        textView.setGravity(Gravity.CENTER);
        textView.setMinWidth(dp(activity, 116));
        textView.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        textView.setBackgroundColor(0x00000000);
        return textView;
    }

    private static TextView text(Activity activity, String value, int sp, boolean bold, int color) {
        TextView textView = new TextView(activity);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return textView;
    }

    private static GradientDrawable makeRound(Activity activity, int radiusDp, int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(activity, radiusDp));
        if (stroke != 0) {
            drawable.setStroke(dp(activity, 1), stroke);
        }
        return drawable;
    }

    private static GradientDrawable makeGradient(Activity activity, int radiusDp, int startColor, int endColor,
                                                 int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        drawable.setCornerRadius(dp(activity, radiusDp));
        drawable.setStroke(dp(activity, 1), strokeColor);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
