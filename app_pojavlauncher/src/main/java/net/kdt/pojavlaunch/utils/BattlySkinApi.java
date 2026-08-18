package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BattlySkinApi {
    private static final String API_BASE = "https://api.battlylauncher.com";
    private static final int MAX_SKIN_BYTES = 100 * 1024;

    private BattlySkinApi() {
    }

    public static SkinLibrary loadLibrary(Context context) throws IOException {
        AuthContext auth = requireAuth(context);
        HttpURLConnection connection = openConnection(API_BASE + "/api/v2/skins/library", "GET", auth.token);
        JSONObject response = readJson(connection);
        if (!response.optBoolean("success", false)) {
            throw apiError(response, "No se pudo cargar la biblioteca de skins");
        }

        SkinLibrary library = new SkinLibrary();
        library.username = auth.username;
        library.establishedSkinId = response.optString("established", "");
        library.slim = response.optBoolean("slim", false);
        JSONArray skins = response.optJSONArray("skins");
        if (skins != null) {
            for (int i = 0; i < skins.length(); i++) {
                String id = skins.optString(i, "");
                if (Tools.isValidString(id)) {
                    library.skins.add(new SkinEntry(id, id.equals(library.establishedSkinId)));
                }
            }
        }
        return library;
    }

    public static UploadResult uploadSkin(Context context, Uri uri) throws IOException {
        AuthContext auth = requireAuth(context);
        byte[] skinBytes = readSkinBytes(context, uri);
        String boundary = "BattlySkin" + System.currentTimeMillis();
        HttpURLConnection connection = openConnection(API_BASE + "/api/v2/skin/subir", "POST", auth.token);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setChunkedStreamingMode(0);

        try (OutputStream outputStream = connection.getOutputStream()) {
            writeFormField(outputStream, boundary, "token", auth.token);
            outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write("Content-Disposition: form-data; name=\"skin\"; filename=\"skin.png\"\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write(skinBytes);
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        JSONObject response = readJson(connection);
        if (response.optInt("status", 0) != 200) {
            throw apiError(response, "No se pudo subir la skin");
        }
        return new UploadResult(response.optString("skinID", ""), response.optString("message", ""));
    }

    public static void setSkin(Context context, String skinId) throws IOException {
        AuthContext auth = requireAuth(context);
        JSONObject payload = new JSONObject();
        try {
            payload.put("token", auth.token);
            payload.put("skinID", skinId);
        } catch (Exception e) {
            throw new IOException(e);
        }
        JSONObject response = postJson(API_BASE + "/api/v2/skin/establecer", auth.token, payload);
        if (response.optInt("status", 0) != 200) {
            throw apiError(response, "No se pudo establecer la skin");
        }
        refreshCurrentAccountFace(context);
    }

    public static void deleteSkin(Context context, String skinId) throws IOException {
        AuthContext auth = requireAuth(context);
        JSONObject payload = new JSONObject();
        try {
            payload.put("token", auth.token);
            payload.put("skinID", skinId);
        } catch (Exception e) {
            throw new IOException(e);
        }
        JSONObject response = postJson(API_BASE + "/api/v2/skin/eliminar", auth.token, payload);
        if (response.optInt("status", 0) != 200) {
            throw apiError(response, "No se pudo eliminar la skin");
        }
        refreshCurrentAccountFace(context);
    }

    public static void toggleSlim(Context context) throws IOException {
        AuthContext auth = requireAuth(context);
        JSONObject payload = new JSONObject();
        try {
            payload.put("token", auth.token);
        } catch (Exception e) {
            throw new IOException(e);
        }
        JSONObject response = postJson(API_BASE + "/api/v2/skin/slim/set", auth.token, payload);
        String status = response.optString("status", "");
        if (!"OK".equalsIgnoreCase(status)) {
            throw apiError(response, "No se pudo cambiar el modelo de skin");
        }
    }

    public static Bitmap downloadSkinBitmap(String username, String skinId) throws IOException {
        return downloadBitmap(getSkinUrl(username, skinId));
    }

    public static Bitmap downloadCurrentSkinBitmap(String username) throws IOException {
        return downloadBitmap(getCurrentSkinUrl(username));
    }

    public static Bitmap downloadFaceBitmap(String username) throws IOException {
        return downloadBitmap(getFaceUrl(username));
    }

    public static String downloadSkinDataUri(String username, String skinId) throws IOException {
        return toPngDataUri(downloadBytes(getSkinUrl(username, skinId)));
    }

    public static String downloadCurrentSkinDataUri(String username) throws IOException {
        return toPngDataUri(downloadBytes(getCurrentSkinUrl(username)));
    }

    public static byte[] downloadSkinBytes(String username, String skinId) throws IOException {
        return downloadBytes(getSkinUrl(username, skinId));
    }

    public static byte[] downloadCurrentSkinBytes(String username) throws IOException {
        return downloadBytes(getCurrentSkinUrl(username));
    }

    public static byte[] readSkinBytesForOffline(Context context, Uri uri) throws IOException {
        return readSkinBytes(context, uri);
    }

    public static String getSkinUrl(String username, String skinId) throws IOException {
        return API_BASE + "/api/v2/skin/" + encode(username) + "/" + encode(skinId) + "?t=" + System.currentTimeMillis();
    }

    public static String getCurrentSkinUrl(String username) throws IOException {
        return API_BASE + "/api/skin/" + encode(username) + "?t=" + System.currentTimeMillis();
    }

    public static String getFaceUrl(String username) throws IOException {
        return API_BASE + "/api/face/" + encode(username) + "?t=" + System.currentTimeMillis();
    }

    private static String toPngDataUri(byte[] bytes) {
        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static Bitmap downloadBitmap(String url) throws IOException {
        byte[] bytes = downloadBytes(url);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) {
            throw new IOException("Imagen no válida");
        }
        return bitmap;
    }

    private static byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code);
        }
        try (InputStream inputStream = connection.getInputStream()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static JSONObject postJson(String url, String token, JSONObject payload) throws IOException {
        HttpURLConnection connection = openConnection(url, "POST", token);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(bytes);
        }
        return readJson(connection);
    }

    private static HttpURLConnection openConnection(String url, String method, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        if (Tools.isValidString(token)) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
        }
        return connection;
    }

    private static JSONObject readJson(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        String body = stream == null ? "" : Tools.read(stream);
        if (!Tools.isValidString(body)) {
            throw new IOException("Respuesta vacía del servidor");
        }
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            throw new IOException("Respuesta no válida del servidor: HTTP " + code, e);
        }
    }

    private static IOException apiError(JSONObject response, String fallback) {
        String message = response.optString("description", response.optString("message", fallback));
        return new IOException(Tools.isValidString(message) ? message : fallback);
    }

    private static void writeFormField(OutputStream outputStream, String boundary, String name, String value) throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readSkinBytes(Context context, Uri uri) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("No se pudo abrir el archivo");
            }
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SKIN_BYTES) {
                    throw new IOException("La skin supera el límite de 100 KB de Battly");
                }
                outputStream.write(buffer, 0, read);
            }
        }
        byte[] bytes = outputStream.toByteArray();
        if (bytes.length < 8 || bytes[0] != (byte) 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47) {
            throw new IOException("Selecciona una skin PNG válida");
        }
        return bytes;
    }

    private static AuthContext requireAuth(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        String token = BattlyPlusManager.getToken(appContext);
        String username = getBattlyUsername(appContext);
        if (!Tools.isValidString(token) || !Tools.isValidString(username)) {
            throw new IOException("Inicia sesión con una cuenta de Battly para gestionar skins");
        }
        return new AuthContext(token, username);
    }

    private static String getBattlyUsername(Context context) {
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(context, null);
        if (account != null && account.isBattly() && Tools.isValidString(account.username)) {
            return account.username;
        }
        SharedPreferences prefs = context.getSharedPreferences(BattlyPlusManager.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("battly_username", "");
    }

    private static void refreshCurrentAccountFace(Context context) {
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(context, null);
        if (account == null || !account.isBattly()) {
            return;
        }
        try {
            account.updateSkinFace();
            account.save();
        } catch (Exception ignored) {
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static class AuthContext {
        final String token;
        final String username;

        AuthContext(String token, String username) {
            this.token = token;
            this.username = username;
        }
    }

    public static class UploadResult {
        public final String skinId;
        public final String message;

        UploadResult(String skinId, String message) {
            this.skinId = skinId;
            this.message = message;
        }
    }

    public static class SkinLibrary {
        public String username;
        public String establishedSkinId;
        public boolean slim;
        public final List<SkinEntry> skins = new ArrayList<>();
    }

    public static class SkinEntry {
        public final String id;
        public final boolean selected;

        SkinEntry(String id, boolean selected) {
            this.id = id;
            this.selected = selected;
        }
    }
}
