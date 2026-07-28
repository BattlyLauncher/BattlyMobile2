package net.kdt.pojavlaunch.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the primary mod-resolution failure from Fabric, Quilt and Forge logs.
 * This class deliberately has no Android dependencies so the parser can be unit tested.
 */
public final class ModCompatibilityAnalyzer {
    private static final Pattern STRUCTURED_DEPENDENCY = Pattern.compile(
            "(?:HARD_DEP|HARD_DEP_INCOMPATIBLE_PRESELECTED)\\s+([^\\s{},]+)\\s+([^\\s{},]+)"
                    + "\\s+\\{(?:depends|recommends)\\s+([^\\s@}]+)\\s+@\\s+\\[([^\\r\\n}]+)\\]\\}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPANISH_DEPENDENCY = Pattern.compile(
            "(?:¡)?El mod ['\"]([^'\"]+)['\"] \\(([^)]+)\\)\\s+([^\\s]+)\\s+"
                    + "(?:necesita|requiere)\\s+(.+?)\\s+de ['\"]([^'\"]+)['\"] \\(([^)]+)\\),"
                    + "\\s+pero .*?:\\s*([^!\\r\\n]+)!",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_DEPENDENCY = Pattern.compile(
            "Mod ['\"]([^'\"]+)['\"] \\(([^)]+)\\)\\s+([^\\s]+)\\s+"
                    + "(?:needs|requires)\\s+(.+?)\\s+of ['\"]([^'\"]+)['\"] \\(([^)]+)\\),"
                    + "\\s+but .*?:\\s*([^!\\r\\n]+)!",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPANISH_REPLACE = Pattern.compile(
            "Cambia ['\"]([^'\"]+)['\"] \\(([^)]+)\\)\\s+([^\\s]+)\\s+por la versi[oó]n\\s+([^\\s.]+(?:\\.[^\\s.]+)*)\\.?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_REPLACE = Pattern.compile(
            "(?:Replace|Change) ['\"]?([^'\"(]+?)['\"]?\\s*\\(([^)]+)\\)\\s+([^\\s]+)"
                    + "\\s+(?:with|to)\\s+(?:version\\s+)?([^\\s.]+(?:\\.[^\\s.]+)*)\\.?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUCTURED_REPLACE = Pattern.compile(
            "\\[([^\\s\\]]+)\\s+([^\\]]+)]\\s*->\\s*add:([^\\s]+)\\s+([^\\s(\\]]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGE_LANGUAGE = Pattern.compile(
            "Mod File\\s+([^\\r\\n]+?\\.jar)\\s+needs language provider\\s+([^:\\s]+):([^\\s]+)"
                    + "\\s+or above.*?found\\s+([^\\s\\r\\n]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FORGE_MISSING_MOD = Pattern.compile(
            "(?:Mod\\s+['\"]?([^'\"\\s]+)['\"]?|Mod ID:\\s*([^,\\s]+)).{0,180}?"
                    + "(?:requires|needs|depends on)\\s+(?:mod\\s+)?['\"]?([^'\"\\s,]+)['\"]?"
                    + "(?:\\s+(?:version|range)\\s+([^\\r\\n,]+))?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private ModCompatibilityAnalyzer() {
    }

    public static Analysis analyze(String source) {
        if (source == null || source.trim().isEmpty()) return Analysis.none();
        String lower = source.toLowerCase(Locale.ROOT);
        if (!containsMarker(lower)) return Analysis.none();

        String loader = detectLoader(lower);
        LinkedHashMap<String, MutableIssue> issues = new LinkedHashMap<>();
        parseStructuredDependencies(source, issues);
        parseReadableDependencies(source, SPANISH_DEPENDENCY, issues);
        parseReadableDependencies(source, ENGLISH_DEPENDENCY, issues);
        parseForgeFailures(source, issues);

        Replacement replacement = parseReplacement(source);
        List<Issue> immutableIssues = new ArrayList<>();
        for (MutableIssue issue : issues.values()) immutableIssues.add(issue.freeze());

        String summary = buildSummary(replacement, immutableIssues);
        String solution = replacement == null ? "" : replacement.toSentence();
        String primaryExcerpt = primaryExcerpt(source);
        return new Analysis(true, loader, summary, solution,
                replacement == null ? "" : replacement.currentMinecraftVersion(),
                replacement == null ? "" : replacement.requiredMinecraftVersion(),
                Collections.unmodifiableList(immutableIssues), primaryExcerpt);
    }

    private static boolean containsMarker(String lower) {
        return lower.contains("incompatible mods found")
                || lower.contains("mod resolution failed")
                || lower.contains("mod resolution encountered")
                || lower.contains("some of your mods are incompatible")
                || lower.contains("depends minecraft")
                || lower.contains("needs minecraft")
                || lower.contains("cambia 'minecraft'")
                || lower.contains("cambia \"minecraft\"")
                || lower.contains("formattedexception")
                || lower.contains("modloadingexception")
                || lower.contains("missing mandatory dependencies")
                || lower.contains("needs language provider javafml");
    }

    private static String detectLoader(String lower) {
        if (lower.contains("net.fabricmc") || lower.contains("fabric loader")
                || lower.contains("fabric-api")) return "Fabric";
        if (lower.contains("org.quiltmc") || lower.contains("quilt loader")) return "Quilt";
        if (lower.contains("net.neoforged") || lower.contains("neoforge")) return "NeoForge";
        if (lower.contains("net.minecraftforge") || lower.contains("javafml")
                || lower.contains("modloadingexception")) return "Forge";
        return "Mod loader";
    }

    private static void parseStructuredDependencies(String source,
                                                    Map<String, MutableIssue> issues) {
        Matcher matcher = STRUCTURED_DEPENDENCY.matcher(source);
        while (matcher.find()) {
            MutableIssue issue = issue(issues, matcher.group(1), matcher.group(2),
                    matcher.group(3), cleanRequirement(matcher.group(4)));
            if ("minecraft".equalsIgnoreCase(issue.dependencyId)) {
                issue.dependencyName = "Minecraft";
            } else if ("java".equalsIgnoreCase(issue.dependencyId)) {
                issue.dependencyName = "Java";
            }
        }
    }

    private static void parseReadableDependencies(String source, Pattern pattern,
                                                  Map<String, MutableIssue> issues) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String modName = matcher.group(1).trim();
            String modId = matcher.group(2).trim();
            String installedVersion = matcher.group(3).trim();
            String requirement = matcher.group(4).trim();
            String dependencyName = matcher.group(5).trim();
            String dependencyId = matcher.group(6).trim();
            String currentVersion = matcher.group(7).trim();
            MutableIssue issue = issue(issues, modId, installedVersion, dependencyId, requirement);
            issue.modName = modName;
            issue.dependencyName = dependencyName;
            issue.currentVersion = currentVersion;
        }
    }

