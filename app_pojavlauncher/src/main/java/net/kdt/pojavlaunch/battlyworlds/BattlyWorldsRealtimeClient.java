package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.client.Ack;
final class BattlyWorldsRealtimeClient {
    interface Listener {
        default void onMembersChanged(List<Member> members) { }
        default void onVoiceSignal(String from, JSONObject signal) { }
        default void onVoiceState(String userId, boolean muted, boolean connected) { }
        default void onVoiceChannelChanged(String voiceChannel) { }
        default void onVoicePartyInvite(String partyId, String fromUsername) { }
        default void onConnectionStateChanged(boolean authenticated, String error) { }
    }

    interface PartyInviteListener extends Listener { }

    interface ActionCallback {
        void onResult(boolean success, String value);
    }

    static final class Member {
        final String userId;
        final String username;
        boolean muted;
        boolean voiceConnected;
        String voiceChannel;

        Member(String userId, String username, boolean muted, boolean voiceConnected,
               String voiceChannel) {
            this.userId = userId;
            this.username = username;
            this.muted = muted;
            this.voiceConnected = voiceConnected;
            this.voiceChannel = normalizeVoiceChannel(voiceChannel);
        }
    }

    static final class IceServerConfig {
        final String url;
        final String username;
        final String credential;

        IceServerConfig(String url, String username, String credential) {
            this.url = url;
            this.username = username;
            this.credential = credential;
        }
    }

    private static final String TAG = "BattlyWorldsRealtime";
    private static final long AUTH_TIMEOUT_MS = 12_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Member> MEMBERS = new CopyOnWriteArrayList<>();
    private static Socket socket;
    private static String roomCode = "";
    private static String currentUserId = "";
    private static boolean authenticated;
    private static boolean authenticating;
    private static String currentVoiceChannel = "room";
    private static int authenticationAttempt;
    private static Context appContext;
    private static final List<IceServerConfig> ICE_SERVERS = new CopyOnWriteArrayList<>();
    private static PendingPartyInvite pendingPartyInvite;

    static synchronized void connect(Activity activity, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (!normalized.matches("^[A-Z0-9]{6}$")) return;
        if (socket != null && normalized.equals(roomCode) && socket.connected()) {
            ensureAuthenticated();
            return;
        }
        disconnect();
        appContext = activity.getApplicationContext();
        roomCode = normalized;
        try {
            IO.Options options = IO.Options.builder()
                    .setReconnection(true)
                    .setReconnectionAttempts(Integer.MAX_VALUE)
                    .setReconnectionDelay(1000)
                    .build();
            socket = IO.socket("https://api.battlylauncher.com/battlyworlds", options);
            bindEvents(activity, socket);
            socket.connect();
        } catch (URISyntaxException error) {
            Log.e(TAG, "Invalid realtime URL", error);
        }
    }

