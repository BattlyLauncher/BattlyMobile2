package net.kdt.pojavlaunch.battlysocial;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.analytics.FirebaseProcessGuard;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsManager;
import net.kdt.pojavlaunch.customcontrols.MinecraftServerSessionTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BattlySocialManager {
    private static final String KEY_SERVER_HOST = "pending_server_host";
    private static final String KEY_SERVER_PORT = "pending_server_port";
    private static final String KEY_SERVER_NAME = "pending_server_name";
    private static final String KEY_SERVER_VERSION = "pending_server_version";
    private static final String KEY_ACTIVE_SERVER_HOST = "active_server_host";
    private static final String KEY_ACTIVE_SERVER_PORT = "active_server_port";
    private static final String KEY_ACTIVE_SERVER_NAME = "active_server_name";
    private static final long GAME_HEARTBEAT_INTERVAL_MS = 20000L;
    private static long sLastLauncherHeartbeat;
    private static Context sGameContext;
    private static String sGameVersion = "";
    private static final AtomicBoolean GAME_HEARTBEAT_IN_FLIGHT = new AtomicBoolean();
    private static final Runnable GAME_HEARTBEAT = new Runnable() {
        @Override
        public void run() {
            Context context = sGameContext;
            if (context == null) return;
            if (GAME_HEARTBEAT_IN_FLIGHT.compareAndSet(false, true)) {
                sendGameHeartbeat(context, sGameVersion);
            }
            Tools.MAIN_HANDLER.postDelayed(this, GAME_HEARTBEAT_INTERVAL_MS);
        }
    };

    private BattlySocialManager() {
    }

    public static void heartbeatLauncher(Context context) {
        if (context == null || !FirebaseProcessGuard.isLauncherProcess(context)
                || System.currentTimeMillis() - sLastLauncherHeartbeat < 30000L) return;
        if (BattlyWorldsInvites.isGameActive(context)) return;
        sLastLauncherHeartbeat = System.currentTimeMillis();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlySocialApi.updatePresence(context.getApplicationContext(), "online", "", null);
            } catch (Throwable ignored) {
            }
        });
        registerDeviceToken(context);
    }

    public static void heartbeatGame(Context context, String version) {
        if (context == null) return;
        sGameContext = context.getApplicationContext();
        sGameVersion = version == null ? "" : version;
        Tools.MAIN_HANDLER.removeCallbacks(GAME_HEARTBEAT);
        Tools.MAIN_HANDLER.post(GAME_HEARTBEAT);
    }

    public static void stopGameHeartbeat(Context context) {
        Tools.MAIN_HANDLER.removeCallbacks(GAME_HEARTBEAT);
        sGameContext = null;
        sGameVersion = "";
        GAME_HEARTBEAT_IN_FLIGHT.set(false);
        LauncherPreferences.DEFAULT_PREF.edit()
                .remove(KEY_ACTIVE_SERVER_HOST)
                .remove(KEY_ACTIVE_SERVER_PORT)
                .remove(KEY_ACTIVE_SERVER_NAME)
                .apply();
    }

    private static void sendGameHeartbeat(Context context, String version) {
        BattlySocialApi.Server server = currentServer(context);
        boolean battlyWorlds = BattlyWorldsManager.isRoomActive();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlySocialApi.updatePresence(
                        context.getApplicationContext(),
                        "playing",
                        version == null ? "" : version,
                        server,
                        battlyWorlds);
            } catch (Throwable ignored) {
            } finally {
                GAME_HEARTBEAT_IN_FLIGHT.set(false);
            }
        });
    }

    public static void registerDeviceToken(Context context) {
        if (context == null) return;
        if (!FirebaseProcessGuard.ensureInitialized(context)) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (!Tools.isValidString(token)) return;
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    BattlySocialApi.registerDeviceToken(context.getApplicationContext(), token);
                } catch (Throwable ignored) {
                }
            });
        });
    }

    public static void joinServer(Activity activity, BattlySocialApi.Server server, String version) {
        if (activity == null || server == null || !Tools.isValidString(server.host)) return;
        savePendingServer(activity, server, version);
        Toast.makeText(activity, R.string.battly_social_join_preparing, Toast.LENGTH_LONG).show();
        new AsyncVersionList().getVersionList(versions -> {
            if (versions != null) ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions);
            Tools.MAIN_HANDLER.post(() -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        }, false);
    }

    /** Returns the requested server version without modifying the selected instance. */
    public static String consumePendingLaunchVersion() {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return "";
        String version = prefs.getString(KEY_SERVER_VERSION, "");
        prefs.edit().remove(KEY_SERVER_VERSION).apply();
        return version == null ? "" : version.trim();
    }

    public static String[] appendPendingServerArgs(String[] originalArgs) {
        BattlySocialApi.Server server = consumePendingServer();
        if (server == null || !Tools.isValidString(server.host)) return originalArgs;
        List<String> args = new ArrayList<>();
        if (originalArgs != null) java.util.Collections.addAll(args, originalArgs);
        args.add("--server");
        args.add(server.host);
        args.add("--port");
        args.add(String.valueOf(server.port));
        return args.toArray(new String[0]);
    }

    private static void savePendingServer(Context context, BattlySocialApi.Server server, String version) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(KEY_SERVER_HOST, server.host)
                .putInt(KEY_SERVER_PORT, server.port)
                .putString(KEY_SERVER_NAME, server.name)
                .putString(KEY_SERVER_VERSION, version == null ? "" : version)
                .apply();
    }

    private static BattlySocialApi.Server peekPendingServer(Context context) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        String host = prefs.getString(KEY_SERVER_HOST, "");
        if (!Tools.isValidString(host)) return null;
        try {
            org.json.JSONObject json = new org.json.JSONObject()
                    .put("host", host)
                    .put("port", prefs.getInt(KEY_SERVER_PORT, 25565))
                    .put("name", prefs.getString(KEY_SERVER_NAME, host))
                    .put("joinable", true);
            return new BattlySocialApi.Server(json);
        } catch (org.json.JSONException ignored) {
            return null;
        }
    }

    private static BattlySocialApi.Server consumePendingServer() {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        String host = prefs.getString(KEY_SERVER_HOST, "");
        BattlySocialApi.Server server = null;
        if (Tools.isValidString(host)) {
            try {
                org.json.JSONObject json = new org.json.JSONObject()
                        .put("host", host)
                        .put("port", prefs.getInt(KEY_SERVER_PORT, 25565))
                        .put("name", prefs.getString(KEY_SERVER_NAME, host))
                        .put("joinable", true);
                server = new BattlySocialApi.Server(json);
                prefs.edit()
                        .putString(KEY_ACTIVE_SERVER_HOST, host)
                        .putInt(KEY_ACTIVE_SERVER_PORT, server.port)
                        .putString(KEY_ACTIVE_SERVER_NAME, server.name)
                        .apply();
            } catch (org.json.JSONException ignored) {
            }
        }
        prefs.edit()
                .remove(KEY_SERVER_HOST)
                .remove(KEY_SERVER_PORT)
                .remove(KEY_SERVER_NAME)
                .apply();
        return server;
    }

    private static BattlySocialApi.Server currentServer(Context context) {
        MinecraftServerSessionTracker.Endpoint endpoint = MinecraftServerSessionTracker.getEndpoint();
        if (endpoint != null && Tools.isValidString(endpoint.host)) {
            return server(endpoint.host, endpoint.port, endpoint.host);
        }
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        String host = prefs.getString(KEY_ACTIVE_SERVER_HOST, "");
        if (!Tools.isValidString(host)) return peekPendingServer(context);
        return server(host, prefs.getInt(KEY_ACTIVE_SERVER_PORT, 25565),
                prefs.getString(KEY_ACTIVE_SERVER_NAME, host));
    }

    private static BattlySocialApi.Server server(String host, int port, String name) {
        try {
            return new BattlySocialApi.Server(new org.json.JSONObject()
                    .put("host", host)
                    .put("port", port)
                    .put("name", name)
                    .put("joinable", true));
        } catch (org.json.JSONException ignored) {
            return null;
        }
    }
}
