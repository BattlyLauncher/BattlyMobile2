package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Named, per-instance sets of enabled mods and packs. */
public final class ContentProfileManager {
    private ContentProfileManager() {
    }

    public static File save(@NonNull MinecraftProfile profile, @NonNull String name) throws IOException {
        File game = Tools.getGameDirPath(profile);
        JSONObject root = new JSONObject();
        try {
            root.put("schema", "battly-content-profile-v1");
            root.put("name", name.trim());
            root.put("createdAt", System.currentTimeMillis());
            root.put("mods", enabledFiles(new File(game, "mods"), ".disabledmod"));
            root.put("shaders", enabledFiles(new File(game, "shaderpacks"), ".disabledshader"));
            root.put("datapacks", enabledFiles(new File(game, "datapacks"), ".disableddatapack"));
            root.put("resourcepacks", enabledResourcePacks(game));
        } catch (Exception exception) {
            throw new IOException("Unable to serialize content profile", exception);
        }
        File directory = directory(profile);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Unable to create content profiles directory");
        File output = new File(directory, sanitize(name) + ".json");
        Tools.write(output.getAbsolutePath(), root.toString());
        return output;
    }

    public static List<File> list(@NonNull MinecraftProfile profile) {
        File[] files = directory(profile).listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
        List<File> result = new ArrayList<>();
        if (files != null) java.util.Collections.addAll(result, files);
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    public static void apply(@NonNull MinecraftProfile profile, @NonNull File contentProfile) throws IOException {
        try {
            JSONObject root = new JSONObject(Tools.read(contentProfile.getAbsolutePath()));
            File game = Tools.getGameDirPath(profile);
            applyDirectory(new File(game, "mods"), set(root.optJSONArray("mods")), ".disabledmod", ".jar");
            applyDirectory(new File(game, "shaderpacks"), set(root.optJSONArray("shaders")), ".disabledshader", ".zip");
            applyDirectory(new File(game, "datapacks"), set(root.optJSONArray("datapacks")), ".disableddatapack", ".zip");
            applyResourcePacks(game, set(root.optJSONArray("resourcepacks")));
        } catch (Exception exception) {
            if (exception instanceof IOException) throw (IOException) exception;
            throw new IOException("Invalid content profile", exception);
        }
    }

    private static JSONArray enabledFiles(File directory, String disabledSuffix) {
        JSONArray array = new JSONArray();
        File[] files = directory.listFiles(file -> !file.getName().startsWith(".") && !file.getName().endsWith(disabledSuffix));
        if (files != null) for (File file : files) array.put(baseName(file.getName(), disabledSuffix));
        return array;
    }

    private static JSONArray enabledResourcePacks(File game) {
        JSONArray result = new JSONArray();
        try {
            String options = Tools.read(new File(game, "options.txt").getAbsolutePath());
            java.util.regex.Matcher line = java.util.regex.Pattern.compile("(?m)^resourcePacks:(.*)$").matcher(options);
            if (line.find()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"(?:file/)?([^\"]+)\"").matcher(line.group(1));
                while (matcher.find()) result.put(matcher.group(1));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void applyDirectory(File directory, Set<String> enabled, String disabledSuffix, String enabledSuffix) {
        File[] files = directory.listFiles(file -> !file.getName().startsWith("."));
        if (files == null) return;
        for (File file : files) {
            String base = baseName(file.getName(), disabledSuffix);
            boolean shouldEnable = enabled.contains(base);
            boolean isDisabled = file.getName().endsWith(disabledSuffix);
            if (shouldEnable == !isDisabled) continue;
            String targetName = shouldEnable ? stripSuffix(file.getName(), disabledSuffix) + enabledSuffix
                    : stripKnownExtension(file.getName()) + disabledSuffix;
            File target = new File(directory, targetName);
            if (!target.exists()) file.renameTo(target);
        }
    }

    private static void applyResourcePacks(File game, Set<String> enabled) throws IOException {
        File options = new File(game, "options.txt");
        String text = options.isFile() ? Tools.read(options.getAbsolutePath()) : "";
        StringBuilder list = new StringBuilder("resourcePacks:[");
        for (String name : enabled) {
            if (list.length() > "resourcePacks:[".length()) list.append(',');
            list.append('"').append("file/").append(name.replace("\"", "")).append('"');
        }
        list.append(']');
        if (java.util.regex.Pattern.compile("(?m)^resourcePacks:.*$").matcher(text).find()) {
            text = text.replaceAll("(?m)^resourcePacks:.*$", list.toString());
        }
        else text += (text.endsWith("\n") || text.isEmpty() ? "" : "\n") + list + "\n";
        Tools.write(options.getAbsolutePath(), text);
    }

    private static Set<String> set(JSONArray array) {
        Set<String> values = new HashSet<>();
        if (array != null) for (int i = 0; i < array.length(); i++) values.add(array.optString(i));
        return values;
    }

    private static File directory(MinecraftProfile profile) {
        return new File(Tools.getGameDirPath(profile), ".battly/content-profiles");
    }

    private static String baseName(String name, String disabledSuffix) {
        return stripKnownExtension(stripSuffix(name, disabledSuffix));
    }
    private static String stripSuffix(String value, String suffix) { return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value; }
    private static String stripKnownExtension(String value) { return value.replaceFirst("(?i)\\.(jar|zip)$", ""); }
    private static String sanitize(String value) {
        String result = value.trim().replaceAll("[^A-Za-z0-9._-]+", "-").toLowerCase(Locale.ROOT);
        return result.isEmpty() ? "profile" : result;
    }
}
