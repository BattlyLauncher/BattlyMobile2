package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads loader metadata and reports missing, duplicate and declared-conflicting mods. */
public final class ContentDependencyAnalyzer {
    private static final Set<String> PROVIDED = new HashSet<>(Arrays.asList(
            "minecraft", "java", "fabricloader", "fabric-loader", "forge", "neoforge", "quilt_loader"));

    private ContentDependencyAnalyzer() {
    }

    @NonNull
    public static Report analyze(@NonNull File modsDirectory) {
        List<ModMetadata> mods = new ArrayList<>();
        File[] files = modsDirectory.listFiles(file -> file.isFile()
                && (file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
                || file.getName().toLowerCase(Locale.ROOT).endsWith(".disabledmod")));
        if (files != null) for (File file : files) mods.add(read(file));

        Map<String, List<ModMetadata>> byId = new HashMap<>();
        for (ModMetadata mod : mods) {
            if (!mod.enabled) continue;
            for (String id : mod.ids) byId.computeIfAbsent(normalize(id), ignored -> new ArrayList<>()).add(mod);
        }
        List<Issue> issues = new ArrayList<>();
        for (Map.Entry<String, List<ModMetadata>> entry : byId.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(new Issue(Severity.ERROR, "Duplicate mod: " + entry.getKey(),
                        joinFiles(entry.getValue())));
            }
        }
        for (ModMetadata mod : mods) {
            if (!mod.enabled) continue;
            for (String required : mod.required) {
                String id = normalize(required);
                if (!PROVIDED.contains(id) && !byId.containsKey(id)) {
                    issues.add(new Issue(Severity.ERROR, "Missing dependency: " + required,
                            mod.displayName + " requires it"));
                }
            }
            for (String conflict : mod.conflicts) {
                String id = normalize(conflict);
                if (byId.containsKey(id)) {
                    issues.add(new Issue(Severity.WARNING, "Declared conflict: " + conflict,
                            mod.displayName + " may not work with " + joinFiles(byId.get(id))));
                }
            }
            if (mod.ids.isEmpty()) {
                issues.add(new Issue(Severity.INFO, "Metadata not recognized", mod.file.getName()));
            }
        }
        return new Report(mods, issues);
    }

    private static ModMetadata read(File file) {
        ModMetadata result = new ModMetadata(file);
        File archive = file;
        try (ZipFile zip = new ZipFile(archive)) {
            String fabric = read(zip, "fabric.mod.json");
            if (fabric != null) parseFabric(result, new JSONObject(fabric));
            String quilt = read(zip, "quilt.mod.json");
            if (quilt != null) parseQuilt(result, new JSONObject(quilt));
            String toml = read(zip, "META-INF/mods.toml");
            if (toml != null) parseToml(result, toml);
            String neoToml = read(zip, "META-INF/neoforge.mods.toml");
            if (neoToml != null) parseToml(result, neoToml);
            String legacy = read(zip, "mcmod.info");
            if (legacy != null) parseLegacy(result, legacy);
        } catch (Exception ignored) {
        }
        if (result.ids.isEmpty()) {
            result.displayName = friendlyFileName(file.getName());
            add(result.ids, fallbackModId(file.getName()));
            result.metadataInferred = true;
        }
        return result;
    }

    private static void parseFabric(ModMetadata target, JSONObject json) {
        add(target.ids, json.optString("id"));
        target.displayName = json.optString("name", target.displayName);
        keys(json.optJSONObject("depends"), target.required);
        keys(json.optJSONObject("breaks"), target.conflicts);
        keys(json.optJSONObject("conflicts"), target.conflicts);
    }

    private static void parseQuilt(ModMetadata target, JSONObject root) {
        JSONObject loader = root.optJSONObject("quilt_loader");
        if (loader == null) return;
        add(target.ids, loader.optString("id"));
        JSONObject metadata = loader.optJSONObject("metadata");
        if (metadata != null) target.displayName = metadata.optString("name", target.displayName);
        parseQuiltDependencies(loader.optJSONArray("depends"), target.required);
        parseQuiltDependencies(loader.optJSONArray("breaks"), target.conflicts);
    }

    private static void parseQuiltDependencies(JSONArray dependencies, Set<String> output) {
        if (dependencies == null) return;
        for (int i = 0; i < dependencies.length(); i++) {
            Object value = dependencies.opt(i);
            if (value instanceof String) add(output, (String) value);
            else if (value instanceof JSONObject) add(output, ((JSONObject) value).optString("id"));
        }
    }

    private static void parseToml(ModMetadata target, String text) {
        Matcher ids = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']([^\"']+)").matcher(text);
        while (ids.find()) add(target.ids, ids.group(1));
        Matcher names = Pattern.compile("(?m)^\\s*displayName\\s*=\\s*[\"']([^\"']+)").matcher(text);
        if (names.find()) target.displayName = names.group(1);
        Matcher blocks = Pattern.compile("(?s)\\[\\[dependencies\\.[^]]+]](.*?)(?=\\[\\[|\\z)").matcher(text);
        while (blocks.find()) {
            String block = blocks.group(1);
            Matcher id = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']([^\"']+)").matcher(block);
            if (!id.find()) continue;
            Matcher mandatory = Pattern.compile("(?m)^\\s*mandatory\\s*=\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(block);
            if (!mandatory.find() || Boolean.parseBoolean(mandatory.group(1))) add(target.required, id.group(1));
        }
    }

    private static void parseLegacy(ModMetadata target, String text) {
        try {
            JSONArray array;
            if (text.trim().startsWith("[")) {
                array = new JSONArray(text);
            } else {
                JSONObject root = new JSONObject(text);
                array = root.optJSONArray("modList");
                if (array == null) array = new JSONArray().put(root);
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                add(target.ids, object.optString("modid"));
                target.displayName = object.optString("name", target.displayName);
                JSONArray deps = object.optJSONArray("requiredMods");
                if (deps != null) for (int j = 0; j < deps.length(); j++) add(target.required, cleanDependency(deps.optString(j)));
            }
        } catch (Exception ignored) {
        }
    }

    private static void keys(JSONObject object, Set<String> output) {
        if (object == null) return;
        java.util.Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) add(output, iterator.next());
    }

    private static String read(ZipFile zip, String name) {
        try {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.getSize() > 2 * 1024 * 1024) return null;
            try (InputStream input = zip.getInputStream(entry)) { return Tools.read(input); }
        } catch (Exception ignored) { return null; }
    }

    private static String joinFiles(List<ModMetadata> mods) {
        StringBuilder value = new StringBuilder();
        for (ModMetadata mod : mods) {
            if (value.length() > 0) value.append(", ");
            value.append(mod.file.getName());
        }
        return value.toString();
    }

    private static String normalize(String id) { return cleanDependency(id).toLowerCase(Locale.ROOT); }
    private static String cleanDependency(String id) {
        if (id == null) return "";
        return id.replaceFirst("^[a-z]+:", "").replaceAll("[@<>=~ ].*$", "").trim();
    }
    private static void add(Set<String> output, String value) { if (Tools.isValidString(value)) output.add(cleanDependency(value)); }
    private static String stripExtension(String name) { int dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(0, dot) : name; }

    private static String friendlyFileName(String name) {
        String value = stripExtension(name)
                .replaceFirst("(?i)^\\[[^]]+]\\s*", "")
                .replaceAll("[_-]+", " ")
                .replaceFirst("(?i)\\s+v?\\d+(?:[._+-].*)?$", "")
                .trim();
        return Tools.isValidString(value) ? value : stripExtension(name);
    }

    private static String fallbackModId(String name) {
        String value = friendlyFileName(name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .trim();
        return Tools.isValidString(value) ? value : "unknown_" + Integer.toHexString(name.hashCode());
    }

    public enum Severity { ERROR, WARNING, INFO }

    public static final class Issue {
        public final Severity severity;
        public final String title;
        public final String detail;
        Issue(Severity severity, String title, String detail) { this.severity = severity; this.title = title; this.detail = detail; }
    }

    public static final class ModMetadata {
        public final File file;
        public final boolean enabled;
        public final Set<String> ids = new HashSet<>();
        public final Set<String> required = new HashSet<>();
        public final Set<String> conflicts = new HashSet<>();
        public String displayName;
        public boolean metadataInferred;
        ModMetadata(File file) {
            this.file = file;
            enabled = !file.getName().toLowerCase(Locale.ROOT).endsWith(".disabledmod");
            displayName = stripExtension(file.getName());
        }
    }

    public static final class Report {
        public final List<ModMetadata> mods;
        public final List<Issue> issues;
        Report(List<ModMetadata> mods, List<Issue> issues) { this.mods = mods; this.issues = issues; }
        public boolean healthy() { return issues.isEmpty(); }
    }
}