    private static void bindEvents(Activity activity, Socket target) {
        target.on(Socket.EVENT_CONNECT, args -> {
            authenticated = false;
            notifyConnectionState("");
            authenticate(target);
        });
        target.on("room_members", args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
            JSONArray array = ((JSONObject) args[0]).optJSONArray("members");
            MEMBERS.clear();
            if (array != null) for (int i = 0; i < array.length(); i++) {
                Member member = parseMember(array.optJSONObject(i));
                if (member != null) MEMBERS.add(member);
            }
            notifyMembers();
        });
        target.on("member_joined", args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
            Member member = parseMember(((JSONObject) args[0]).optJSONObject("member"));
            if (member == null) return;
            upsert(member);
            if (!member.userId.equals(currentUserId)) {
                BattlyWorldsPresenceOverlay.showJoined(currentActivity(activity), member.username);
            }
            notifyMembers();
        });
        target.on("member_left", args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
            String userId = ((JSONObject) args[0]).optString("userId", "");
            removeMember(userId);
            notifyMembers();
        });
        target.on("voice_state", args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
            JSONObject data = (JSONObject) args[0];
            String userId = data.optString("userId", "");
            for (Member member : MEMBERS) if (member.userId.equals(userId)) {
                member.muted = data.optBoolean("muted", false);
                member.voiceConnected = data.optBoolean("connected", false);
                member.voiceChannel = normalizeVoiceChannel(data.optString("voiceChannel", member.voiceChannel));
            }
            for (Listener listener : LISTENERS) listener.onVoiceState(userId,
                    data.optBoolean("muted", false), data.optBoolean("connected", false));
            notifyMembers();
        });
        target.on("voice_channel_changed", args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
            currentVoiceChannel = normalizeVoiceChannel(
                    ((JSONObject) args[0]).optString("voiceChannel", "room"));
            for (Listener listener : LISTENERS) listener.onVoiceChannelChanged(currentVoiceChannel);
        });
        target.on("voice_party_invite", args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
            JSONObject data = (JSONObject) args[0];
            String partyId = data.optString("partyId", "");
            if (partyId.isEmpty()) return;
            PendingPartyInvite invite = new PendingPartyInvite(partyId,
                    data.optString("fromUsername", "BattlyPlayer"));
            pendingPartyInvite = invite;
            dispatchPendingPartyInvite();
        });
        target.on("voice_signal", args -> {
            if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
            JSONObject data = (JSONObject) args[0];
            JSONObject signal = data.optJSONObject("signal");
            if (signal == null) return;
            for (Listener listener : LISTENERS) {
                listener.onVoiceSignal(data.optString("from", ""), signal);
            }
        });
        target.on(Socket.EVENT_CONNECT_ERROR, args -> {
            authenticating = false;
            authenticated = false;
            Log.w(TAG, "Realtime connection failed");
            notifyConnectionState("connection_failed");
        });
        target.on(Socket.EVENT_DISCONNECT, args -> {
            authenticating = false;
            authenticated = false;
            notifyConnectionState("disconnected");
        });
    }

    private static synchronized void authenticate(Socket target) {
        if (target == null || target != socket || !target.connected() || authenticated || authenticating) return;
        authenticating = true;
        int attempt = ++authenticationAttempt;
        JSONObject auth = new JSONObject();
        try {
            auth.put("token", BattlyWorldsInvites.getBattlyToken(appContext));
            auth.put("roomCode", roomCode);
            auth.put("username", BattlyWorldsInvites.getBattlyUsername(appContext));
        } catch (Exception ignored) { }
        target.emit("authenticate_room", new Object[]{auth}, (Ack) ackArgs -> {
            synchronized (BattlyWorldsRealtimeClient.class) {
                if (target != socket || attempt != authenticationAttempt) return;
                authenticating = false;
                if (ackArgs.length == 0 || !(ackArgs[0] instanceof JSONObject)) {
                    notifyConnectionState("invalid_response");
                    return;
                }
                JSONObject response = (JSONObject) ackArgs[0];
                if (!response.optBoolean("ok", false)) {
                    String error = response.optString("error", "unknown");
                    Log.w(TAG, "Realtime room authentication rejected: " + error);
                    notifyConnectionState(error);
                    return;
                }
                currentUserId = response.optString("userId", "");
                currentVoiceChannel = normalizeVoiceChannel(response.optString("voiceChannel", "room"));
                updateIceServers(response.optJSONArray("iceServers"));
                authenticated = !currentUserId.isEmpty();
                notifyConnectionState(authenticated ? "" : "invalid_response");
            }
        });
        MAIN.postDelayed(() -> {
            synchronized (BattlyWorldsRealtimeClient.class) {
                if (socket == target && attempt == authenticationAttempt
                        && target.connected() && !authenticated) {
                    authenticating = false;
                    Log.w(TAG, "Realtime room authentication timed out");
                    notifyConnectionState("authentication_timeout");
                }
            }
        }, AUTH_TIMEOUT_MS);
    }

    static synchronized void ensureAuthenticated() {
        Socket target = socket;
        if (target == null) return;
        if (target.connected()) authenticate(target);
        else target.connect();
    }

    private static Activity currentActivity(Activity fallback) {
        Activity attached = BattlyWorldsManager.getAttachedActivity();
        return attached == null ? fallback : attached;
    }

    static void sendSignal(String to, JSONObject signal) {
        Socket target = socket;
        if (target == null || !target.connected()) return;
        JSONObject data = new JSONObject();
        try {
            data.put("to", to);
            data.put("signal", signal);
            target.emit("voice_signal", data);
        } catch (Exception ignored) { }
    }

    static void setVoiceState(boolean muted, boolean connected) {
        Socket target = socket;
        if (target == null || !target.connected()) return;
        JSONObject data = new JSONObject();
        try {
            data.put("muted", muted);
            data.put("connected", connected);
            target.emit("voice_state", data);
        } catch (Exception ignored) { }
    }

    static void createVoiceParty(List<String> memberIds, ActionCallback callback) {
        JSONObject data = new JSONObject();
        try {
            data.put("memberIds", new JSONArray(memberIds));
        } catch (Exception ignored) { }
        emitAction("voice_party_create", data, callback, "partyId");
    }

    static void joinVoiceParty(String partyId, ActionCallback callback) {
        JSONObject data = new JSONObject();
        try { data.put("partyId", partyId); } catch (Exception ignored) { }
        emitAction("voice_party_join", data, callback, "partyId");
    }

    static void switchToRoomVoice(ActionCallback callback) {
        emitAction("voice_channel_room", new JSONObject(), callback, "voiceChannel");
    }

    private static void emitAction(String event, JSONObject data, ActionCallback callback,
                                   String valueKey) {
        Socket target = socket;
        if (target == null || !target.connected() || !authenticated) {
            if (callback != null) callback.onResult(false, "disconnected");
            return;
        }
        target.emit(event, new Object[]{data}, (Ack) args -> {
            JSONObject response = args.length > 0 && args[0] instanceof JSONObject
                    ? (JSONObject) args[0] : null;
            boolean success = response != null && response.optBoolean("ok", false);
            String value = response == null ? "invalid_response"
                    : response.optString(success ? valueKey : "error", success ? "" : "unknown");
            if (success && ("partyId".equals(valueKey) || "voiceChannel".equals(valueKey))) {
                currentVoiceChannel = normalizeVoiceChannel(value);
            }
            if (callback != null) callback.onResult(success, value);
        });
    }

    static void addListener(Listener listener) {
        if (!LISTENERS.contains(listener)) LISTENERS.add(listener);
        listener.onMembersChanged(getMembers());
        if (listener instanceof PartyInviteListener) dispatchPendingPartyInvite();
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    static List<Member> getMembers() {
        return Collections.unmodifiableList(new ArrayList<>(MEMBERS));
    }

    static String getCurrentUserId() {
        return currentUserId;
    }

    static boolean isAuthenticated() {
        return authenticated;
    }

    static String getCurrentVoiceChannel() { return currentVoiceChannel; }

    static List<IceServerConfig> getIceServers() {
        if (ICE_SERVERS.isEmpty()) {
            return Collections.singletonList(new IceServerConfig(
                    "stun:stun.l.google.com:19302", "", ""));
        }
        return Collections.unmodifiableList(new ArrayList<>(ICE_SERVERS));
    }

    static synchronized void disconnect() {
        if (socket != null) {
            socket.emit("leave_room");
            socket.off();
            socket.disconnect();
            socket = null;
        }
        roomCode = "";
        currentUserId = "";
        currentVoiceChannel = "room";
        authenticated = false;
        authenticating = false;
        authenticationAttempt++;
        MEMBERS.clear();
        ICE_SERVERS.clear();
        pendingPartyInvite = null;
        notifyMembers();
        notifyConnectionState("disconnected");
    }

    private static void updateIceServers(JSONArray servers) {
        ICE_SERVERS.clear();
        if (servers == null) return;
        for (int i = 0; i < servers.length(); i++) {
            JSONObject server = servers.optJSONObject(i);
            if (server == null) continue;
            String url = server.optString("url", "").trim();
            if (url.isEmpty()) continue;
            ICE_SERVERS.add(new IceServerConfig(url,
                    server.optString("username", ""), server.optString("credential", "")));
        }
    }

    private static Member parseMember(JSONObject object) {
        if (object == null) return null;
        String userId = object.optString("userId", "");
        if (userId.isEmpty()) return null;
        return new Member(userId, object.optString("username", "BattlyPlayer"),
                object.optBoolean("muted", false), object.optBoolean("voiceConnected", false),
                object.optString("voiceChannel", "room"));
    }

    private static String normalizeVoiceChannel(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "room" : normalized;
    }

    private static void upsert(Member incoming) {
        removeMember(incoming.userId);
        MEMBERS.add(incoming);
    }

    private static void removeMember(String userId) {
        if (userId == null || userId.isEmpty()) return;
        for (Member member : new ArrayList<>(MEMBERS)) {
            if (userId.equals(member.userId)) MEMBERS.remove(member);
        }
    }

    private static void notifyMembers() {
        List<Member> snapshot = getMembers();
        for (Listener listener : LISTENERS) listener.onMembersChanged(snapshot);
    }

    private static void notifyConnectionState(String error) {
        for (Listener listener : LISTENERS) {
            listener.onConnectionStateChanged(authenticated, error);
        }
    }

    private static synchronized void dispatchPendingPartyInvite() {
        PendingPartyInvite invite = pendingPartyInvite;
        if (invite == null) return;
        for (Listener listener : LISTENERS) {
            if (!(listener instanceof PartyInviteListener)) continue;
            pendingPartyInvite = null;
            listener.onVoicePartyInvite(invite.partyId, invite.fromUsername);
            return;
        }
    }

    private static final class PendingPartyInvite {
        final String partyId;
        final String fromUsername;

        PendingPartyInvite(String partyId, String fromUsername) {
            this.partyId = partyId;
            this.fromUsername = fromUsername;
        }
    }

    private BattlyWorldsRealtimeClient() { }
}
