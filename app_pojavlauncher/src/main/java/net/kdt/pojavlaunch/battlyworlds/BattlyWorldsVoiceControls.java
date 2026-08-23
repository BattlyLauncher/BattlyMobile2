package net.kdt.pojavlaunch.battlyworlds;

import android.content.Context;

public final class BattlyWorldsVoiceControls {
    public static boolean toggleMicrophone(Context context) {
        boolean muted = !isMicrophoneMuted(context);
        BattlyWorldsPreferences.setVoiceMuted(context, muted);
        BattlyWorldsVoiceManager.setMuted(muted);
        return muted;
    }

    public static boolean toggleAudio(Context context) {
        boolean deafened = !isAudioMuted(context);
        BattlyWorldsPreferences.setVoiceDeafened(context, deafened);
        BattlyWorldsVoiceManager.setDeafened(deafened);
        return deafened;
    }

    public static boolean isMicrophoneMuted(Context context) {
        return BattlyWorldsPreferences.isVoiceMuted(context);
    }

    public static boolean isAudioMuted(Context context) {
        return BattlyWorldsPreferences.isVoiceDeafened(context);
    }

    private BattlyWorldsVoiceControls() { }
}
