package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Keeps Battly promotions at the top of each instance's multiplayer server list. */
public final class PromotedServersManager {
    private static final String TAG = "PromotedServers";
    private static final String CONFIG_URL =
            "https://api.battlylauncher.com/v2/battlylauncher/launcher/config-launcher/config.json";
    private static final String PREFS = "battly_promoted_servers";
    private static final String CACHE_JSON = "config_json";
    private static final String CACHE_TIME = "config_time";
    private static final String MANAGED_PREFIX = "managed_";
    private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final int MAX_CONFIG_BYTES = 2 * 1024 * 1024;
    private static final int MAX_NBT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_COLLECTION_SIZE = 100_000;

    private PromotedServersManager() {
    }

    public static synchronized void syncBeforeLaunch(Context context, File gameDirectory) {
        if (context == null || gameDirectory == null) return;
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            List<Promotion> promotions = loadPromotions(preferences, false);
            if (promotions == null) {
                Logger.appendToLog("Warning: Promoted servers were not updated because Battly config is unavailable");
                return;
            }
            merge(preferences, gameDirectory, promotions);
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not update promoted servers", throwable);
            Logger.appendToLog("Warning: Promoted servers update failed: " + safeMessage(throwable));
        }
    }

    /** Refreshes the API config and persists promoted servers in every known instance. */
    public static synchronized void syncAllAtLauncherStart(Context context) {
        if (context == null) return;
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            List<Promotion> promotions = loadPromotions(preferences, true);
            if (promotions == null) {
                Logger.appendToLog("Warning: Promoted servers were not updated because Battly config is unavailable");
                return;
            }

            List<File> gameDirectories = knownGameDirectories();
            int synchronizedDirectories = 0;
            for (File gameDirectory : gameDirectories) {
                try {
                    merge(preferences, gameDirectory, promotions);
                    synchronizedDirectories++;
                } catch (IOException exception) {
                    Log.w(TAG, "Could not update promoted servers for " + gameDirectory, exception);
                }
            }
            Logger.appendToLog("Info: Promoted servers refreshed at Battly startup for "
                    + synchronizedDirectories + "/" + gameDirectories.size() + " instances");
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not refresh promoted servers at Battly startup", throwable);
            Logger.appendToLog("Warning: Promoted servers startup refresh failed: " + safeMessage(throwable));
        }
    }

    private static List<Promotion> loadPromotions(SharedPreferences preferences, boolean forceRefresh)
            throws Exception {
        long now = System.currentTimeMillis();
        String cached = preferences.getString(CACHE_JSON, null);
        if (!forceRefresh && cached != null
                && now - preferences.getLong(CACHE_TIME, 0L) < CACHE_TTL_MS) {
            return parsePromotions(cached);
        }
        try {
            String fresh = fetchConfig();
            List<Promotion> parsed = parsePromotions(fresh);
            preferences.edit().putString(CACHE_JSON, fresh).putLong(CACHE_TIME, now).apply();
            return parsed;
        } catch (Exception networkError) {
            if (cached != null) {
                Log.w(TAG, "Using cached promoted server config", networkError);
                return parsePromotions(cached);
            }
            throw networkError;
        }
    }

    private static List<File> knownGameDirectories() {
        LinkedHashMap<String, File> directories = new LinkedHashMap<>();
        addGameDirectory(directories, new File(Tools.DIR_GAME_NEW));

        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles != null) {
            List<MinecraftProfile> profiles = new ArrayList<>(
                    LauncherProfiles.mainProfileJson.profiles.values());
            for (MinecraftProfile profile : profiles) {
                if (profile != null) addGameDirectory(directories, Tools.getGameDirPath(profile));
            }
        }
        return new ArrayList<>(directories.values());
    }

    private static void addGameDirectory(Map<String, File> directories, File directory) {
        if (directory == null) return;
        File canonical;
        try {
            canonical = directory.getCanonicalFile();
        } catch (IOException ignored) {
            canonical = directory.getAbsoluteFile();
        }
        directories.put(canonical.getAbsolutePath(), canonical);
    }

    private static String fetchConfig() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(CONFIG_URL).openConnection();
        connection.setConnectTimeout(3500);
        connection.setReadTimeout(4500);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Battly-Mobile/2.0.3");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Battly config returned HTTP " + status);
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                return new String(readLimited(input, MAX_CONFIG_BYTES), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static List<Promotion> parsePromotions(String json) throws Exception {
        JSONArray entries = new JSONObject(json).optJSONArray("promoted_servers");
        if (entries == null) return Collections.emptyList();
        LinkedHashMap<String, Promotion> unique = new LinkedHashMap<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject item = entries.optJSONObject(index);
            if (item == null) continue;
            String ip = clean(item.optString("ip", ""));
            String name = clean(item.optString("name", ""));
            if (ip.isEmpty() || name.isEmpty() || ip.length() > 255 || name.length() > 255) continue;
            String icon = item.optString("icon", "");
            if (icon.length() > 512 * 1024) icon = "";
            unique.put(normalizeIp(ip), new Promotion(name, ip, icon, item.optBoolean("enabled", false)));
        }
        return new ArrayList<>(unique.values());
    }

    private static void merge(SharedPreferences preferences, File gameDirectory,
                              List<Promotion> promotions) throws IOException {
        File serversFile = new File(gameDirectory, "servers.dat");
        String preferenceKey = MANAGED_PREFIX + Integer.toHexString(canonicalPath(gameDirectory).hashCode());
        Set<String> previousManaged = new LinkedHashSet<>(
                preferences.getStringSet(preferenceKey, Collections.emptySet()));
        Set<String> currentManaged = new LinkedHashSet<>();
        for (Promotion promotion : promotions) currentManaged.add(normalizeIp(promotion.ip));

        if (!serversFile.isFile() && !hasEnabled(promotions)) {
            preferences.edit().putStringSet(preferenceKey, currentManaged).apply();
            return;
        }

        NbtDocument document = serversFile.isFile() ? NbtIo.read(serversFile) : NbtDocument.empty();
        NbtTag serversTag = document.rootCompound().get("servers");
        NbtList servers;
        if (serversTag == null) {
            servers = new NbtList(NbtIo.COMPOUND, new ArrayList<>());
            document.rootCompound().put("servers", new NbtTag(NbtIo.LIST, servers));
        } else if (serversTag.type == NbtIo.LIST
                && ((NbtList) serversTag.value).elementType == NbtIo.COMPOUND) {
            servers = (NbtList) serversTag.value;
        } else {
            throw new IOException("servers.dat has an unsupported servers tag");
        }

        Set<String> replace = new LinkedHashSet<>(previousManaged);
        replace.addAll(currentManaged);
        List<NbtTag> merged = new ArrayList<>();
        for (Promotion promotion : promotions) {
            if (promotion.enabled) merged.add(serverTag(promotion));
        }
        Set<String> seen = new LinkedHashSet<>(currentManaged);
        for (NbtTag existing : servers.values) {
            String ip = serverIp(existing);
            String normalized = normalizeIp(ip);
            if (!normalized.isEmpty() && (replace.contains(normalized) || seen.contains(normalized))) continue;
            if (!normalized.isEmpty()) seen.add(normalized);
            merged.add(existing);
        }
        servers.values.clear();
        servers.values.addAll(merged);
        NbtIo.writeAtomic(serversFile, document);
        preferences.edit().putStringSet(preferenceKey, currentManaged).apply();
        Logger.appendToLog("Info: Promoted servers synchronized: " + enabledCount(promotions)
                + " active, " + merged.size() + " total");
    }

    private static NbtTag serverTag(Promotion promotion) {
        Map<String, NbtTag> compound = new LinkedHashMap<>();
        compound.put("name", new NbtTag(NbtIo.STRING, promotion.name));
        compound.put("ip", new NbtTag(NbtIo.STRING, promotion.ip));
        if (!promotion.icon.isEmpty()) compound.put("icon", new NbtTag(NbtIo.STRING, promotion.icon));
        compound.put("acceptTextures", new NbtTag(NbtIo.BYTE, (byte) 0));
        return new NbtTag(NbtIo.COMPOUND, compound);
    }

    @SuppressWarnings("unchecked")
    private static String serverIp(NbtTag tag) {
        if (tag == null || tag.type != NbtIo.COMPOUND) return "";
        NbtTag ip = ((Map<String, NbtTag>) tag.value).get("ip");
        return ip != null && ip.type == NbtIo.STRING ? String.valueOf(ip.value) : "";
    }

    private static boolean hasEnabled(List<Promotion> promotions) {
        for (Promotion promotion : promotions) if (promotion.enabled) return true;
        return false;
    }

    private static int enabledCount(List<Promotion> promotions) {
        int count = 0;
        for (Promotion promotion : promotions) if (promotion.enabled) count++;
        return count;
    }

    private static String normalizeIp(String ip) {
        return clean(ip).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName() : message;
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("Response exceeds " + limit + " bytes");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class Promotion {
        final String name;
        final String ip;
        final String icon;
        final boolean enabled;

        Promotion(String name, String ip, String icon, boolean enabled) {
            this.name = name;
            this.ip = ip;
            this.icon = icon;
            this.enabled = enabled;
        }
    }

    private static final class NbtDocument {
        final String rootName;
        final NbtTag root;
        final boolean compressed;

        NbtDocument(String rootName, NbtTag root, boolean compressed) {
            this.rootName = rootName;
            this.root = root;
            this.compressed = compressed;
        }

        static NbtDocument empty() {
            return new NbtDocument("", new NbtTag(NbtIo.COMPOUND, new LinkedHashMap<>()), true);
        }

        @SuppressWarnings("unchecked")
        Map<String, NbtTag> rootCompound() throws IOException {
            if (root.type != NbtIo.COMPOUND) throw new IOException("NBT root is not a compound");
            return (Map<String, NbtTag>) root.value;
        }
    }

    private static final class NbtTag {
        final byte type;
        final Object value;

        NbtTag(byte type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private static final class NbtList {
        final byte elementType;
        final List<NbtTag> values;

        NbtList(byte elementType, List<NbtTag> values) {
            this.elementType = elementType;
            this.values = values;
        }
    }

    private static final class NbtIo {
        static final byte END = 0;
        static final byte BYTE = 1;
        static final byte SHORT = 2;
        static final byte INT = 3;
        static final byte LONG = 4;
        static final byte FLOAT = 5;
        static final byte DOUBLE = 6;
        static final byte BYTE_ARRAY = 7;
        static final byte STRING = 8;
        static final byte LIST = 9;
        static final byte COMPOUND = 10;
        static final byte INT_ARRAY = 11;
        static final byte LONG_ARRAY = 12;

        static NbtDocument read(File file) throws IOException {
            byte[] raw;
            try (FileInputStream input = new FileInputStream(file)) {
                raw = readLimited(input, MAX_NBT_BYTES);
            }
            boolean gzip = raw.length >= 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b;
            InputStream source = new ByteArrayInputStream(raw);
            if (gzip) source = new GZIPInputStream(source);
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(source))) {
                byte type = input.readByte();
                if (type != COMPOUND) throw new IOException("Invalid servers.dat root tag " + type);
                String name = input.readUTF();
                return new NbtDocument(name, new NbtTag(type, readPayload(input, type, 0)), gzip);
            } catch (EOFException exception) {
                throw new IOException("Truncated servers.dat", exception);
            }
        }

        static void writeAtomic(File target, NbtDocument document) throws IOException {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create game directory");
            }
            File temp = new File(parent, target.getName() + ".battly.tmp");
            File backup = new File(parent, target.getName() + ".battly.bak");
            if (temp.exists() && !temp.delete()) throw new IOException("Could not clear temporary servers.dat");
            try (FileOutputStream fileOutput = new FileOutputStream(temp)) {
                GZIPOutputStream gzipOutput = document.compressed ? new GZIPOutputStream(fileOutput) : null;
                OutputStream payloadOutput = gzipOutput == null ? fileOutput : gzipOutput;
                DataOutputStream output = new DataOutputStream(payloadOutput);
                output.writeByte(document.root.type);
                output.writeUTF(document.rootName);
                writePayload(output, document.root);
                output.flush();
                if (gzipOutput != null) gzipOutput.finish();
                payloadOutput.flush();
                fileOutput.getFD().sync();
            }
            if (backup.exists() && !backup.delete()) throw new IOException("Could not clear servers.dat backup");
            boolean hadTarget = target.isFile();
            if (hadTarget && !target.renameTo(backup)) throw new IOException("Could not back up servers.dat");
            if (!temp.renameTo(target)) {
                if (hadTarget) backup.renameTo(target);
                throw new IOException("Could not replace servers.dat");
            }
            if (backup.exists() && !backup.delete()) Log.w(TAG, "Could not delete servers.dat backup");
        }

        private static Object readPayload(DataInputStream input, byte type, int depth) throws IOException {
            if (depth > 64) throw new IOException("NBT nesting is too deep");
            switch (type) {
                case BYTE: return input.readByte();
                case SHORT: return input.readShort();
                case INT: return input.readInt();
                case LONG: return input.readLong();
                case FLOAT: return input.readFloat();
                case DOUBLE: return input.readDouble();
                case BYTE_ARRAY: {
                    int size = checkedSize(input.readInt());
                    byte[] bytes = new byte[size];
                    input.readFully(bytes);
                    return bytes;
                }
                case STRING: return input.readUTF();
                case LIST: {
                    byte childType = input.readByte();
                    int size = checkedSize(input.readInt());
                    List<NbtTag> values = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        values.add(new NbtTag(childType, readPayload(input, childType, depth + 1)));
                    }
                    return new NbtList(childType, values);
                }
                case COMPOUND: {
                    Map<String, NbtTag> values = new LinkedHashMap<>();
                    while (true) {
                        byte childType = input.readByte();
                        if (childType == END) return values;
                        String name = input.readUTF();
                        values.put(name, new NbtTag(childType, readPayload(input, childType, depth + 1)));
                        if (values.size() > MAX_COLLECTION_SIZE) throw new IOException("NBT compound is too large");
                    }
                }
                case INT_ARRAY: {
                    int size = checkedSize(input.readInt());
                    int[] values = new int[size];
                    for (int i = 0; i < size; i++) values[i] = input.readInt();
                    return values;
                }
                case LONG_ARRAY: {
                    int size = checkedSize(input.readInt());
                    long[] values = new long[size];
                    for (int i = 0; i < size; i++) values[i] = input.readLong();
                    return values;
                }
                default: throw new IOException("Unsupported NBT tag " + type);
            }
        }

        @SuppressWarnings("unchecked")
        private static void writePayload(DataOutputStream output, NbtTag tag) throws IOException {
            switch (tag.type) {
                case BYTE: output.writeByte((Byte) tag.value); break;
                case SHORT: output.writeShort((Short) tag.value); break;
                case INT: output.writeInt((Integer) tag.value); break;
                case LONG: output.writeLong((Long) tag.value); break;
                case FLOAT: output.writeFloat((Float) tag.value); break;
                case DOUBLE: output.writeDouble((Double) tag.value); break;
                case BYTE_ARRAY: {
                    byte[] values = (byte[]) tag.value;
                    output.writeInt(values.length);
                    output.write(values);
                    break;
                }
                case STRING: output.writeUTF((String) tag.value); break;
                case LIST: {
                    NbtList list = (NbtList) tag.value;
                    output.writeByte(list.elementType);
                    output.writeInt(list.values.size());
                    for (NbtTag value : list.values) writePayload(output, value);
                    break;
                }
                case COMPOUND: {
                    for (Map.Entry<String, NbtTag> entry : ((Map<String, NbtTag>) tag.value).entrySet()) {
                        output.writeByte(entry.getValue().type);
                        output.writeUTF(entry.getKey());
                        writePayload(output, entry.getValue());
                    }
                    output.writeByte(END);
                    break;
                }
                case INT_ARRAY: {
                    int[] values = (int[]) tag.value;
                    output.writeInt(values.length);
                    for (int value : values) output.writeInt(value);
                    break;
                }
                case LONG_ARRAY: {
                    long[] values = (long[]) tag.value;
                    output.writeInt(values.length);
                    for (long value : values) output.writeLong(value);
                    break;
                }
                default: throw new IOException("Unsupported NBT tag " + tag.type);
            }
        }

        private static int checkedSize(int size) throws IOException {
            if (size < 0 || size > MAX_COLLECTION_SIZE) throw new IOException("Invalid NBT collection size " + size);
            return size;
        }
    }
}
