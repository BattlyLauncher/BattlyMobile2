package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.fragments.BattlyPlusWelcomeFragment;

public final class BattlyPlusWelcomeDialog {
    private static final String PREF_WELCOME_SEEN = "battly_plus_welcome_seen_v1";

    private BattlyPlusWelcomeDialog() {
    }

    public static void showIfNeeded(Activity activity) {
        showIfNeeded(activity, null);
    }

    public static void showIfNeeded(Activity activity, Runnable afterDismiss) {
        if (activity == null || activity.isFinishing()) {
            runAfterDismiss(afterDismiss);
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences(BattlyPlusManager.PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_WELCOME_SEEN, false) || !BattlyPlusManager.isPlus(activity)) {
            runAfterDismiss(afterDismiss);
            return;
        }
        showPanel(activity, afterDismiss);
    }

    public static void showAfterLogin(Activity activity) {
        showAfterLogin(activity, null);
    }

    public static void showAfterLogin(Activity activity, Runnable afterDismiss) {
        if (activity == null || activity.isFinishing() || !BattlyPlusManager.isPlus(activity)) {
            runAfterDismiss(afterDismiss);
            return;
        }
        showPanel(activity, afterDismiss);
    }

    private static void showPanel(Activity activity, Runnable afterDismiss) {
        if (!(activity instanceof FragmentActivity)) {
            runAfterDismiss(afterDismiss);
            return;
        }
        Tools.runOnUiThread(() -> {
            BattlyPlusWelcomeFragment.prepare(afterDismiss);
            Tools.swapFragment((FragmentActivity) activity, BattlyPlusWelcomeFragment.class,
                    BattlyPlusWelcomeFragment.TAG, null);
        });
    }

    private static void runAfterDismiss(Runnable afterDismiss) {
        if (afterDismiss != null) {
            afterDismiss.run();
        }
    }
}
