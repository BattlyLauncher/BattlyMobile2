package net.kdt.pojavlaunch.battlyworlds;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public final class BattlyWorldsPreferences {
    public static final String KEY_DEFAULT_VISIBILITY = "battlyworlds_default_visibility";
    public static final String KEY_AUTO_LAN_DETECTION = "battlyworlds_auto_lan_detection";
    public static final String KEY_INVITATIONS = "battlyworlds_invitations";
    public static final String KEY_CONNECTION_ALERTS = "battlyworlds_connection_alerts";
    public static final String KEY_CLOSE_ON_GAME_EXIT = "battlyworlds_close_on_game_exit";
    public static final String KEY_DEFAULT_DURATION = "battlyworlds_default_duration";
    public static final String KEY_ALLOW_PUBLIC_LISTING = "battlyworlds_allow_public_listing";
    private static final String KEY_ACTIVE_ROOM_CODE = "battlyworlds_active_room_code";

    private static SharedPreferences preferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static boolean isDefaultPublic(Context context) {
        return isPublicListingAllowed(context)
                && "public".equals(preferences(context).getString(KEY_DEFAULT_VISIBILITY, "private"));
    }

    public static boolean isAutoLanDetectionEnabled(Context context) {
        return preferences(context).getBoolean(KEY_AUTO_LAN_DETECTION, true);
    }

    public static boolean areInvitationsEnabled(Context context) {
        return preferences(context).getBoolean(KEY_INVITATIONS, true);
    }

    public static boolean areConnectionAlertsEnabled(Context context) {
        return preferences(context).getBoolean(KEY_CONNECTION_ALERTS, true);
    }

    public static boolean shouldCloseOnGameExit(Context context) {
        return preferences(context).getBoolean(KEY_CLOSE_ON_GAME_EXIT, true);
    }

    public static boolean isPublicListingAllowed(Context context) {
        return preferences(context).getBoolean(KEY_ALLOW_PUBLIC_LISTING, true);
    }

    public static int getDefaultDurationHours(Context context, int accountMaximumHours) {
        String value = preferences(context).getString(KEY_DEFAULT_DURATION, "6");
        int requested;
        try {
            requested = Integer.parseInt(value == null ? "6" : value);
        } catch (NumberFormatException ignored) {
            requested = 6;
        }
        return clampDuration(requested, accountMaximumHours);
    }

    public static int clampDuration(int requestedHours, int accountMaximumHours) {
        int maximum = Math.max(1, accountMaximumHours);
        return Math.max(1, Math.min(requestedHours, maximum));
    }

    public static int[] durationOptions(int accountMaximumHours) {
        int maximum = Math.max(1, accountMaximumHours);
        int[] presets = {1, 3, 6, 12, 24, 48, 72};
        List<Integer> values = new ArrayList<>();
        for (int preset : presets) {
            if (preset <= maximum) values.add(preset);
        }
        if (!values.contains(maximum)) values.add(maximum);
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    public static void setActiveRoomCode(Context context, String roomCode) {
        preferences(context).edit().putString(KEY_ACTIVE_ROOM_CODE,
                roomCode == null ? "" : roomCode.trim().toUpperCase()).apply();
    }

    public static String getActiveRoomCode(Context context) {
        String code = preferences(context).getString(KEY_ACTIVE_ROOM_CODE, "");
        return code == null ? "" : code;
    }

    public static void clearActiveRoomCode(Context context) {
        preferences(context).edit().remove(KEY_ACTIVE_ROOM_CODE).apply();
    }

    private BattlyWorldsPreferences() {
    }
}
