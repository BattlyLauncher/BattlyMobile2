package net.kdt.pojavlaunch.battlysocial;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.NotificationUtils;

import java.util.Map;

public final class BattlySocialNotifications {
    public static final String TYPE = "battly_social_invite";
    public static final String EVENT_TYPE = "battly_social_event";
    public static final String EXTRA_OPEN_SOCIAL = "battly_social_open";
    private static final String CHANNEL_ID = "battly_social";

    private BattlySocialNotifications() {
    }

    public static void dispatch(Context context, Map<String, String> data) {
        if (context == null) return;
        createChannel(context);
        String from = value(data, "fromUsername");
        String title = value(data, "title");
        String message = value(data, "message");
        if (!Tools.isValidString(title)) title = context.getString(R.string.battly_social_notification_title);
        if (!Tools.isValidString(message)) {
            message = context.getString(R.string.battly_social_notification_text, from);
        }
        Intent intent = new Intent(context, LauncherActivity.class)
                .putExtra(EXTRA_OPEN_SOCIAL, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                NotificationUtils.PENDINGINTENT_CODE_BATTLYWORLDS + 250,
                intent,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) (System.currentTimeMillis() & 0x0FFFFFFF), builder.build());
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.battly_social_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.battly_social_notification_channel_description));
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static String value(Map<String, String> data, String key) {
        String value = data == null ? null : data.get(key);
        return value == null ? "" : value.trim();
    }
}
