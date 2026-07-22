package net.kdt.pojavlaunch.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central compatibility policy for Minecraft, Java, LWJGL and Android renderers.
 * It deliberately consumes existing profile/version data instead of introducing a
 * second version database, so old launcher profiles remain valid.
 */
public final class MinecraftCompatibilityEngine {
    private static final Pattern RELEASE = Pattern.compile("(?:^|[^0-9])(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private MinecraftCompatibilityEngine() {
    }

    @NonNull
    public static Report evaluate(@NonNull Context context, @Nullable String versionId,
                                  @Nullable JMinecraftVersionList.Version version,
                                  @Nullable String requestedRenderer) {
        String effectiveVersion = firstNonEmpty(versionId, version == null ? null : version.id, "unknown");
        VersionFamily family = VersionFamily.parse(effectiveVersion,
                version == null ? null : version.inheritsFrom);
        int requiredJava = version != null && version.javaVersion != null
                ? version.javaVersion.majorVersion : inferJava(family);
        String lwjgl = inferLwjgl(version, family);
        List<String> issues = new ArrayList<>();

        String renderer = requestedRenderer;
        boolean userRendererValid = Tools.isValidString(renderer)
                && Tools.checkRendererCompatible(context, renderer);
        if (!userRendererValid) {
            if (Tools.isValidString(renderer)) {
                issues.add("Renderer unavailable on this device: " + renderer);
            }
            renderer = recommendRenderer(context, family);
        }

        String runtime = MultiRTUtils.getExactJreName(requiredJava);
        if (runtime == null) {
            issues.add("Java " + requiredJava + " is not installed");
        }
        if (family.modern && "opengles2".equals(renderer)) {
            issues.add("GL4ES is a compatibility fallback for this Minecraft generation");
        }
        return new Report(effectiveVersion, family, requiredJava, lwjgl,
                renderer, runtime, Collections.unmodifiableList(issues));
    }

    @NonNull
    private static String recommendRenderer(@NonNull Context context, @NonNull VersionFamily family) {
        List<String> compatible = Tools.getCompatibleRenderers(context).rendererIds;
        if (family.requiresDesktopGl) {
            GLInfoUtils.GLInfo graphics = GLInfoUtils.getGlInfo();
            // The Android-owned MobileGlues surface is the safest automatic path for
            // 26.x. Freedreno remains available manually, but some A7xx drivers crash
            // in libvulkan_freedreno before Minecraft can create its first frame.
            if (compatible.contains("opengles_mobileglues")) {
                return "opengles_mobileglues";
            }
            if (graphics.isAdreno() && compatible.contains("opengles3_desktopgl_freedreno")) {
                return "opengles3_desktopgl_freedreno";
            }
            // Zink/Kopper can expose a portrait-oriented swapchain on Xclipse and Mali even
            // though Android and GLFW report landscape dimensions. MobileGlues owns the Android
            // surface directly and is the stable automatic path for those GPU families.
            if (compatible.contains("opengles3_desktopgl_zink_kopper")) {
                return "opengles3_desktopgl_zink_kopper";
            }
        }
        if (family.modern && compatible.contains("opengles_mobileglues")) {
            return "opengles_mobileglues";
        }
        if (family.modern && compatible.contains("opengles3_desktopgl_freedreno")) {
            return "opengles3_desktopgl_freedreno";
        }
        if (family.modern && compatible.contains("opengles3_desktopgl_zink_kopper")) {
            return "opengles3_desktopgl_zink_kopper";
        }
        if (family.modern) {
            for (String renderer : compatible) {
                if (!"opengles2".equals(renderer)) {
                    return renderer;
                }
            }
        }
        if (compatible.contains("opengles2")) {
            return "opengles2";
        }
        return compatible.isEmpty() ? "opengles2" : compatible.get(0);
    }

    private static int inferJava(VersionFamily family) {
        if (family.yearVersion >= 26 || family.minor >= 21 && family.patch >= 9) return 25;
        if (family.minor >= 20 && family.patch >= 5 || family.minor >= 21) return 21;
        if (family.minor >= 18) return 17;
        return 8;
    }

