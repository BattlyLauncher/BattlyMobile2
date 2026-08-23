package net.kdt.pojavlaunch.battlyworlds;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BattlyWorldsVoiceActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (BattlyWorldsVoiceNotification.ACTION_TOGGLE_MIC.equals(action)) {
            BattlyWorldsVoiceManager.setMuted(!BattlyWorldsVoiceManager.isMuted());
        } else if (BattlyWorldsVoiceNotification.ACTION_TOGGLE_DEAFEN.equals(action)) {
            BattlyWorldsVoiceManager.setDeafened(!BattlyWorldsVoiceManager.isDeafened());
        } else if (BattlyWorldsVoiceNotification.ACTION_LEAVE.equals(action)) {
            BattlyWorldsVoiceManager.leave();
        }
    }
}
