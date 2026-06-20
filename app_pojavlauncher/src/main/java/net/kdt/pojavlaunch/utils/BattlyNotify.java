package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.kdt.pojavlaunch.Tools;

public final class BattlyNotify {
    private static final long DURATION_MS = 5200L;

    private BattlyNotify() {
    }

    public static void warning(Activity activity, String title, String message) {
        show(activity, title, message, 0xFFE6B74A);
    }

    public static void show(Activity activity, String title, String message, int accentColor) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        Tools.runOnUiThread(() -> addNotifyView(activity, title, message, accentColor));
    }

    private static void addNotifyView(Activity activity, String title, String message, int accentColor) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setClipToPadding(false);
        overlay.setClipChildren(false);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 10));
        card.setBackground(cardBackground(accentColor));
        card.setElevation(dp(activity, 8));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView icon = new TextView(activity);
        icon.setGravity(Gravity.CENTER);
        icon.setText("!");
        icon.setTextColor(accentColor);
        icon.setTextSize(20);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setBackground(circleBackground(accentColor));
        row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(activity, 12), 0, 0, 0);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(titleView);

        TextView messageView = new TextView(activity);
        messageView.setText(message);
        messageView.setTextColor(0xFFD8E2EA);
        messageView.setTextSize(12);
        messageView.setMaxLines(3);
        texts.addView(messageView);

        card.addView(row);

        overlay.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 64), dp(activity, 430)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        overlayParams.topMargin = dp(activity, 34);
        decor.addView(overlay, overlayParams);

        overlay.setAlpha(0f);
        overlay.setTranslationY(-dp(activity, 16));
        overlay.animate()
                .alpha(1f)
                .translationY(0f)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(180)
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            overlay.animate()
                    .alpha(0f)
                    .translationY(-dp(activity, 12))
                    .setDuration(180)
                    .withEndAction(() -> {
                        if (overlay.getParent() instanceof ViewGroup) {
                            ((ViewGroup) overlay.getParent()).removeView(overlay);
                        }
                    })
                    .start();
        }, DURATION_MS);
    }

    private static GradientDrawable cardBackground(int accentColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xF21A2632);
        drawable.setCornerRadius(18);
        drawable.setStroke(1, withAlpha(accentColor, 0xAA));
        return drawable;
    }

    private static GradientDrawable circleBackground(int accentColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.TRANSPARENT);
        drawable.setStroke(2, accentColor);
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
