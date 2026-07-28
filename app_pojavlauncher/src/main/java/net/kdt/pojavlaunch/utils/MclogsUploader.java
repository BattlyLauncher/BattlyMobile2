package net.kdt.pojavlaunch.utils;

import android.os.Build;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Uploads a redacted, size-limited launcher log to mclo.gs. */
public final class MclogsUploader {
    private static final String ENDPOINT = "https://api.mclo.gs/1/log";
    private static final int MAX_LINES = 25_000;
    private static final int MAX_SOURCE_BYTES = 8 * 1024 * 1024;
    private static final Pattern ACCESS_TOKEN = Pattern.compile(
            "(?i)(--accessToken(?:=|\\s+))([^\\s,;]+)");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)([^\\s,;]+)");
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)(\\\"?(?:access_?token|refresh_?token|client_?secret|password|sessionid|xsts)\\\"?\\s*[:=]\\s*\\\"?)([^\\\"\\s,;}]+)");
    private static final Pattern JWT_TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}(?![A-Za-z0-9_-])");
    private static final Pattern LONG_HEX_TOKEN = Pattern.compile(
            "(?i)(?<![0-9a-f])[0-9a-f]{48,}(?![0-9a-f])");

    private MclogsUploader() {
    }

    public static UploadResult upload(@NonNull File logFile) throws IOException, JSONException {
        if (!logFile.isFile() || !logFile.canRead()) {
            throw new IOException("Log file is not readable: " + logFile);
        }
        JSONObject payload = new JSONObject();
        payload.put("content", redactAndLimit(readTail(logFile)));
        payload.put("source", "Battly Mobile");
        payload.put("metadata", buildMetadata());

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(25_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("User-Agent", "Battly-Mobile/" + BuildConfig.VERSION_NAME);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                connection.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(payload.toString());
        }

        int status = connection.getResponseCode();
        String response = readResponse(status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        JSONObject body = new JSONObject(response);
        if (status < 200 || status >= 300 || !body.optBoolean("success")) {
            throw new IOException(body.optString("error", "mclo.gs returned HTTP " + status));
        }
        String url = body.optString("url");
        if (url.isEmpty()) throw new IOException("mclo.gs did not return a share URL");
        return new UploadResult(url, body.optString("id"), body.optInt("lines"), body.optInt("errors"));
    }

    static String redactAndLimit(String raw) {
        String redacted = ACCESS_TOKEN.matcher(raw).replaceAll("$1[REDACTED]");
        redacted = AUTHORIZATION.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = SECRET_FIELD.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = JWT_TOKEN.matcher(redacted).replaceAll("[REDACTED_JWT]");
        redacted = LONG_HEX_TOKEN.matcher(redacted).replaceAll("[REDACTED_TOKEN]");
        String[] lines = redacted.split("\\R", -1);
        int start = Math.max(0, lines.length - MAX_LINES);
        StringBuilder output = new StringBuilder(Math.min(redacted.length(), MAX_SOURCE_BYTES));
        if (start > 0) output.append("[Battly: earlier log lines were truncated]\\n");
        for (int i = start; i < lines.length; i++) {
            if (output.length() + lines[i].length() + 1 > MAX_SOURCE_BYTES) {
                output.delete(0, Math.min(output.length(), output.length() / 4));
                output.insert(0, "[Battly: earlier log content was truncated]\\n");
            }
            output.append(lines[i]);
            if (i + 1 < lines.length) output.append('\n');
        }
        return output.toString();
    }

    private static String readTail(File file) throws IOException {
        long offset = Math.max(0L, file.length() - MAX_SOURCE_BYTES);
        try (FileInputStream input = new FileInputStream(file)) {
            long skipped = 0L;
            while (skipped < offset) {
                long amount = input.skip(offset - skipped);
                if (amount <= 0L) break;
                skipped += amount;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), MAX_SOURCE_BYTES));
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            String value = output.toString(StandardCharsets.UTF_8.name());
            if (offset > 0) {
                int firstLine = value.indexOf('\n');
                if (firstLine >= 0) value = value.substring(firstLine + 1);
            }
            return value;
        }
    }

    private static JSONArray buildMetadata() throws JSONException {
        JSONArray metadata = new JSONArray();
        addMetadata(metadata, "launcher_version", BuildConfig.VERSION_NAME, "Battly Mobile", true);
        MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
        addMetadata(metadata, "minecraft_version", profile == null ? "unknown" : profile.lastVersionId,
                "Minecraft", true);
        addMetadata(metadata, "renderer", profile != null && Tools.isValidString(profile.pojavRendererName)
                ? profile.pojavRendererName : LauncherPreferences.PREF_RENDERER, "Renderer", true);
        addMetadata(metadata, "android_api", Build.VERSION.SDK_INT, "Android API", true);
        addMetadata(metadata, "architecture", Architecture.archAsString(Tools.DEVICE_ARCHITECTURE),
                "Architecture", true);
        addMetadata(metadata, "device", Build.MANUFACTURER + " " + Build.MODEL, "Device", false);
        return metadata;
    }

    private static void addMetadata(JSONArray target, String key, Object value, String label, boolean visible)
            throws JSONException {
        target.put(new JSONObject().put("key", key).put("value", value)
                .put("label", label).put("visible", visible));
    }

    private static String readResponse(InputStream stream) throws IOException {
        if (stream == null) return "{}";
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        }
        return response.toString();
    }

    public static final class UploadResult {
        public final String url;
        public final String id;
        public final int lines;
        public final int errors;

        UploadResult(String url, String id, int lines, int errors) {
            this.url = url;
            this.id = id;
            this.lines = lines;
            this.errors = errors;
        }
    }
}
