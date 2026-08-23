package net.kdt.pojavlaunch.battlysocial;

import android.content.Context;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BattlySocialApi {
    private static final String API_BASE = "https://api.battlylauncher.com";

    public static final class Server {
        public final String host;
        public final int port;
        public final String address;
        public final String name;
        public final boolean joinable;

        Server(JSONObject json) {
            host = json == null ? "" : json.optString("host", "");
            port = json == null ? 25565 : json.optInt("port", 25565);
            address = json == null ? "" : json.optString("address", host);
            name = json == null ? "" : json.optString("name", address);
            joinable = json != null && json.optBoolean("joinable", true);
        }
    }

    public static final class Friend {
        public final String userId;
        public final String username;
        public final String state;
        public final String activityLabel;
        public final boolean battlyWorlds;
        public final String version;
        public final Server server;
        public final boolean premium;

        Friend(JSONObject json) {
            userId = json.optString("userId", "");
            username = json.optString("username", "");
            state = json.optString("state", "offline");
            JSONObject activity = json.optJSONObject("activity");
            activityLabel = activity == null ? "" : activity.optString("label", "");
            battlyWorlds = activity != null && (activity.optBoolean("battlyWorlds", false)
                    || "battlyworlds".equals(activity.optString("type", "")));
            version = activity == null ? "" : activity.optString("version", "");
            server = activity == null || activity.optJSONObject("server") == null
                    ? null
                    : new Server(activity.optJSONObject("server"));
            JSONObject profile = json.optJSONObject("profile");
            premium = profile != null && profile.optBoolean("isPremium", false);
        }

        public boolean isOnline() {
            return !"offline".equals(state);
        }

        public boolean isPlaying() {
            return "playing".equals(state);
        }
    }

    public static final class Request {
        public final String userId;
        public final String username;

        Request(JSONObject json) {
            userId = json.optString("userId", "");
            username = json.optString("username", "");
        }
    }

    public static final class SearchUser {
        public final String userId;
        public final String username;
        public final String relation;
        public final boolean premium;

        SearchUser(JSONObject json) {
            userId = json.optString("userId", "");
            username = json.optString("username", "");
            relation = json.optString("relation", "none");
            JSONObject profile = json.optJSONObject("profile");
            premium = profile != null && profile.optBoolean("isPremium", false);
        }
    }

    public static final class Invite {
        public final String inviteId;
        public final String kind;
        public final String fromUsername;
        public final String version;
        public final String roomCode;
        public final Server server;

        Invite(JSONObject json) {
            inviteId = json.optString("inviteId", "");
            kind = json.optString("kind", "server");
            fromUsername = json.optString("fromUsername", "");
            version = json.optString("version", "");
            roomCode = json.optString("roomCode", "");
            server = json.optJSONObject("server") == null ? null : new Server(json.optJSONObject("server"));
        }
    }

    public static final class Overview {
        public final String username;
        public final int onlineCount;
        public final int playingCount;
        public final int requestCount;
        public final int inviteCount;
        public final List<Friend> friends;
        public final List<Request> receivedRequests;
        public final List<Request> sentRequests;
        public final boolean battlyWorldsAvailable;
        public final Server myServer;
        public final String myVersion;

        Overview(JSONObject json) {
            JSONObject me = json.optJSONObject("me");
            username = me == null ? "" : me.optString("username", "");
            JSONObject counts = json.optJSONObject("counts");
            onlineCount = counts == null ? 0 : counts.optInt("online", 0);
            playingCount = counts == null ? 0 : counts.optInt("playing", 0);
            requestCount = counts == null ? 0 : counts.optInt("requests", 0);
            inviteCount = counts == null ? 0 : counts.optInt("invites", 0);
            friends = parseFriends(json.optJSONArray("friends"));
            JSONObject requests = json.optJSONObject("requests");
            receivedRequests = parseRequests(requests == null ? null : requests.optJSONArray("received"));
            sentRequests = parseRequests(requests == null ? null : requests.optJSONArray("sent"));
            JSONObject capabilities = json.optJSONObject("capabilities");
            battlyWorldsAvailable = capabilities != null && capabilities.optBoolean("battlyWorlds", false);
            JSONObject myActivity = me == null ? null : me.optJSONObject("activity");
            myServer = myActivity == null || myActivity.optJSONObject("server") == null
                    ? null
                    : new Server(myActivity.optJSONObject("server"));
            myVersion = myActivity == null ? "" : myActivity.optString("version", "");
        }
    }

    private BattlySocialApi() {
    }

    public static Overview fetchOverview(Context context) throws IOException, JSONException {
        return new Overview(request(context, "GET", "/api/v2/social/overview", null));
    }

    public static List<Invite> fetchInvites(Context context) throws IOException, JSONException {
        JSONArray array = request(context, "GET", "/api/v2/social/invites", null).optJSONArray("invites");
        if (array == null) return Collections.emptyList();
        List<Invite> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) result.add(new Invite(item));
        }
        return result;
    }

    public static List<SearchUser> search(Context context, String query) throws IOException, JSONException {
        String encoded = java.net.URLEncoder.encode(query == null ? "" : query, "UTF-8");
        JSONArray array = request(context, "GET", "/api/v2/social/search?q=" + encoded, null).optJSONArray("users");
        if (array == null) return Collections.emptyList();
        List<SearchUser> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) result.add(new SearchUser(item));
        }
        return result;
    }

    public static void sendFriendRequest(Context context, String username) throws IOException, JSONException {
        request(context, "POST", "/api/v2/social/friends/request",
                new JSONObject().put("username", username));
    }

    public static void acceptRequest(Context context, String username) throws IOException, JSONException {
        request(context, "POST", "/api/v2/users/aceptarSolicitud",
                new JSONObject().put("solicitud", username));
    }

    public static void rejectRequest(Context context, String username) throws IOException, JSONException {
        request(context, "POST", "/api/v2/users/rechazarSolicitud",
                new JSONObject().put("amigo", username));
    }

    public static void cancelRequest(Context context, String username) throws IOException, JSONException {
        request(context, "POST", "/api/v2/users/cancelarSolicitud",
                new JSONObject().put("amigo", username));
    }

    public static void removeFriend(Context context, String username) throws IOException, JSONException {
        request(context, "POST", "/api/v2/social/friends/remove",
                new JSONObject().put("username", username));
    }

    public static void sendServerInvite(Context context, String username, Server server, String version)
            throws IOException, JSONException {
        JSONObject serverJson = new JSONObject()
                .put("host", server.host)
                .put("port", server.port)
                .put("name", server.name);
        request(context, "POST", "/api/v2/social/invites",
                new JSONObject()
                        .put("friendUsername", username)
                        .put("kind", "server")
                        .put("server", serverJson)
                        .put("version", version));
    }

    public static void updateInvite(Context context, String inviteId, String status)
            throws IOException, JSONException {
        request(context, "POST", "/api/v2/social/invites/" + inviteId + "/status",
                new JSONObject().put("status", status));
    }

    public static void updatePresence(Context context, String state, String version, Server server)
            throws IOException, JSONException {
        updatePresence(context, state, version, server, false);
    }

    public static void updatePresence(Context context, String state, String version, Server server,
                                      boolean battlyWorlds)
            throws IOException, JSONException {
        request(context, "POST", "/api/v2/social/presence",
                buildPresencePayload(state, version, server, battlyWorlds));
    }

    static JSONObject buildPresencePayload(String state, String version, Server server,
                                           boolean battlyWorlds) throws JSONException {
        JSONObject activity = new JSONObject()
                .put("version", version == null ? "" : version)
                .put("type", battlyWorlds ? "battlyworlds" : "minecraft")
                .put("battlyWorlds", battlyWorlds)
                .put("label", "playing".equals(state)
                        ? (battlyWorlds ? "Battly Worlds · Minecraft " : "Minecraft ") + version
                        : "Battly Mobile");
        JSONObject payload = new JSONObject()
                .put("state", state)
                .put("activity", activity)
                .put("client", new JSONObject()
                        .put("platform", "android")
                        .put("version", BuildConfig.VERSION_NAME))
                .put("privacy", new JSONObject()
                        .put("sharePresence", true)
                        .put("shareServer", true));
        if (server != null) {
            JSONObject serverJson = new JSONObject()
                    .put("host", server.host)
                    .put("port", server.port)
                    .put("name", server.name);
            payload.put("server", serverJson);
            activity.put("server", serverJson);
        }
        return payload;
    }

    public static void registerDeviceToken(Context context, String token) throws IOException, JSONException {
        request(context, "POST", "/api/v2/social/device-token",
                new JSONObject()
                        .put("token", token)
                        .put("platform", "android")
                        .put("clientVersion", BuildConfig.VERSION_NAME));
    }

    private static List<Friend> parseFriends(JSONArray array) {
        if (array == null) return Collections.emptyList();
        List<Friend> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) result.add(new Friend(item));
        }
        return result;
    }

    private static List<Request> parseRequests(JSONArray array) {
        if (array == null) return Collections.emptyList();
        List<Request> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) result.add(new Request(item));
        }
        return result;
    }

    private static JSONObject request(Context context, String method, String path, JSONObject payload)
            throws IOException, JSONException {
        String token = BattlyPlusManager.getToken(context);
        if (token == null || token.trim().isEmpty()) {
            throw new IOException("Inicia sesión con tu cuenta de Battly");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        if (payload != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.getOutputStream().write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = readFully(stream);
        connection.disconnect();
        if (body == null || !body.trim().startsWith("{")) {
            throw new IOException("La API de Battly Social devolvió una respuesta no válida");
        }
        JSONObject json = new JSONObject(body);
        int status = json.optInt("status", code >= 400 ? code : 200);
        if (code >= 400 || status >= 400) {
            throw new IOException(json.optString("message", json.optString("error", "Error " + status)));
        }
        return json;
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }
}