    private static void parseForgeFailures(String source, Map<String, MutableIssue> issues) {
        Matcher language = FORGE_LANGUAGE.matcher(source);
        if (language.find()) {
            MutableIssue issue = issue(issues, fileBaseName(language.group(1)), "",
                    language.group(2), language.group(3) + "+");
            issue.modName = fileBaseName(language.group(1));
            issue.dependencyName = language.group(2);
            issue.currentVersion = language.group(4);
        }

        Matcher missing = FORGE_MISSING_MOD.matcher(source);
        while (missing.find()) {
            String modId = value(missing.group(1), missing.group(2));
            if (modId == null) continue;
            issue(issues, modId, "", missing.group(3),
                    missing.group(4) == null ? "required" : missing.group(4).trim());
        }
    }

    private static MutableIssue issue(Map<String, MutableIssue> issues, String modId,
                                      String installedVersion, String dependencyId,
                                      String requirement) {
        String key = normalize(modId) + "|" + normalize(dependencyId);
        MutableIssue issue = issues.get(key);
        if (issue == null) {
            issue = new MutableIssue(modId, installedVersion, dependencyId, requirement);
            issues.put(key, issue);
        }
        return issue;
    }

    private static Replacement parseReplacement(String source) {
        Matcher spanish = SPANISH_REPLACE.matcher(source);
        if (spanish.find()) {
            return new Replacement(spanish.group(1).trim(), spanish.group(2).trim(),
                    spanish.group(3).trim(), spanish.group(4).trim());
        }
        Matcher english = ENGLISH_REPLACE.matcher(source);
        if (english.find()) {
            return new Replacement(english.group(1).trim(), english.group(2).trim(),
                    english.group(3).trim(), english.group(4).trim());
        }
        Matcher structured = STRUCTURED_REPLACE.matcher(source);
        if (structured.find() && structured.group(1).equalsIgnoreCase(structured.group(3))) {
            String id = structured.group(1).trim();
            String name = "minecraft".equalsIgnoreCase(id) ? "Minecraft" : id;
            return new Replacement(name, id, structured.group(2).trim(), structured.group(4).trim());
        }
        return null;
    }

    private static String buildSummary(Replacement replacement, List<Issue> issues) {
        if (replacement != null && "minecraft".equalsIgnoreCase(replacement.id)) {
            return "This modpack is not compatible with Minecraft " + replacement.currentVersion
                    + ". " + countLabel(issues.size()) + " require Minecraft "
                    + replacement.requiredVersion + ".";
        }
        if (!issues.isEmpty()) {
            return countLabel(issues.size())
                    + " have missing or incompatible dependencies.";
        }
        return "The mod loader found incompatible mods or unsatisfied dependencies.";
    }

