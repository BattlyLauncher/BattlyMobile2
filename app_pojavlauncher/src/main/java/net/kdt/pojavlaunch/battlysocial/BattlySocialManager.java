package net.kdt.pojavlaunch.battlysocial;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.List;

public final class BattlySocialManager {
    private static final String KEY_SERVER_HOST = "pending_server_host";
    private static final String KEY_SERVER_PORT = "pending_server_port";
    private static final String KEY_SERVER_NAME = "pending_server_name";
    private static final String KEY_SERVER_VERSION = "pending_server_version";
    private static long sLastLauncherHeartbeat;

    private BattlySocialManager() {
    }

    public static void heartbeatLauncher(Context context) {
        if (context == null || System.currentTimeMillis() - sLastLauncherHeartbeat < 30000L) return;
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
        BattlySocialApi.Server pendingServer = peekPendingServer(context);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlySocialApi.updatePresence(
                        context.getApplicationContext(),
                        "playing",
                        version == null ? "" : version,
                        pendingServer);
            } catch (Throwable ignored) {
            }
        });
    }

    public static void registerDeviceToken(Context context) {
        if (context == null) return;
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
        if (Tools.isValidString(version)) {
            LauncherProfiles.load();
            String selectedProfile = LauncherPreferences.DEFAULT_PREF
                    .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            MinecraftProfile profile = LauncherProfiles.mainProfileJson == null
                    ? null
                    : LauncherProfiles.mainProfileJson.profiles.get(selectedProfile);
            if (profile != null) {
                profile.lastVersionId = version;
                LauncherProfiles.write();
            }
        }
        Toast.makeText(activity, R.string.battly_social_join_preparing, Toast.LENGTH_LONG).show();
        new AsyncVersionList().getVersionList(versions -> {
            if (versions != null) ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions);
            Tools.MAIN_HANDLER.post(() -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        }, false);
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
            } catch (org.json.JSONException ignored) {
            }
        }
        prefs.edit()
                .remove(KEY_SERVER_HOST)
                .remove(KEY_SERVER_PORT)
                .remove(KEY_SERVER_NAME)
                .remove(KEY_SERVER_VERSION)
                .apply();
        return server;
    }
}
