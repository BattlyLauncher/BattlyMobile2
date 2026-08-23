package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.WeakHashMap;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

final class BattlyWorldsPresenceOverlay {
    private static final long DISPLAY_MS = 5000L;
    private static final WeakHashMap<Activity, LinearLayout> CONTAINERS = new WeakHashMap<>();
    private static final Map<Activity, Queue<String>> PENDING = new WeakHashMap<>();
    private static final Map<Activity, Boolean> CONNECTION_IN_PROGRESS = new WeakHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void showJoined(Activity activity, String username) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            if (Boolean.TRUE.equals(CONNECTION_IN_PROGRESS.get(activity))) {
                PENDING.computeIfAbsent(activity, ignored -> new ArrayDeque<>()).offer(username);
                return;
            }
            addCard(activity, username);
        });
    }

    static void setConnectionInProgress(Activity activity, boolean inProgress) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            CONNECTION_IN_PROGRESS.put(activity, inProgress);
            BattlyWorldsVoiceOverlay.setConnectionBlocked(activity, inProgress);
            if (inProgress) return;
            Queue<String> pending = PENDING.remove(activity);
            if (pending == null || pending.isEmpty()) return;
            MAIN.postDelayed(() -> {
                String username;
                while ((username = pending.poll()) != null) addCard(activity, username);
            }, 350L);
        });
    }

    static boolean isConnectionInProgress(Activity activity) {
        return activity != null && Boolean.TRUE.equals(CONNECTION_IN_PROGRESS.get(activity));
    }

    private static void addCard(Activity activity, String username) {
        if (activity.isDestroyed()) return;
        LinearLayout container = CONTAINERS.get(activity);
        if (container == null || container.getParent() == null) {
            container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.END);
            container.setClipChildren(false);
            container.setClipToPadding(false);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    Math.min(dp(activity, 360), activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 32)),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            params.topMargin = dp(activity, 72);
            params.rightMargin = dp(activity, 16);
            ((ViewGroup) activity.getWindow().getDecorView()).addView(container, params);
            CONTAINERS.put(activity, container);
        }

        LinearLayout card = new LinearLayout(activity);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 13), dp(activity, 10), dp(activity, 8), dp(activity, 10));
        card.setBackground(background());
        card.setElevation(dp(activity, 8));

        ImageView mark = new ImageView(activity);
        mark.setImageResource(net.kdt.pojavlaunch.R.drawable.bworlds);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setContentDescription(activity.getString(net.kdt.pojavlaunch.R.string.battlyworlds_title));
        card.addView(mark, new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(activity, 10), 0, dp(activity, 8), 0);
        TextView title = new TextView(activity);
        title.setText(username);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView subtitle = new TextView(activity);
        subtitle.setText(activity.getString(net.kdt.pojavlaunch.R.string.battlyworlds_member_joined));
        subtitle.setTextColor(0xFFC6D6E3);
        subtitle.setTextSize(12);
        text.addView(title);
        text.addView(subtitle);
        card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView close = new ImageView(activity);
        close.setImageResource(net.kdt.pojavlaunch.R.drawable.ic_close_white);
        close.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        close.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
        close.setContentDescription(activity.getString(android.R.string.cancel));
        card.addView(close, new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(activity, 8);
        container.addView(card, 0, cardParams);
        card.setAlpha(0f);
        card.setTranslationX(dp(activity, 80));
        card.animate().alpha(1f).translationX(0f).setDuration(220).start();

        Runnable dismiss = () -> dismiss(card);
        close.setOnClickListener(v -> dismiss.run());
        MAIN.postDelayed(dismiss, DISPLAY_MS);
    }

    private static void dismiss(View card) {
        if (card.getParent() == null || Boolean.TRUE.equals(card.getTag())) return;
        card.setTag(Boolean.TRUE);
        card.animate().cancel();
        card.animate().alpha(0f).translationX(dp(card.getContext(), 100)).setDuration(240)
                .withEndAction(() -> {
                    if (card.getParent() instanceof ViewGroup) {
                        ((ViewGroup) card.getParent()).removeView(card);
                    }
                }).start();
    }

    private static GradientDrawable background() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xF21A2A33);
        drawable.setCornerRadius(14);
        drawable.setStroke(1, 0x668ADBC6);
        return drawable;
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private BattlyWorldsPresenceOverlay() {
    }
}
