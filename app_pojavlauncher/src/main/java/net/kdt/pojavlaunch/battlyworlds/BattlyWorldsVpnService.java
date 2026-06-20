package net.kdt.pojavlaunch.battlyworlds;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import net.burningtnt.terracotta.TerracottaAndroidAPI;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.NotificationUtils;

import java.io.IOException;

@SuppressLint("VpnServicePolicy")
public class BattlyWorldsVpnService extends VpnService {
    private static final String TAG = "BattlyWorldsVpnService";

    public static final String ACTION_START = "net.kdt.pojavlaunch.battlyworlds.START";
    public static final String ACTION_STOP = "net.kdt.pojavlaunch.battlyworlds.STOP";
    public static final String ACTION_UPDATE_STATE = "net.kdt.pojavlaunch.battlyworlds.UPDATE_STATE";
    public static final String EXTRA_STATE_TEXT = "state_text";

    private static volatile boolean sRunning;

    private ParcelFileDescriptor mVpnInterface;
    private String mStateText;

    public static boolean isRunning() {
        return sRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Tools.buildNotificationChannel(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            cleanup();
            stopForeground(true);
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        if (ACTION_UPDATE_STATE.equals(action)) {
            if (intent != null && intent.hasExtra(EXTRA_STATE_TEXT)) {
                mStateText = intent.getStringExtra(EXTRA_STATE_TEXT);
            }
            startForegroundCompat(buildNotification());
            return Service.START_STICKY;
        }

        startForegroundCompat(buildNotification());

        try {
            Builder builder = new Builder()
                    .setSession(getString(R.string.battlyworlds_vpn_session));
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            TerracottaAndroidAPI.VpnServiceRequest request = TerracottaAndroidAPI.getPendingVpnServiceRequest();
            mVpnInterface = request.startVpnService(builder);
        } catch (Throwable throwable) {
            Log.e(TAG, "Unable to start BattlyWorlds VPN", throwable);
            BattlyWorldsManager.setWaiting(this, false);
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        return Service.START_STICKY;
    }

    @Override
    public void onRevoke() {
        BattlyWorldsManager.setWaiting(this, false);
        cleanup();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private Notification buildNotification() {
        Intent contentIntent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingContentIntent = PendingIntent.getActivity(
                this,
                NotificationUtils.PENDINGINTENT_CODE_BATTLYWORLDS,
                contentIntent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        String stateText = mStateText == null ? getString(R.string.battlyworlds_state_starting) : mStateText;
        BattlyWorldsManager.Mode mode = BattlyWorldsManager.getMode();
        String modeText = mode == BattlyWorldsManager.Mode.HOST
                ? getString(R.string.battlyworlds_mode_host)
                : getString(R.string.battlyworlds_mode_guest);

        return new NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(getString(R.string.battlyworlds_notification_title))
                .setContentText(getString(R.string.battlyworlds_notification_text, modeText, stateText))
                .setContentIntent(pendingContentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setNotificationSilent()
                .build();
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NotificationUtils.NOTIFICATION_ID_BATTLYWORLDS,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(NotificationUtils.NOTIFICATION_ID_BATTLYWORLDS, notification);
        }
        sRunning = true;
    }

    private void cleanup() {
        if (mVpnInterface != null) {
            try {
                mVpnInterface.close();
            } catch (IOException ignored) {
            }
            mVpnInterface = null;
        }
        sRunning = false;
    }
}
