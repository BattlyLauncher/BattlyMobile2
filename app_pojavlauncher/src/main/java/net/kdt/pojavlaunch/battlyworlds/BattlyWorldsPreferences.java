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
    public static final String KEY_VOICE_AUTO_JOIN = "battlyworlds_voice_auto_join";
    public static final String KEY_VOICE_MUTED = "battlyworlds_voice_muted";
    public static final String KEY_VOICE_DEAFENED = "battlyworlds_voice_deafened";
    public static final String KEY_VOICE_SOUNDS = "battlyworlds_voice_sounds";
    public static final String KEY_VOICE_OVERLAY_ENABLED = "battlyworlds_voice_overlay_enabled";
    public static final String KEY_VOICE_OVERLAY_OPACITY = "battlyworlds_voice_overlay_opacity";
    private static final String KEY_MICROPHONE_EXPLANATION_SHOWN = "battlyworlds_microphone_explanation_shown";
    private static final String KEY_VOICE_VOLUME_PREFIX = "battlyworlds_voice_volume_";
    private static final String KEY_VOICE_OVERLAY_X = "battlyworlds_voice_overlay_x";
    private static final String KEY_VOICE_OVERLAY_Y = "battlyworlds_voice_overlay_y";
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

    public static boolean shouldAutoJoinVoice(Context context) {
        return preferences(context).getBoolean(KEY_VOICE_AUTO_JOIN, true);
    }

    public static boolean isVoiceMuted(Context context) {
        return preferences(context).getBoolean(KEY_VOICE_MUTED, false);
    }

    public static void setVoiceMuted(Context context, boolean muted) {
        preferences(context).edit().putBoolean(KEY_VOICE_MUTED, muted).apply();
    }

    public static boolean isVoiceDeafened(Context context) {
        return preferences(context).getBoolean(KEY_VOICE_DEAFENED, false);
    }

    public static boolean areVoiceSoundsEnabled(Context context) {
        return preferences(context).getBoolean(KEY_VOICE_SOUNDS, true);
    }

    public static boolean isVoiceOverlayEnabled(Context context) {
        return preferences(context).getBoolean(KEY_VOICE_OVERLAY_ENABLED, true);
    }

    public static boolean wasMicrophoneExplanationShown(Context context) {
        return preferences(context).getBoolean(KEY_MICROPHONE_EXPLANATION_SHOWN, false);
    }

    public static void markMicrophoneExplanationShown(Context context) {
        preferences(context).edit().putBoolean(KEY_MICROPHONE_EXPLANATION_SHOWN, true).apply();
    }

    public static void setVoiceDeafened(Context context, boolean deafened) {
        preferences(context).edit().putBoolean(KEY_VOICE_DEAFENED, deafened).apply();
    }

    public static int getVoiceUserVolume(Context context, String userId) {
        String key = KEY_VOICE_VOLUME_PREFIX + safeUserId(userId);
        int storedVolume = preferences(context).getInt(key, 100);
        int safeVolume = clampVoiceUserVolume(storedVolume);
        if (safeVolume != storedVolume) {
            preferences(context).edit().putInt(key, safeVolume).apply();
        }
        return safeVolume;
    }

    public static void setVoiceUserVolume(Context context, String userId, int volume) {
        preferences(context).edit().putInt(KEY_VOICE_VOLUME_PREFIX + safeUserId(userId),
                clampVoiceUserVolume(volume)).apply();
    }

    static int clampVoiceUserVolume(int volume) {
        return Math.max(0, Math.min(volume, 100));
    }

    public static int getVoiceOverlayOpacity(Context context) {
        return Math.max(25, Math.min(100,
                preferences(context).getInt(KEY_VOICE_OVERLAY_OPACITY, 92)));
    }

    public static void setVoiceOverlayOpacity(Context context, int opacity) {
        preferences(context).edit().putInt(KEY_VOICE_OVERLAY_OPACITY,
                Math.max(25, Math.min(100, opacity))).apply();
    }

    public static int getVoiceOverlayX(Context context) {
        return Math.max(0, Math.min(1000,
                preferences(context).getInt(KEY_VOICE_OVERLAY_X, 1000)));
    }

    public static int getVoiceOverlayY(Context context) {
        return Math.max(0, Math.min(1000,
                preferences(context).getInt(KEY_VOICE_OVERLAY_Y, 45)));
    }

    public static void setVoiceOverlayPosition(Context context, int x, int y) {
        preferences(context).edit()
                .putInt(KEY_VOICE_OVERLAY_X, Math.max(0, Math.min(1000, x)))
                .putInt(KEY_VOICE_OVERLAY_Y, Math.max(0, Math.min(1000, y)))
                .apply();
    }

    public static void resetVoiceOverlayPosition(Context context) {
        preferences(context).edit()
                .remove(KEY_VOICE_OVERLAY_X)
                .remove(KEY_VOICE_OVERLAY_Y)
                .apply();
    }

    private static String safeUserId(String userId) {
        return (userId == null ? "unknown" : userId).replaceAll("[^A-Za-z0-9_-]", "_");
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