    private static String inferLwjgl(@Nullable JMinecraftVersionList.Version version,
                                     @NonNull VersionFamily family) {
        if (version != null && version.libraries != null) {
            for (net.kdt.pojavlaunch.value.DependentLibrary library : version.libraries) {
                if (library == null || library.name == null || !library.name.startsWith("org.lwjgl:lwjgl:")) {
                    continue;
                }
                String[] split = library.name.split(":");
                if (split.length >= 3) {
                    return compareVersion(split[2], "3.4.0") >= 0 ? "3.4.1" : "3.3.3";
                }
            }
        }
        return family.yearVersion >= 26 ? "3.4.1" : "3.3.3";
    }

    private static int compareVersion(String left, String right) {
        String[] a = left.split("[.-]");
        String[] b = right.split("[.-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? parseLeadingInt(a[i]) : 0;
            int bv = i < b.length ? parseLeadingInt(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int parseLeadingInt(String value) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isDigit(c)) break;
            result = result * 10 + (c - '0');
        }
        return result;
    }

    private static String firstNonEmpty(String first, String second, String fallback) {
        if (Tools.isValidString(first)) return first;
        if (Tools.isValidString(second)) return second;
        return fallback;
    }

    public static final class Report {
        public final String versionId;
        public final VersionFamily family;
        public final int javaMajor;
        public final String lwjglChannel;
        public final String rendererId;
        public final String runtimeName;
        public final List<String> issues;

        private Report(String versionId, VersionFamily family, int javaMajor,
                       String lwjglChannel, String rendererId, String runtimeName,
                       List<String> issues) {
            this.versionId = versionId;
            this.family = family;
            this.javaMajor = javaMajor;
            this.lwjglChannel = lwjglChannel;
            this.rendererId = rendererId;
            this.runtimeName = runtimeName;
            this.issues = issues;
        }

        public boolean isReady() {
            return runtimeName != null;
        }

        public String diagnosticLine() {
            return "Compatibility: Minecraft=" + versionId + ", Java=" + javaMajor
                    + ", LWJGL=" + lwjglChannel + ", renderer=" + rendererId
                    + ", arch=" + Architecture.archAsString(Architecture.getDeviceArchitecture());
        }
    }

    public static final class VersionFamily {
        public final int major;
        public final int minor;
        public final int patch;
        public final int yearVersion;
        public final boolean snapshot;
        public final boolean modern;
        public final boolean requiresDesktopGl;

        private VersionFamily(int major, int minor, int patch, int yearVersion,
                              boolean snapshot, boolean modern, boolean requiresDesktopGl) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.yearVersion = yearVersion;
            this.snapshot = snapshot;
            this.modern = modern;
            this.requiresDesktopGl = requiresDesktopGl;
        }

        static VersionFamily parse(String versionId, String inheritsFrom) {
            String combined = (versionId + " " + (inheritsFrom == null ? "" : inheritsFrom))
                    .toLowerCase(Locale.ROOT);
            Matcher matcher = RELEASE.matcher(combined);
            int major = 0;
            int minor = 0;
            int patch = 0;
            int year = 0;
            if (matcher.find()) {
                major = parseLeadingInt(matcher.group(1));
                minor = parseLeadingInt(matcher.group(2));
                patch = matcher.group(3) == null ? 0 : parseLeadingInt(matcher.group(3));
                if (major >= 20) year = major;
            }
            Matcher week = Pattern.compile("(?:^|[^0-9])(\\d{2})w\\d+").matcher(combined);
            if (week.find()) year = parseLeadingInt(week.group(1));
            boolean snapshot = combined.contains("snapshot") || combined.matches(".*\\d{2}w\\d+.*")
                    || combined.contains("pre") || combined.contains("rc");
            boolean modern = year >= 20 || major == 1 && minor >= 17;
            boolean desktop = year >= 26 || major == 1 && minor >= 21 && snapshot;
            return new VersionFamily(major, minor, patch, year, snapshot, modern, desktop);
        }
    }
}
