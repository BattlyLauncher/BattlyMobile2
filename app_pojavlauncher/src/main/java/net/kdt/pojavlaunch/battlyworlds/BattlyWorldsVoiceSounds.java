package net.kdt.pojavlaunch.battlyworlds;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import net.kdt.pojavlaunch.R;

final class BattlyWorldsVoiceSounds {
    private static final String TAG = "BattlyWorldsVoiceSounds";

    static void prepare(Context context) { }

    static void playConnected(Context context) { play(context, true); }
    static void playDisconnected(Context context) { play(context, false); }

    private static void play(Context context, boolean connected) {
        if (context == null || !BattlyWorldsPreferences.areVoiceSoundsEnabled(context)) return;
        try {
            MediaPlayer player = MediaPlayer.create(context.getApplicationContext(),
                    connected ? R.raw.battlyworlds_voice_connect
                            : R.raw.battlyworlds_voice_disconnect,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(), 0);
            if (player == null) return;
            player.setVolume(0.72f, 0.72f);
            player.setOnCompletionListener(MediaPlayer::release);
            player.setOnErrorListener((failedPlayer, what, extra) -> {
                failedPlayer.release();
                return true;
            });
            player.start();
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to play voice event sound", error);
        }
    }

    private BattlyWorldsVoiceSounds() { }
}
