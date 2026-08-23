package net.kdt.pojavlaunch.customcontrols.buttons;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsVoiceControls;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;

@SuppressLint("ViewConstructor")
public final class ControlVoiceToggleButton extends ControlButton {
    private static final long STATE_REFRESH_MS = 500;
    private static final int COLOR_ACTIVE = Color.rgb(132, 224, 195);
    private static final int COLOR_MUTED = Color.rgb(255, 139, 148);

    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final boolean microphone;
    private boolean pointerDown;
    private final Runnable stateRefresh = new Runnable() {
        @Override
        public void run() {
            updateState();
            stateHandler.postDelayed(this, STATE_REFRESH_MS);
        }
    };

    public ControlVoiceToggleButton(ControlLayout layout, ControlData properties) {
        super(layout, properties);
        microphone = properties.isVoiceMicrophoneWidget();
        setAllCaps(false);
        setSingleLine(true);
        setTextSize(12);
        setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        updateState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        stateHandler.post(stateRefresh);
    }

    @Override
    protected void onDetachedFromWindow() {
        stateHandler.removeCallbacks(stateRefresh);
        pointerDown = false;
        super.onDetachedFromWindow();
    }

    @Override
    public void sendKeyPresses(boolean isDown) {
        if (!isDown) {
            pointerDown = false;
            setPressed(false);
            return;
        }
        if (pointerDown) return;
        pointerDown = true;
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (microphone) BattlyWorldsVoiceControls.toggleMicrophone(getContext());
        else BattlyWorldsVoiceControls.toggleAudio(getContext());
        updateState();
    }

    @Override
    public boolean triggerToggle() {
        return false;
    }

    private void updateState() {
        boolean disabled = microphone
                ? BattlyWorldsVoiceControls.isMicrophoneMuted(getContext())
                : BattlyWorldsVoiceControls.isAudioMuted(getContext());
        setText(microphone
                ? getContext().getString(disabled
                        ? R.string.customctrl_voice_microphone_off
                        : R.string.customctrl_voice_microphone_on)
                : getContext().getString(disabled
                        ? R.string.customctrl_voice_audio_off
                        : R.string.customctrl_voice_audio_on));
        setTextColor(disabled ? COLOR_MUTED : COLOR_ACTIVE);
        setActivated(disabled);
        setContentDescription(getContext().getString(microphone
                ? (disabled ? R.string.battlyworlds_voice_unmute : R.string.battlyworlds_voice_mute)
                : (disabled ? R.string.battlyworlds_voice_undeafen : R.string.battlyworlds_voice_deafen)));
        invalidate();
    }
}
