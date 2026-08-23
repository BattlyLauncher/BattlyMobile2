package net.kdt.pojavlaunch.battlyworlds;

import android.media.MediaRecorder;

final class BattlyWorldsVoiceAudioPolicy {
    final int audioSource;
    final boolean hardwareEchoCanceler;
    final boolean hardwareNoiseSuppressor;

    private BattlyWorldsVoiceAudioPolicy(int audioSource,
                                        boolean hardwareEchoCanceler,
                                        boolean hardwareNoiseSuppressor) {
        this.audioSource = audioSource;
        this.hardwareEchoCanceler = hardwareEchoCanceler;
        this.hardwareNoiseSuppressor = hardwareNoiseSuppressor;
    }

    static BattlyWorldsVoiceAudioPolicy forInput(boolean externalInput) {
        if (externalInput) {
            return new BattlyWorldsVoiceAudioPolicy(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, true, true);
        }

        // Some vendor VOICE_COMMUNICATION implementations capture a secondary
        // cancellation microphone and suppress nearby speech as if it were echo.
        return new BattlyWorldsVoiceAudioPolicy(MediaRecorder.AudioSource.MIC, false, false);
    }
}
