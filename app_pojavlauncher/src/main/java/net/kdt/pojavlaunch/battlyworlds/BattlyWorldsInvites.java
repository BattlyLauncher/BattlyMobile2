package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.firebase.messaging.FirebaseMessaging;

import net.burningtnt.terracotta.TerracottaAndroidAPI;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public final class BattlyWorldsInvites {
    public static final String TYPE = "battlyworlds_invite";
    public static final String ACTION_INVITE_RECEIVED = "net.kdt.pojavlaunch.battlyworlds.INVITE_RECEIVED";

    private static final String API_BASE = "https://api.battlylauncher.com";
    private static final String PREFS = "battlyworlds_invites";
    private static final String PREF_GAME_ACTIVE = "game_active";
    private static final String PREF_PENDING_CODE = "pending_code";
    private static final String PREF_PENDING_FROM = "pending_from";
    private static final String PREF_PENDING_VERSION = "pending_version";
    private static final String PREF_SEEN_INVITES = "seen_invites";
    private static final String INVITE_CHANNEL_ID = "battlyworlds_invites_high";
    // FCM delivers invites immediately. Polling is only a foreground fallback.
    private static final long INVITE_POLL_INTERVAL_MS = 5 * 60_000L;
    private static volatile long sLastHeartbeat;
    private static volatile long sLastInvitePoll;
    private static volatile Entitlements sEntitlements = Entitlements.free();
    private static volatile Context sInvitePollContext;
    private static volatile boolean sInvitePollScheduled;
    private static volatile Context sPublicRoomContext;
    private static volatile String sPublicRoomCode = "";
    private static final long PUBLIC_ROOM_HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final long USAGE_EVENT_DEBOUNCE_MS = 10_000L;
    private static final Map<String, Long> LAST_USAGE_EVENTS = new ConcurrentHashMap<>();
    private static final Runnable PUBLIC_ROOM_HEARTBEAT = new Runnable() {
        @Override
        public void run() {
            Context context = sPublicRoomContext;
            String code = sPublicRoomCode;
            if (context == null || !looksLikeShortCode(code)) return;
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    heartbeatPublicRoom(context, code);
                } catch (Throwable throwable) {
                    Log.w("BattlyWorlds", "Public room heartbeat failed", throwable);
                } finally {
                    if (code.equals(sPublicRoomCode)) {
                        Tools.MAIN_HANDLER.postDelayed(PUBLIC_ROOM_HEARTBEAT,
                                PUBLIC_ROOM_HEARTBEAT_INTERVAL_MS);
                    }
                }
            });
        }
    };
    private static final AtomicBoolean INVITE_POLL_IN_FLIGHT = new AtomicBoolean();
    private static final Runnable INVITE_POLL = new Runnable() {
        @Override
        public void run() {
            Context context = sInvitePollContext;
            if (context == null || !BattlyWorldsFeature.ENABLED) {
                sInvitePollScheduled = false;
                return;
            }
            long now = System.currentTimeMillis();
            long elapsed = now - sLastInvitePoll;
            if ((sLastInvitePoll == 0L || elapsed >= INVITE_POLL_INTERVAL_MS)
                    && INVITE_POLL_IN_FLIGHT.compareAndSet(false, true)) {
                sLastInvitePoll = now;
                new Thread(() -> {
                    try {
                        pollPendingInvites(context);
                    } finally {
                        INVITE_POLL_IN_FLIGHT.set(false);
                    }
                }, "BattlyWorlds Invite Poll").start();
            }
            long delay = sLastInvitePoll == 0L
                    ? INVITE_POLL_INTERVAL_MS
                    : Math.max(1_000L, INVITE_POLL_INTERVAL_MS
                    - (System.currentTimeMillis() - sLastInvitePoll));
            Tools.MAIN_HANDLER.postDelayed(this, delay);
        }
    };

    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_FROM = "from";
    public static final String EXTRA_VERSION = "version";
    public static final String EXTRA_INVITE_ID = "inviteId";

    public static class Friend {
        public final String username;
        public final String state;

        Friend(String username, String state) {
            this.username = username;
            this.state = state;
        }
    }

    public static class UserSearchResult {
        public final String username;

        UserSearchResult(String username) {
            this.username = username;
        }
    }

    public static final class InviteLimitException extends Exception {
        public final int inviteCount;
        public final int maxInvites;
        public final int rewardedInvitesRemaining;
        public final boolean canUnlockWithAd;

        InviteLimitException(String message, int inviteCount, int maxInvites,
                             int rewardedInvitesRemaining, boolean canUnlockWithAd) {
            super(message);
            this.inviteCount = inviteCount;
            this.maxInvites = maxInvites;
            this.rewardedInvitesRemaining = rewardedInvitesRemaining;
            this.canUnlockWithAd = canUnlockWithAd;
        }
    }

    public static class Entitlements {
        public final boolean plus;
        public final String tier;
        public final String priority;
        public final boolean persistentRooms;
        public final boolean unlimitedInvites;
        public final int maxInvites;
        public final int maxGuests;
        public final int roomDurationHours;

        Entitlements(boolean plus, String tier, String priority, boolean persistentRooms,
                     boolean unlimitedInvites,
                     int maxInvites, int maxGuests, int roomDurationHours) {
            this.plus = plus;
            this.tier = safe(tier).isEmpty() ? "free" : tier;
            this.priority = safe(priority).isEmpty() ? "standard" : priority;
            this.persistentRooms = persistentRooms;
            this.unlimitedInvites = unlimitedInvites;
            this.maxInvites = this.unlimitedInvites ? 0
                    : (maxInvites <= 0 ? (plus ? 50 : 5) : maxInvites);
            this.maxGuests = maxGuests <= 0 ? 3 : maxGuests;
            this.roomDurationHours = roomDurationHours <= 0 ? 6 : roomDurationHours;
        }

        public static Entitlements free() {
            return new Entitlements(false, "free", "standard", false, false, 5, 3, 6);
        }
    }

    public static class Invite {
        public final String code;
        public final String from;
        public final String version;
        public final String inviteId;

        Invite(String code, String from, String version, String inviteId) {
            this.code = safe(code);
            this.from = safe(from);
            this.version = safe(version);
            this.inviteId = safe(inviteId);
        }

        boolean isValid() {
            return !code.isEmpty();
        }
    }

    public static void setGameActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(PREF_GAME_ACTIVE, active).commit();
    }

    public static boolean isBattlyLoggedIn(Context context) {
        return !getBattlyToken(context).isEmpty();
    }

    public static void registerDeviceToken(Context context) {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        String battlyToken = getBattlyToken(context);
        String messagingToken = context.getApplicationContext()
                .getSharedPreferences(Telemetry.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(Telemetry.PREF_FCM_TOKEN, "");
        if (battlyToken.isEmpty()) {
            return;
        }
        if (messagingToken == null || messagingToken.trim().isEmpty()) {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                if (Tools.isValidString(token)) {
                    Telemetry.saveMessagingToken(context, token);
                    registerDeviceToken(context, token);
                }
            });
            return;
        }

        registerDeviceToken(context, messagingToken);
    }

    private static void registerDeviceToken(Context context, String messagingToken) {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        String battlyToken = getBattlyToken(context);
        if (battlyToken.isEmpty() || messagingToken == null || messagingToken.trim().isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("token", messagingToken);
                body.put("platform", "android");
                postJson(API_BASE + "/api/v2/battlyworlds/device-token", battlyToken, body);
            } catch (Throwable ignored) {
            }
        }, "BattlyWorlds Register FCM").start();
    }

    public static void heartbeat(Context context) {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        String battlyToken = getBattlyToken(context);
        if (battlyToken.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - sLastHeartbeat < 60000L) {
            return;
        }
        sLastHeartbeat = now;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("platform", "android");
                body.put("clientVersion", "mobile");
                updateEntitlements(postJson(API_BASE + "/api/v2/battlyworlds/heartbeat", battlyToken, body));
            } catch (Throwable ignored) {
            }
        }, "BattlyWorlds Heartbeat").start();
        registerDeviceToken(context);
    }

    public static synchronized void startInvitePolling(Context context) {
        if (!BattlyWorldsFeature.ENABLED || context == null
                || !BattlyWorldsPreferences.areInvitationsEnabled(context)) {
            return;
        }
        sInvitePollContext = context.getApplicationContext();
        if (sInvitePollScheduled) {
            return;
        }
        sInvitePollScheduled = true;
        Tools.MAIN_HANDLER.post(INVITE_POLL);
    }

    public static synchronized void stopInvitePolling() {
        sInvitePollContext = null;
        sInvitePollScheduled = false;
        Tools.MAIN_HANDLER.removeCallbacks(INVITE_POLL);
    }

    public static Entitlements getCachedEntitlements() {
        return sEntitlements == null ? Entitlements.free() : sEntitlements;
    }

    public static Entitlements refreshEntitlements(Context context) {
        if (!BattlyWorldsFeature.ENABLED) {
            sEntitlements = Entitlements.free();
            return sEntitlements;
        }
        String battlyToken = getBattlyToken(context);
        if (battlyToken.isEmpty()) {
            sEntitlements = Entitlements.free();
            return sEntitlements;
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + "/api/v2/battlyworlds/entitlements").openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + battlyToken);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            try (InputStream inputStream = connection.getResponseCode() >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream()) {
                updateEntitlements(Tools.GLOBAL_GSON.fromJson(readFully(inputStream), JsonObject.class));
            } finally {
                connection.disconnect();
            }
        } catch (Throwable ignored) {
        }
        return getCachedEntitlements();
    }

    public static void dispatchRemoteInvite(Context context, Map<String, String> data) {
        if (!BattlyWorldsFeature.ENABLED || !BattlyWorldsPreferences.areInvitationsEnabled(context)) {
            return;
        }
        Invite invite = fromMap(data);
        if (!invite.isValid()) {
            return;
        }
        if (!markInviteSeen(context, invite.inviteId)) {
            acknowledgeInvite(context, invite.inviteId);
            return;
        }
        Intent broadcast = toIntent(new Intent(ACTION_INVITE_RECEIVED), invite);
        broadcast.setPackage(context.getPackageName());
        context.sendBroadcast(broadcast);
        if (!prefs(context).getBoolean(PREF_GAME_ACTIVE, false)) {
            postInviteNotification(context, invite);
        }
        acknowledgeInvite(context, invite.inviteId);
    }

    private static void pollPendingInvites(Context context) {
        String token = getBattlyToken(context);
        if (token.isEmpty()) {
            return;
        }
        try {
            JsonObject response = getAuthorizedJson(
                    API_BASE + "/api/v2/battlyworlds/invites/pending", token);
            if (response == null || !response.has("invites") || !response.get("invites").isJsonArray()) {
                return;
            }
            for (JsonElement element : response.getAsJsonArray("invites")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                Map<String, String> data = new java.util.HashMap<>();
                data.put(EXTRA_TYPE, TYPE);
                data.put(EXTRA_CODE, stringValue(object, EXTRA_CODE));
                data.put(EXTRA_FROM, stringValue(object, "fromUsername"));
                data.put(EXTRA_VERSION, stringValue(object, EXTRA_VERSION));
                data.put(EXTRA_INVITE_ID, stringValue(object, EXTRA_INVITE_ID));
                dispatchRemoteInvite(context, data);
            }
        } catch (Throwable throwable) {
            Log.w("BattlyWorldsInvites", "Unable to poll pending invites", throwable);
        }
    }

    private static void acknowledgeInvite(Context context, String inviteId) {
        if (safe(inviteId).isEmpty()) {
            return;
        }
        String token = getBattlyToken(context);
        if (token.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                postJson(API_BASE + "/api/v2/battlyworlds/invites/"
                        + URLEncoder.encode(inviteId, "UTF-8") + "/received", token, new JSONObject());
            } catch (Throwable throwable) {
                Log.w("BattlyWorldsInvites", "Unable to acknowledge invite", throwable);
            }
        }, "BattlyWorlds Invite Ack").start();
    }

    private static synchronized boolean markInviteSeen(Context context, String inviteId) {
        String id = safe(inviteId);
        if (id.isEmpty()) {
            return true;
        }
        SharedPreferences preferences = prefs(context);
        Set<String> seen = new LinkedHashSet<>(preferences.getStringSet(
                PREF_SEEN_INVITES, java.util.Collections.emptySet()));
        if (!seen.add(id)) {
            return false;
        }
        while (seen.size() > 40) {
            seen.remove(seen.iterator().next());
        }
        preferences.edit().putStringSet(PREF_SEEN_INVITES, seen).apply();
        return true;
    }

    private static String stringValue(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    public static BroadcastReceiver createGameInviteReceiver(MainActivity activity) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!BattlyWorldsPreferences.areInvitationsEnabled(context)) return;
                Invite invite = fromIntent(intent);
                if (invite.isValid()) {
                    showInGameInvite(activity, invite);
                }
            }
        };
    }

    public static IntentFilter inviteIntentFilter() {
        return new IntentFilter(ACTION_INVITE_RECEIVED);
    }

    public static boolean handleLauncherIntent(LauncherActivity activity, Intent intent) {
        if (!BattlyWorldsPreferences.areInvitationsEnabled(activity)) return false;
        Invite invite = fromIntent(intent);
        if (!invite.isValid()) {
            return false;
        }
        showLauncherInvite(activity, invite);
        return true;
    }

    public static void joinPendingIfAny(MainActivity activity) {
        if (!BattlyWorldsFeature.ENABLED) {
            prefs(activity).edit()
                    .remove(PREF_PENDING_CODE)
                    .remove(PREF_PENDING_FROM)
                    .remove(PREF_PENDING_VERSION)
                    .apply();
            return;
        }
        SharedPreferences prefs = prefs(activity);
        String code = prefs.getString(PREF_PENDING_CODE, "");
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        Invite invite = new Invite(
                code,
                prefs.getString(PREF_PENDING_FROM, ""),
                prefs.getString(PREF_PENDING_VERSION, ""),
                ""
        );
        prefs.edit()
                .remove(PREF_PENDING_CODE)
                .remove(PREF_PENDING_FROM)
                .remove(PREF_PENDING_VERSION)
                .apply();
        Tools.MAIN_HANDLER.postDelayed(() -> startGuest(activity, invite), 1200);
    }

    public static List<Friend> fetchFriends(Context context) throws Exception {
        if (!BattlyWorldsFeature.ENABLED) {
            return new ArrayList<>();
        }
        heartbeat(context);
        JsonObject response = postJson(
                API_BASE + "/api/v2/users/obtenerAmigos",
                getBattlyToken(context),
                new JSONObject()
        );
        JsonArray friends = response.has("amigos") && response.get("amigos").isJsonArray()
                ? response.getAsJsonArray("amigos")
                : new JsonArray();
        List<Friend> result = new ArrayList<>();
        for (JsonElement element : friends) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject friend = element.getAsJsonObject();
            String username = friend.has("username") ? friend.get("username").getAsString() : "";
            if (username.trim().isEmpty()) {
                continue;
            }
            String state = friend.has("estado") ? friend.get("estado").getAsString() : "offline";
            result.add(new Friend(username, state));
        }
        return result;
    }

    public static List<UserSearchResult> searchUsers(Context context, String query) throws Exception {
        if (!BattlyWorldsFeature.ENABLED) {
            return new ArrayList<>();
        }
        heartbeat(context);
        JSONObject body = new JSONObject();
        body.put("username", safe(query));
        JsonObject response = postJson(
                API_BASE + "/api/v2/users/buscarUsuarios",
                getBattlyToken(context),
                body
        );
        if (response.has("status") && response.get("status").getAsInt() != 200) {
            String message = response.has("message") ? response.get("message").getAsString()
                    : response.has("error") ? response.get("error").getAsString()
                    : "Unable to search users";
            throw new IllegalStateException(message);
        }

        JsonArray users = response.has("usuarios") && response.get("usuarios").isJsonArray()
                ? response.getAsJsonArray("usuarios")
                : new JsonArray();
        List<UserSearchResult> result = new ArrayList<>();
        for (JsonElement element : users) {
            String username = element.isJsonPrimitive() ? element.getAsString() : "";
            if (!safe(username).isEmpty()) {
                result.add(new UserSearchResult(username));
            }
        }
        return result;
    }

    public static void sendFriendRequest(Context context, String username) throws Exception {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        heartbeat(context);
        JSONObject body = new JSONObject();
        body.put("amigo", safe(username));
        JsonObject response = postJson(
                API_BASE + "/api/v2/users/enviarSolicitud",
                getBattlyToken(context),
                body
        );
        if (response.has("status") && response.get("status").getAsInt() == 200) {
            return;
        }
        String message = response.has("message") ? response.get("message").getAsString()
                : response.has("error") ? response.get("error").getAsString()
                : "Unable to send friend request";
        throw new IllegalStateException(message);
    }

    public static void sendInvite(Context context, String friendUsername, String roomCode, String version) throws Exception {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        heartbeat(context);
        JSONObject body = new JSONObject();
        body.put("friendUsername", friendUsername);
        body.put("code", roomCode);
        body.put("version", version);
        body.put("hostPlayer", getPlayerName(context));
        JsonObject response = postJson(API_BASE + "/api/v2/battlyworlds/invite", getBattlyToken(context), body);
        if (response.has("status") && response.get("status").getAsInt() == 200) {
            return;
        }
        String message = response.has("message") ? response.get("message").getAsString()
                : response.has("error") ? response.get("error").getAsString()
                : "Unable to send invite";
        if ("BATTLYWORLDS_INVITE_LIMIT".equals(jsonString(response, "code"))) {
            throw new InviteLimitException(
                    message,
                    jsonInt(response, "inviteCount", 5),
                    jsonInt(response, "maxInvites", 5),
                    jsonInt(response, "rewardedInvitesRemaining", 0),
                    jsonBoolean(response, "canUnlockWithAd", false)
            );
        }
        throw new IllegalStateException(message);
    }

    public static void unlockRewardedInvite(Context context, String roomCode) throws Exception {
        String code = safe(roomCode).toUpperCase(java.util.Locale.ROOT);
        if (!looksLikeShortCode(code)) {
            throw new IllegalArgumentException("Invalid Battly Worlds room code");
        }
        JsonObject response = postJson(
                API_BASE + "/api/v2/battlyworlds/rooms/"
                        + URLEncoder.encode(code, StandardCharsets.UTF_8.name())
                        + "/rewarded-invite",
                requireBattlyToken(context),
                new JSONObject()
        );
        if (jsonInt(response, "status", 500) != 200) {
            throw new IllegalStateException(jsonError(response, "Unable to unlock rewarded invite"));
        }
    }

    public static void trackUsage(Context context, String event, String source) {
        if (context == null) return;
        String eventKey = safe(event) + ":" + safe(source);
        long now = SystemClock.elapsedRealtime();
        Long previous = LAST_USAGE_EVENTS.put(eventKey, now);
        if (previous != null && now - previous < USAGE_EVENT_DEBOUNCE_MS) return;
        Context appContext = context.getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String token = getBattlyToken(appContext);
                if (token.isEmpty()) return;
                JSONObject body = new JSONObject();
                body.put("event", safe(event));
                body.put("source", safe(source));
                body.put("minecraftVersion", getCurrentVersion());
                body.put("platform", "android");
                body.put("clientVersion", BuildConfig.VERSION_NAME);
                postJson(API_BASE + "/api/v2/battlyworlds/stats/event", token, body);
            } catch (Throwable throwable) {
                Log.d("BattlyWorlds", "Usage event was not recorded: " + throwable.getMessage());
            }
        });
    }

    public static String createShortRoomCode(Context context, String fullCode, String version) throws Exception {
        String token = requireBattlyToken(context);
        Entitlements entitlements = refreshEntitlements(context);
        JSONObject body = new JSONObject();
        body.put("fullCode", safe(fullCode));
        body.put("version", safe(version));
        body.put("hostPlayer", getPlayerName(context));
        body.put("persistent", true);
        body.put("durationHours", BattlyWorldsPreferences.getDefaultDurationHours(context,
                entitlements.roomDurationHours));
        JsonObject response = postJson(API_BASE + "/api/v2/battlyworlds/rooms", token, body);
        updateEntitlements(response);
        if (response.has("status") && response.get("status").getAsInt() == 200 && response.has("shortCode")) {
            String code = response.get("shortCode").getAsString();
            BattlyWorldsPreferences.setActiveRoomCode(context, code);
            return code;
        }
        String message = response.has("message") ? response.get("message").getAsString()
                : response.has("error") ? response.get("error").getAsString()
                : "Unable to create room code";
        throw new IllegalStateException(message);
    }

    public static void closeRoom(Context context, String code) throws Exception {
        String normalized = safe(code).toUpperCase();
        if (normalized.isEmpty()) return;
        JsonObject response = postJson(API_BASE + "/api/v2/battlyworlds/rooms/"
                + URLEncoder.encode(normalized, StandardCharsets.UTF_8.name()) + "/close",
                requireBattlyToken(context), new JSONObject());
        if (!response.has("status") || response.get("status").getAsInt() != 200) {
            throw new IllegalStateException(jsonError(response, "Unable to close room"));
        }
        stopPublicRoomHeartbeat();
        BattlyWorldsPreferences.clearActiveRoomCode(context);
    }

    public static List<PublicRoom> getPublicRooms(Context context) throws Exception {
        JsonObject response = getAuthorizedJson(
                API_BASE + "/api/v2/battlyworlds/rooms/public?page=1&pageSize=50",
                requireBattlyToken(context));
        List<PublicRoom> rooms = new ArrayList<>();
        if (response == null || !response.has("status") || response.get("status").getAsInt() != 200) {
            throw new IllegalStateException(jsonError(response, "Unable to load public rooms"));
        }
        if (!response.has("rooms") || !response.get("rooms").isJsonArray()) {
            throw new IllegalStateException("Invalid public rooms response");
        }
        response.getAsJsonArray("rooms").forEach(element -> {
            if (!element.isJsonObject()) return;
            JsonObject room = element.getAsJsonObject();
            String code = jsonString(room, "code");
            if (!looksLikeShortCode(code)) return;
            rooms.add(new PublicRoom(
                    code,
                    jsonString(room, "title"),
                    first(jsonString(room, "hostUsername"), jsonString(room, "hostPlayer")),
                    jsonString(room, "version"),
                    room.has("premium") && room.get("premium").getAsBoolean(),
                    room.has("maxGuests") ? room.get("maxGuests").getAsInt() : 3
            ));
        });
        return rooms;
    }

    public static void setRoomPublic(Context context, String code, boolean isPublic, String title) throws Exception {
        String token = getBattlyToken(context);
        if (token.isEmpty()) throw new IllegalStateException(context.getString(R.string.battlyworlds_public_login_required));
        JSONObject body = new JSONObject();
        body.put("isPublic", isPublic);
        body.put("title", safe(title));
        JsonObject response = requestJson(
                "PATCH",
                API_BASE + "/api/v2/battlyworlds/rooms/"
                        + URLEncoder.encode(code, StandardCharsets.UTF_8.name()) + "/visibility",
                token,
                body);
        if (!response.has("status") || response.get("status").getAsInt() != 200) {
            throw new IllegalStateException(jsonError(response, "Unable to update room visibility"));
        }
    }

    public static void heartbeatPublicRoom(Context context, String code) throws Exception {
        String token = getBattlyToken(context);
        if (token.isEmpty() || !looksLikeShortCode(code)) return;
        JsonObject response = postJson(
                API_BASE + "/api/v2/battlyworlds/rooms/"
                        + URLEncoder.encode(code, StandardCharsets.UTF_8.name()) + "/heartbeat",
                token,
                new JSONObject());
        if (!response.has("status") || response.get("status").getAsInt() != 200) {
            throw new IllegalStateException(jsonError(response, "Unable to keep public room active"));
        }
    }

    public static void startPublicRoomHeartbeat(Context context, String code) {
        stopPublicRoomHeartbeat();
        if (context == null || !looksLikeShortCode(code)) return;
        sPublicRoomContext = context.getApplicationContext();
        sPublicRoomCode = code.toUpperCase();
        Tools.MAIN_HANDLER.post(PUBLIC_ROOM_HEARTBEAT);
    }

    public static void stopPublicRoomHeartbeat() {
        sPublicRoomCode = "";
        sPublicRoomContext = null;
        Tools.MAIN_HANDLER.removeCallbacks(PUBLIC_ROOM_HEARTBEAT);
    }

    public static void joinPublicRoom(LauncherActivity activity, PublicRoom room) {
        if (activity == null || room == null) return;
        prepareVersionAndLaunch(activity, new Invite(room.code, room.hostUsername, room.version, ""));
    }

    private static void updateEntitlements(JsonObject response) {
        if (response == null || !response.has("entitlements") || !response.get("entitlements").isJsonObject()) {
            return;
        }
        JsonObject entitlements = response.getAsJsonObject("entitlements");
        sEntitlements = new Entitlements(
                entitlements.has("battlyWorldsPlus") && entitlements.get("battlyWorldsPlus").getAsBoolean(),
                entitlements.has("tier") ? entitlements.get("tier").getAsString() : "free",
                entitlements.has("priority") ? entitlements.get("priority").getAsString() : "standard",
                entitlements.has("persistentRooms") && entitlements.get("persistentRooms").getAsBoolean(),
                entitlements.has("unlimitedInvites") && entitlements.get("unlimitedInvites").getAsBoolean(),
                entitlements.has("maxInvites") ? entitlements.get("maxInvites").getAsInt() : 5,
                entitlements.has("maxGuests") ? entitlements.get("maxGuests").getAsInt() : 3,
                entitlements.has("roomDurationHours") ? entitlements.get("roomDurationHours").getAsInt() : 6
        );
    }

    public static String resolveRoomCode(Context context, String code) throws Exception {
        String cleanCode = safe(code).toUpperCase();
        if (!looksLikeShortCode(cleanCode)) {
            return code;
        }
        JsonObject response = getAuthorizedJson(API_BASE + "/api/v2/battlyworlds/rooms/"
                + URLEncoder.encode(cleanCode, StandardCharsets.UTF_8.name()),
                requireBattlyToken(context));
        if (response.has("status") && response.get("status").getAsInt() == 200 && response.has("fullCode")) {
            return response.get("fullCode").getAsString();
        }
        String message = response.has("message") ? response.get("message").getAsString()
                : response.has("error") ? response.get("error").getAsString()
                : context.getString(R.string.battlyworlds_invalid_code);
        throw new IllegalStateException(message);
    }

    public static boolean looksLikeShortCode(String code) {
        return safe(code).matches("(?i)^[A-Z0-9]{6}$");
    }

    private static void showLauncherInvite(LauncherActivity activity, Invite invite) {
        new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setTitle(activity.getString(R.string.battlyworlds_invite_title))
                .setMessage(activity.getString(R.string.battlyworlds_invite_message,
                        invite.from.isEmpty() ? activity.getString(R.string.battlyworlds_player_anonymous) : invite.from,
                        invite.version.isEmpty() ? "-" : invite.version))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.battlyworlds_invite_accept, (dialog, which) -> {
                    prepareVersionAndLaunch(activity, invite);
                })
                .show();
    }

    private static void showInGameInvite(MainActivity activity, Invite invite) {
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setTitle(activity.getString(R.string.battlyworlds_invite_title))
                .setMessage(activity.getString(R.string.battlyworlds_invite_ingame_message,
                        invite.from.isEmpty() ? activity.getString(R.string.battlyworlds_player_anonymous) : invite.from))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.battlyworlds_invite_accept, (dialog, which) -> startGuest(activity, invite))
                .show());
    }

    private static void prepareVersionAndLaunch(LauncherActivity activity, Invite invite) {
        savePendingInvite(activity, invite);
        if (!invite.version.isEmpty()) {
            LauncherProfiles.load();
            String selectedProfile = LauncherPreferences.DEFAULT_PREF
                    .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(selectedProfile);
            if (profile != null) {
                profile.lastVersionId = invite.version;
                LauncherProfiles.write();
            }
        }
        Toast.makeText(activity, R.string.battlyworlds_invite_launching, Toast.LENGTH_LONG).show();
        new AsyncVersionList().getVersionList(versions -> {
            if (versions != null) {
                ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions);
            }
            Tools.MAIN_HANDLER.post(() -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        }, false);
    }

    private static void startGuest(Activity activity, Invite invite) {
        ConnectionStatusPopup popup = showGuestConnectionStatus(activity);
        new Thread(() -> {
            try {
                String resolvedCode = BattlyWorldsInvites.resolveRoomCode(activity, invite.code);
                refreshEntitlements(activity);
                List<String> nodes = BattlyWorldsNodeList.fetch(activity);
                Tools.MAIN_HANDLER.post(() -> {
                    try {
                        BattlyWorldsManager.initialize(activity);
                        BattlyWorldsManager.attachActivity(activity);
                        TerracottaAndroidAPI.RoomType type = BattlyWorldsManager.parseRoomCode(resolvedCode);
                        if (type == null) {
                            Toast.makeText(activity, R.string.battlyworlds_invalid_code, Toast.LENGTH_SHORT).show();
                            if (popup != null) {
                                popup.setMessage(activity.getString(R.string.battlyworlds_invalid_code));
                            }
                            return;
                        }
                        BattlyWorldsManager.setWaiting(activity, true);
                        if (!BattlyWorldsManager.join(resolvedCode, getPlayerName(activity), nodes)) {
                            Toast.makeText(activity, R.string.battlyworlds_invalid_code, Toast.LENGTH_SHORT).show();
                            if (popup != null) {
                                popup.setMessage(activity.getString(R.string.battlyworlds_invalid_code));
                            }
                        } else if (popup != null) {
                            popup.attachStateListener();
                            popup.setMessage(activity.getString(R.string.battlyworlds_join_status_preparing));
                        }
                    } catch (Throwable throwable) {
                        if (popup != null) {
                            popup.setMessage(throwable.getMessage());
                        }
                        Tools.showError(activity, throwable);
                    }
                });
            } catch (Throwable throwable) {
                Tools.MAIN_HANDLER.post(() -> {
                    if (popup != null) {
                        popup.setMessage(throwable.getMessage());
                    }
                    Tools.showError(activity, throwable);
                });
            }
        }, "BattlyWorlds Invite Join").start();
    }

    @Nullable
    private static ConnectionStatusPopup showGuestConnectionStatus(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }
        ConnectionStatusPopup popup = new ConnectionStatusPopup(activity);
        activity.runOnUiThread(popup::show);
        return popup;
    }

    private static final class ConnectionStatusPopup {
        private final Activity activity;
        private final LinearLayout panel;
        private final TextView message;
        private final BattlyWorldsManager.StateListener listener;
        private boolean attached;
        private boolean closed;

        private ConnectionStatusPopup(Activity activity) {
            this.activity = activity;
            panel = new LinearLayout(activity);
            panel.setOrientation(LinearLayout.HORIZONTAL);
            panel.setGravity(Gravity.CENTER_VERTICAL);
            panel.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 10), dp(activity, 12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xF21A2A33);
            background.setCornerRadius(dp(activity, 20));
            background.setStroke(1, 0x335C7F87);
            panel.setBackground(background);
            panel.setElevation(dp(activity, 8));

            ImageView icon = new ImageView(activity);
            icon.setImageResource(R.drawable.logo);
            icon.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            GradientDrawable iconBackground = new GradientDrawable();
            iconBackground.setColor(0x333C4E58);
            iconBackground.setCornerRadius(dp(activity, 14));
            icon.setBackground(iconBackground);
            panel.addView(icon, new LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)));

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(activity, 12), 0, dp(activity, 10), 0);
            TextView title = new TextView(activity);
            title.setText(R.string.battlyworlds_join_status_title);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            message = new TextView(activity);
            message.setText(R.string.battlyworlds_join_status_preparing);
            message.setTextColor(0xFFB4C5D0);
            message.setTextSize(12);
            message.setMaxLines(3);
            copy.addView(title);
            copy.addView(message);
            panel.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageButton close = new ImageButton(activity);
            close.setImageResource(R.drawable.ic_close_white);
            close.setColorFilter(0xFFFFFFFF);
            close.setBackgroundColor(Color.TRANSPARENT);
            close.setPadding(dp(activity, 9), dp(activity, 9), dp(activity, 9), dp(activity, 9));
            close.setOnClickListener(v -> close());
            panel.addView(close, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)));

            listener = state -> {
                String guestUrl = BattlyWorldsManager.getGuestUrl(state);
                if (guestUrl != null) {
                    setMessage(activity.getString(R.string.battlyworlds_join_status_ready));
                    Tools.MAIN_HANDLER.postDelayed(this::close, 7000);
                } else {
                    setMessage(BattlyWorldsManager.describeState(activity, state));
                }
            };
        }

        private void show() {
            try {
                FrameLayout root = activity.findViewById(android.R.id.content);
                int promptWidth = Math.min(dp(activity, 380),
                        activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 36));
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        promptWidth,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.gravity = Gravity.TOP | Gravity.END;
                params.setMargins(0, dp(activity, 42), dp(activity, 18), 0);
                root.addView(panel, params);
            } catch (Throwable ignored) {
                BattlyWorldsManager.removeStateListener(listener);
            }
        }

        private void attachStateListener() {
            if (attached) {
                return;
            }
            attached = true;
            BattlyWorldsManager.addStateListener(listener);
        }

        private void setMessage(String text) {
            Tools.MAIN_HANDLER.post(() -> {
                if (activity.isFinishing() || closed) {
                    return;
                }
                message.setText(TextUtils.isEmpty(text)
                        ? activity.getString(R.string.battlyworlds_join_status_preparing)
                        : text);
            });
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            BattlyWorldsManager.removeStateListener(listener);
            if (panel.getParent() instanceof ViewGroup) {
                ((ViewGroup) panel.getParent()).removeView(panel);
            }
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void postInviteNotification(Context context, Invite invite) {
        if (!BattlyWorldsPreferences.areInvitationsEnabled(context)) return;
        buildInviteNotificationChannel(context);
        Intent intent = toIntent(new Intent(context, LauncherActivity.class), invite);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                NotificationUtils.PENDINGINTENT_CODE_BATTLYWORLDS + 100,
                intent,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, INVITE_CHANNEL_ID)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(context.getString(R.string.battlyworlds_invite_notification_title))
                .setContentText(context.getString(R.string.battlyworlds_invite_notification_text,
                        invite.from.isEmpty() ? context.getString(R.string.battlyworlds_player_anonymous) : invite.from))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 180, 80, 180})
                .setTicker(context.getString(R.string.battlyworlds_invite_notification_title))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) (System.currentTimeMillis() & 0x0FFFFFFF), builder.build());
    }

    private static void buildInviteNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                INVITE_CHANNEL_ID,
                context.getString(R.string.battlyworlds_invite_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.battlyworlds_invite_channel_description));
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 180, 80, 180});
        channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private static Intent toIntent(Intent intent, Invite invite) {
        intent.putExtra(EXTRA_TYPE, TYPE);
        intent.putExtra(EXTRA_CODE, invite.code);
        intent.putExtra(EXTRA_FROM, invite.from);
        intent.putExtra(EXTRA_VERSION, invite.version);
        intent.putExtra(EXTRA_INVITE_ID, invite.inviteId);
        return intent;
    }

    private static Invite fromIntent(@Nullable Intent intent) {
        if (intent == null || !TYPE.equals(intent.getStringExtra(EXTRA_TYPE))) {
            return new Invite("", "", "", "");
        }
        return new Invite(
                intent.getStringExtra(EXTRA_CODE),
                intent.getStringExtra(EXTRA_FROM),
                intent.getStringExtra(EXTRA_VERSION),
                intent.getStringExtra(EXTRA_INVITE_ID)
        );
    }

    private static Invite fromMap(Map<String, String> data) {
        if (data == null || !TYPE.equals(data.get(EXTRA_TYPE))) {
            return new Invite("", "", "", "");
        }
        return new Invite(
                data.get(EXTRA_CODE),
                first(data.get(EXTRA_FROM), data.get("fromUsername")),
                data.get(EXTRA_VERSION),
                data.get(EXTRA_INVITE_ID)
        );
    }

    private static void savePendingInvite(Context context, Invite invite) {
        prefs(context).edit()
                .putString(PREF_PENDING_CODE, invite.code)
                .putString(PREF_PENDING_FROM, invite.from)
                .putString(PREF_PENDING_VERSION, invite.version)
                .apply();
    }

    private static JsonObject postJson(String endpoint, String token, JSONObject body) throws Exception {
        return requestJson("POST", endpoint, token, body);
    }

    private static JsonObject requestJson(String method, String endpoint, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (!token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setDoOutput(true);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bytes);
        }
        try (InputStream inputStream = connection.getResponseCode() >= 400
                ? connection.getErrorStream()
                : connection.getInputStream()) {
            String response = readFully(inputStream);
            return Tools.GLOBAL_GSON.fromJson(response, JsonObject.class);
        } finally {
            connection.disconnect();
        }
    }

    private static String jsonString(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull()
                ? safe(object.get(key).getAsString()) : "";
    }

    private static int jsonInt(JsonObject object, String key, int fallback) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean jsonBoolean(JsonObject object, String key, boolean fallback) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String jsonError(JsonObject response, String fallback) {
        if (response != null && response.has("message")) return response.get("message").getAsString();
        if (response != null && response.has("error")) return response.get("error").getAsString();
        return fallback;
    }

    public static final class PublicRoom {
        public final String code;
        public final String title;
        public final String hostUsername;
        public final String version;
        public final boolean premium;
        public final int maxGuests;

        public PublicRoom(String code, String title, String hostUsername, String version,
                          boolean premium, int maxGuests) {
            this.code = code;
            this.title = title;
            this.hostUsername = hostUsername;
            this.version = version;
            this.premium = premium;
            this.maxGuests = maxGuests;
        }
    }

    private static JsonObject getAuthorizedJson(String endpoint, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        try (InputStream inputStream = connection.getResponseCode() >= 400
                ? connection.getErrorStream()
                : connection.getInputStream()) {
            return Tools.GLOBAL_GSON.fromJson(readFully(inputStream), JsonObject.class);
        } finally {
            connection.disconnect();
        }
    }

    private static String readFully(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "{}";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private static String getBattlyToken(Context context) {
        if (context == null) return "";
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(
                context.getApplicationContext(), null);
        if (account == null || !account.isBattly() || !Tools.isValidString(account.accessToken)) {
            return "";
        }
        String token = account.accessToken.trim();
        return "0".equals(token) || token.length() < 32 ? "" : token;
    }

    private static String requireBattlyToken(Context context) {
        String token = getBattlyToken(context);
        if (token.isEmpty()) {
            throw new IllegalStateException(context == null
                    ? "Battly account required"
                    : context.getString(R.string.battlyworlds_login_required));
        }
        return token;
    }

    private static String getCurrentVersion() {
        try {
            MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
            if (profile != null && profile.lastVersionId != null) {
                return AsyncMinecraftDownloader.normalizeVersionId(profile.lastVersionId);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static String getHostVersion() {
        return getCurrentVersion();
    }

    private static String getPlayerName(Context context) {
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(context, null);
        if (account != null && !TextUtils.isEmpty(account.username)) {
            return account.username.replace("Demo.", "");
        }
        String battlyName = context.getSharedPreferences("battly_account", Context.MODE_PRIVATE)
                .getString("battly_username", "");
        return battlyName == null || battlyName.trim().isEmpty()
                ? context.getString(R.string.battlyworlds_player_anonymous)
                : battlyName;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String first(String first, String second) {
        return safe(first).isEmpty() ? safe(second) : safe(first);
    }

    private BattlyWorldsInvites() {
    }
}
