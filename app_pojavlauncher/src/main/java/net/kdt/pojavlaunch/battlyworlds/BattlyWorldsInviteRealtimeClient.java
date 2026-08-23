package net.kdt.pojavlaunch.battlyworlds;

import android.app.Application;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;

/** Keeps Battly Worlds invitations live while the launcher and game processes swap. */
final class BattlyWorldsInviteRealtimeClient {
    private static final String TAG = "BattlyWorldsInvitesRT";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long STOP_GRACE_MS = 12_000L;

    private static Context appContext;
    private static Socket socket;
    private static boolean foreground;
    private static final Runnable DELAYED_STOP = () -> {
        synchronized (BattlyWorldsInviteRealtimeClient.class) {
            if (!foreground) disconnectLocked();
        }
    };

    static synchronized void start(Context context) {
        if (context == null || !BattlyWorldsFeature.ENABLED
                || !BattlyWorldsPreferences.areInvitationsEnabled(context)) return;
        String token = BattlyWorldsInvites.getBattlyToken(context);
        if (token.isEmpty()) {
            disconnectLocked();
            return;
        }
        appContext = context.getApplicationContext();
        foreground = true;
        MAIN.removeCallbacks(DELAYED_STOP);
        if (socket != null) {
            if (!socket.connected()) socket.connect();
            return;
        }
        try {
            Map<String, String> auth = new HashMap<>();
            auth.put("token", token);
            auth.put("client", "battly-mobile");
            auth.put("version", "2.2");
            IO.Options options = IO.Options.builder()
                    .setAuth(auth)
                    .setReconnection(true)
                    .setReconnectionAttempts(Integer.MAX_VALUE)
                    .setReconnectionDelay(750)
                    .setTimeout(12_000)
                    .build();
            Socket target = IO.socket("https://api.battlylauncher.com", options);
            socket = target;
            target.on(Socket.EVENT_CONNECT, args -> {
                Log.i(TAG, "Invitation realtime connection ready");
                announcePresence(target);
            });
            target.on("battlyworlds_invite", args -> {
                if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
                Log.i(TAG, "Invitation received in realtime");
                dispatch((JSONObject) args[0]);
            });
            target.on(Socket.EVENT_CONNECT_ERROR, args ->
                    Log.w(TAG, "Invitation realtime connection failed"));
            target.connect();
        } catch (URISyntaxException error) {
            Log.e(TAG, "Invalid realtime URL", error);
        }
    }

    static synchronized void stopAfterTransition() {
        foreground = false;
        MAIN.removeCallbacks(DELAYED_STOP);
        MAIN.postDelayed(DELAYED_STOP, STOP_GRACE_MS);
    }

    private static void announcePresence(Socket target) {
        if (target != socket) return;
        JSONObject payload = new JSONObject();
        try {
            boolean gameProcess = isGameProcess();
            payload.put("status", gameProcess
                    ? "ausente" : "online");
            payload.put("details", gameProcess
                    ? "Battly Worlds" : "Battly Mobile");
            target.emit("updateStatus-v3", payload);
            target.emit("session-handshake-v3");
        } catch (Exception error) {
            Log.w(TAG, "Unable to announce invitation presence", error);
        }
    }

    private static boolean isGameProcess() {
        String processName = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            processName = Application.getProcessName();
        } else if (appContext != null) {
            ActivityManager manager = (ActivityManager) appContext.getSystemService(
                    Context.ACTIVITY_SERVICE);
            if (manager != null) {
                for (ActivityManager.RunningAppProcessInfo process
                        : manager.getRunningAppProcesses()) {
                    if (process.pid == Process.myPid()) {
                        processName = process.processName;
                        break;
                    }
                }
            }
        }
        return processName != null && processName.endsWith(":game");
    }

    private static void dispatch(JSONObject invite) {
        Context context = appContext;
        if (context == null) return;
        Map<String, String> data = new HashMap<>();
        data.put(BattlyWorldsInvites.EXTRA_TYPE, BattlyWorldsInvites.TYPE);
        data.put(BattlyWorldsInvites.EXTRA_CODE, invite.optString("code", ""));
        data.put(BattlyWorldsInvites.EXTRA_FROM,
                invite.optString("from", invite.optString("fromUsername", "")));
        data.put(BattlyWorldsInvites.EXTRA_VERSION, invite.optString("version", ""));
        data.put(BattlyWorldsInvites.EXTRA_INVITE_ID, invite.optString("inviteId", ""));
        BattlyWorldsInvites.dispatchRemoteInvite(context, data);
    }

    private static void disconnectLocked() {
        MAIN.removeCallbacks(DELAYED_STOP);
        if (socket != null) {
            socket.off();
            socket.disconnect();
            socket = null;
        }
        appContext = null;
    }

    private BattlyWorldsInviteRealtimeClient() { }
}
