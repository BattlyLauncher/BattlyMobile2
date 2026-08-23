package net.kdt.pojavlaunch.battlyworlds;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.MediaRecorder;

import org.junit.Test;

public class BattlyWorldsVoiceAudioPolicyTest {
    @Test
    public void builtInMicrophoneAvoidsBrokenVendorVoiceProcessing() {
        BattlyWorldsVoiceAudioPolicy policy = BattlyWorldsVoiceAudioPolicy.forInput(false);

        assertTrue(policy.audioSource == MediaRecorder.AudioSource.MIC);
        assertFalse(policy.hardwareEchoCanceler);
        assertFalse(policy.hardwareNoiseSuppressor);
    }

    @Test
    public void externalMicrophoneKeepsCommunicationProcessing() {
        BattlyWorldsVoiceAudioPolicy policy = BattlyWorldsVoiceAudioPolicy.forInput(true);

        assertTrue(policy.audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        assertTrue(policy.hardwareEchoCanceler);
        assertTrue(policy.hardwareNoiseSuppressor);
    }
}
