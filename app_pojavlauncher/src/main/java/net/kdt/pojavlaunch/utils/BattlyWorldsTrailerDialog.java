package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public final class BattlyWorldsTrailerDialog {
    private static final String PREF_SEEN = "battly_worlds_trailer_seen_v1";
    private static final String VIDEO_URL =
            "https://cdn.battlylauncher.com/public/mobile/onboarding/battly-worlds-trailer.mp4";
    private static final long ACTION_HIDE_MS = 2600L;

    private BattlyWorldsTrailerDialog() {}

    public static void showIfNeeded(Activity activity, Runnable afterDone) {
        if (activity == null || activity.isFinishing()) {
            run(afterDone);
            return;
        }
        if (activity.getSharedPreferences(BattlyPlusManager.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_SEEN, false)) {
            run(afterDone);
            return;
        }
        Tools.runOnUiThread(() -> show(activity, afterDone));
    }

    private static void show(Activity activity, Runnable afterDone) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        VideoView video = new VideoView(activity);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        ProgressBar loading = new ProgressBar(activity);
        root.addView(loading, new FrameLayout.LayoutParams(dp(activity, 44), dp(activity, 44), Gravity.CENTER));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10));
        actions.setBackgroundColor(0xA8000000);
        root.addView(actions, new FrameLayout.LayoutParams(-1, dp(activity, 68), Gravity.BOTTOM));
        TextView skip = action(activity, activity.getString(R.string.onboarding_action_skip));
        TextView enter = action(activity, activity.getString(R.string.battlyworlds_trailer_enter));
        actions.addView(skip);
        actions.addView(enter);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(root);
        dialog.setCancelable(false);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable hide = () -> actions.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> actions.setVisibility(View.INVISIBLE)).start();
        Runnable reveal = () -> {
            actions.animate().cancel();
            actions.setVisibility(View.VISIBLE);
            actions.animate().alpha(1f).setDuration(150).start();
            handler.removeCallbacks(hide);
            handler.postDelayed(hide, ACTION_HIDE_MS);
            hideSystemUi(dialog);
        };
        View.OnTouchListener touch = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) reveal.run();
            return false;
        };
        root.setOnTouchListener(touch);
        video.setOnTouchListener(touch);

        final boolean[] closing = {false};
        Runnable close = () -> {
            if (closing[0]) return;
            closing[0] = true;
            dialog.dismiss();
        };
        skip.setOnClickListener(v -> close.run());
        enter.setOnClickListener(v -> close.run());
        video.setVideoURI(Uri.parse(VIDEO_URL));
        video.setOnPreparedListener(player -> {
            loading.setVisibility(View.GONE);
            player.setLooping(false);
            video.start();
            reveal.run();
        });
        video.setOnCompletionListener(player -> {
            if (closing[0]) return;
            closing[0] = true;
            root.animate().alpha(0f).setDuration(500).withEndAction(dialog::dismiss).start();
        });
        video.setOnErrorListener((player, what, extra) -> {
            loading.setVisibility(View.GONE);
            reveal.run();
            return true;
        });
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                window.setLayout(-1, -1);
            }
            hideSystemUi(dialog);
        });
        dialog.setOnDismissListener(ignored -> {
            handler.removeCallbacksAndMessages(null);
            video.stopPlayback();
            activity.getSharedPreferences(BattlyPlusManager.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(PREF_SEEN, true).apply();
            run(afterDone);
        });
        dialog.show();
        hideSystemUi(dialog);
    }

    private static TextView action(Activity activity, String label) {
        TextView view = new TextView(activity);
        view.setText(label.toUpperCase());
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(activity, 18), 0, dp(activity, 18), 0);
        view.setMinHeight(dp(activity, 48));
        return view;
    }

    private static void hideSystemUi(Dialog dialog) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private static void run(Runnable runnable) {
        if (runnable != null) Tools.runOnUiThread(runnable);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
