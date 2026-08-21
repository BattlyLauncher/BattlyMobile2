package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsFeature;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsDiscovery;
import net.kdt.pojavlaunch.fragments.BattlySkinManagerFragment;
import net.kdt.pojavlaunch.fragments.BattlyWorldsFragment;

public final class BattlyHomeHubDialog {
    private BattlyHomeHubDialog() {}

    public static void show(FragmentActivity activity) {
        show(activity, null);
    }

    public static void show(FragmentActivity activity, Runnable onDiscoveryChanged) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 20));

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        heading.addView(logo, new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)));
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(activity, 13), 0, 0, 0);
        text.addView(label(activity, R.string.battly_hub_title, 21, true, Color.WHITE));
        text.addView(label(activity, R.string.battly_hub_subtitle, 12, false, 0xFFC6D6E3));
        heading.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(heading);

        View worlds = option(activity, R.drawable.logo, R.string.battlyworlds_title,
                R.string.battly_hub_worlds_description, true);
        View skins = option(activity, R.drawable.logo, R.string.battly_skins_title,
                R.string.battly_hub_skins_description, false);
        root.addView(worlds, optionParams(activity, 18));
        root.addView(skins, optionParams(activity, 9));

        AlertDialog dialog = Tools.createStyledDialogBuilder(activity).setView(root).create();
        worlds.setOnClickListener(v -> {
            BattlyWorldsDiscovery.markCurrentCampaignSeen(activity);
            if (onDiscoveryChanged != null) onDiscoveryChanged.run();
            dialog.dismiss();
            if (!BattlyWorldsFeature.ENABLED) {
                BattlyWorldsFeature.showDisabledDialog(activity);
                return;
            }
            BattlyWorldsTrailerDialog.showIfNeeded(activity, () -> Tools.swapFragment(activity,
                    BattlyWorldsFragment.class, BattlyWorldsFragment.TAG, null));
        });
        skins.setOnClickListener(v -> {
            dialog.dismiss();
            Tools.swapFragment(activity, BattlySkinManagerFragment.class, BattlySkinManagerFragment.TAG, null);
        });
        Tools.styleDialog(dialog);
        dialog.show();
    }

    private static View option(Activity activity, int iconRes, int titleRes, int descriptionRes,
                               boolean showNewBadge) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 15), dp(activity, 13), dp(activity, 15), dp(activity, 13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB5263944);
        bg.setCornerRadius(dp(activity, 16));
        bg.setStroke(dp(activity, 1), 0x334ED7C0);
        row.setBackground(bg);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        row.addView(icon, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(activity, 14), 0, dp(activity, 8), 0);
        copy.addView(label(activity, titleRes, 16, true, Color.WHITE));
        copy.addView(label(activity, descriptionRes, 12, false, 0xFFC6D6E3));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        if (showNewBadge && BattlyWorldsDiscovery.shouldShowNew(activity)) {
            TextView badge = label(activity, 0, 9, true, 0xFF10221E);
            badge.setText(R.string.battly_new_badge);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(dp(activity, 40));
            badge.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
            badge.setBackgroundResource(R.drawable.bg_battly_new_badge);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, dp(activity, 20));
            badgeParams.setMargins(0, 0, dp(activity, 8), 0);
            row.addView(badge, badgeParams);
            BattlyWorldsDiscovery.updateBadge(badge);
        }
        TextView arrow = label(activity, 0, 24, false, 0xFF8ADBC6);
        arrow.setText(R.string.battly_disclosure_arrow);
        row.addView(arrow);
        return row;
    }

    private static TextView label(Activity activity, int res, int size, boolean bold, int color) {
        TextView view = new TextView(activity);
        if (res != 0) view.setText(res);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static LinearLayout.LayoutParams optionParams(Activity activity, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(activity, top), 0, 0);
        return params;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
