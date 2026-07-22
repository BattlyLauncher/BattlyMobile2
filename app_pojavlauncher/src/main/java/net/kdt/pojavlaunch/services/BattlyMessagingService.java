package net.kdt.pojavlaunch.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.battlysocial.BattlySocialNotifications;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsFeature;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites;
import net.kdt.pojavlaunch.utils.BattlyInAppMessaging;

public class BattlyMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Telemetry.saveMessagingToken(this, token);
        Telemetry.logMessagingTokenRefresh();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Telemetry.logMessageReceived(remoteMessage.getFrom());

        if (BattlyWorldsInvites.TYPE.equals(remoteMessage.getData().get(BattlyWorldsInvites.EXTRA_TYPE))) {
            if (!BattlyWorldsFeature.ENABLED) {
                return;
            }
            BattlyWorldsInvites.dispatchRemoteInvite(this, remoteMessage.getData());
            return;
        }
        String socialType = remoteMessage.getData().get("type");
        if (BattlySocialNotifications.TYPE.equals(socialType)
                || BattlySocialNotifications.EVENT_TYPE.equals(socialType)) {
            BattlySocialNotifications.dispatch(this, remoteMessage.getData());
            return;
        }

        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (BattlyInAppMessaging.isInAppMessage(remoteMessage.getData())) {
            BattlyInAppMessaging.dispatch(
                    this,
                    remoteMessage.getData(),
                    notification == null ? null : notification.getTitle(),
                    notification == null ? null : notification.getBody());
            return;
        }

        if (notification == null || notification.getTitle() == null) {
            return;
        }

        Tools.buildNotificationChannel(this);
        Intent launcherIntent = new Intent(this, LauncherActivity.class);
        launcherIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launcherIntent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(notification.getTitle())
                .setContentText(notification.getBody())
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
