package net.kdt.pojavlaunch.battlyworlds;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

public final class BattlyWorldsDiscovery {
    private static final String PREFS = "battlyworlds_discovery";
    private static final String CURRENT_CAMPAIGN = "public_rooms_2_2";
    private static final String PREF_SEEN_CAMPAIGN = "seen_campaign";

    private BattlyWorldsDiscovery() {}

    public static boolean shouldShowNew(Context context) {
        return !CURRENT_CAMPAIGN.equals(preferences(context).getString(PREF_SEEN_CAMPAIGN, ""));
    }

    public static void markCurrentCampaignSeen(Context context) {
        preferences(context).edit().putString(PREF_SEEN_CAMPAIGN, CURRENT_CAMPAIGN).apply();
    }

    public static void updateBadge(View badge) {
        if (badge == null) return;
        boolean visible = shouldShowNew(badge.getContext());
        badge.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            badge.clearAnimation();
            return;
        }
        AlphaAnimation pulse = new AlphaAnimation(1f, 0.35f);
        pulse.setDuration(700L);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        badge.startAnimation(pulse);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