    private static String countLabel(int count) {
        if (count == 1) return "One mod";
        if (count > 1) return count + " mods";
        return "Some mods";
    }

    private static String primaryExcerpt(String source) {
        int start = firstIndex(source,
                "Mod resolution failed", "Incompatible mods found", "Some of your mods are incompatible",
                "Missing mandatory dependencies", "ModLoadingException");
        if (start < 0) start = 0;
        int awt = firstIndexAfter(source, start,
                "java.awt.Insets.initIDs", "FabricMainWindow.open", "FabricGuiEntry.open");
        int end = awt >= 0 ? awt : Math.min(source.length(), start + 24_000);
        return source.substring(start, end).trim();
    }

    private static int firstIndex(String source, String... values) {
        int result = -1;
        for (String value : values) {
            int index = source.indexOf(value);
            if (index >= 0 && (result < 0 || index < result)) result = index;
        }
        return result;
    }

    private static int firstIndexAfter(String source, int start, String... values) {
        int result = -1;
        for (String value : values) {
            int index = source.indexOf(value, start);
            if (index >= 0 && (result < 0 || index < result)) result = index;
        }
        return result;
    }

    private static String cleanRequirement(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String fileBaseName(String value) {
        if (value == null) return "";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1).trim() : normalized.trim();
    }

    private static String value(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        if (second != null && !second.trim().isEmpty()) return second.trim();
        return null;
    }

    public static final class Analysis {
        public final boolean detected;
        public final String loader;
        public final String summary;
        public final String solution;
        public final String currentMinecraftVersion;
        public final String recommendedMinecraftVersion;
        public final List<Issue> issues;
        public final String primaryExcerpt;

        private Analysis(boolean detected, String loader, String summary, String solution,
                         String currentMinecraftVersion, String recommendedMinecraftVersion,
                         List<Issue> issues, String primaryExcerpt) {
            this.detected = detected;
            this.loader = loader;
            this.summary = summary;
            this.solution = solution;
            this.currentMinecraftVersion = currentMinecraftVersion;
            this.recommendedMinecraftVersion = recommendedMinecraftVersion;
            this.issues = issues;
            this.primaryExcerpt = primaryExcerpt;
        }

        private static Analysis none() {
            return new Analysis(false, "", "", "", "", "",
                    Collections.emptyList(), "");
        }
    }

    public static final class Issue {
        public final String modName;
        public final String modId;
        public final String installedVersion;
        public final String dependencyName;
        public final String dependencyId;
        public final String requirement;
        public final String currentVersion;

        private Issue(String modName, String modId, String installedVersion,
                      String dependencyName, String dependencyId, String requirement,
                      String currentVersion) {
            this.modName = modName;
            this.modId = modId;
            this.installedVersion = installedVersion;
            this.dependencyName = dependencyName;
            this.dependencyId = dependencyId;
            this.requirement = requirement;
            this.currentVersion = currentVersion;
        }
    }

    private static final class MutableIssue {
        private String modName;
        private final String modId;
        private final String installedVersion;
        private String dependencyName;
        private final String dependencyId;
        private final String requirement;
        private String currentVersion = "";

        private MutableIssue(String modId, String installedVersion, String dependencyId,
                             String requirement) {
            this.modName = modId;
            this.modId = modId;
            this.installedVersion = installedVersion == null ? "" : installedVersion;
            this.dependencyName = dependencyId;
            this.dependencyId = dependencyId;
            this.requirement = requirement == null ? "" : requirement;
        }

        private Issue freeze() {
            return new Issue(modName, modId, installedVersion, dependencyName, dependencyId,
                    requirement, currentVersion);
        }
    }

    private static final class Replacement {
        private final String name;
        private final String id;
        private final String currentVersion;
        private final String requiredVersion;

        private Replacement(String name, String id, String currentVersion,
                            String requiredVersion) {
            this.name = name;
            this.id = id;
            this.currentVersion = currentVersion;
            this.requiredVersion = requiredVersion;
        }

        private String toSentence() {
            return "Recommended solution: change " + name + " from " + currentVersion
                    + " to " + requiredVersion + ".";
        }

        private String currentMinecraftVersion() {
            return "minecraft".equalsIgnoreCase(id) ? currentVersion : "";
        }

        private String requiredMinecraftVersion() {
            return "minecraft".equalsIgnoreCase(id) ? requiredVersion : "";
        }
    }
}
