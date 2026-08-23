package net.kdt.pojavlaunch.battlyworlds;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.R;

final class BattlyWorldsVoiceNotification {
    static final String ACTION_TOGGLE_MIC = "net.kdt.pojavlaunch.battlyworlds.voice.TOGGLE_MIC";
    static final String ACTION_TOGGLE_DEAFEN = "net.kdt.pojavlaunch.battlyworlds.voice.TOGGLE_DEAFEN";
    static final String ACTION_LEAVE = "net.kdt.pojavlaunch.battlyworlds.voice.LEAVE";
    private static final String CHANNEL_ID = "battlyworlds_voice";
    private static final int NOTIFICATION_ID = 7291;

    static void update(Context context, boolean joined, boolean joining,
                       boolean muted, boolean deafened) {
        if (context == null) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (!joined && !joining) {
            manager.cancel(NOTIFICATION_ID);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        ensureChannel(context, manager);
        PendingIntent content = PendingIntent.getActivity(context, 7291,
                new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(context.getString(R.string.battlyworlds_voice_notification_title))
                .setContentText(context.getString(joining
                        ? R.string.battlyworlds_voice_notification_connecting
                        : deafened ? R.string.battlyworlds_voice_notification_deafened
                        : muted ? R.string.battlyworlds_voice_notification_muted
                        : R.string.battlyworlds_voice_notification_connected))
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (joined) {
            builder.addAction(android.R.drawable.ic_btn_speak_now,
                    context.getString(muted ? R.string.battlyworlds_voice_unmute
                            : R.string.battlyworlds_voice_mute),
                    action(context, ACTION_TOGGLE_MIC, 7292));
            builder.addAction(android.R.drawable.ic_lock_silent_mode,
                    context.getString(deafened ? R.string.battlyworlds_voice_undeafen
                            : R.string.battlyworlds_voice_deafen),
                    action(context, ACTION_TOGGLE_DEAFEN, 7293));
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.battlyworlds_voice_leave),
                    action(context, ACTION_LEAVE, 7294));
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private static PendingIntent action(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, BattlyWorldsVoiceActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void ensureChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.battlyworlds_voice_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.battlyworlds_voice_notification_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    private BattlyWorldsVoiceNotification() {
    }
}
