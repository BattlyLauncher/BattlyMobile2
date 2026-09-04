package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure launch-time policy for shader-heavy workloads. It never persists user settings. */
public final class AdaptiveGraphicsPolicy {
    public static final String PROFILE_AUTO = "auto";
    public static final String PROFILE_PERFORMANCE = "performance";
    public static final String PROFILE_BALANCED = "balanced";
    public static final String PROFILE_QUALITY = "quality";
    public static final String PROFILE_CUSTOM = "custom";

    private static final String MOBILE_GLUES = "opengles_mobileglues";
    private static final String FREEDRENO = "opengles3_desktopgl_freedreno";
    private static final String KOPPER = "opengles3_desktopgl_zink_kopper";
    private static final String ZINK = "vulkan_zink";
    private static final Pattern ADRENO_MODEL = Pattern.compile("adreno(?:\\s*\\(tm\\))?\\s*(\\d{3,4})",
            Pattern.CASE_INSENSITIVE);

    private AdaptiveGraphicsPolicy() {
    }

    public static final class Input {
        public final String profile;
        public final String gpu;
        public final boolean shaderEnabled;
        public final boolean modernMinecraft;
        public final boolean requiresDesktopGl;
        public final long memoryMb;
        public final int userResolutionPercent;
        public final boolean userSustainedPerformance;
        public final String baselineRenderer;
        public final List<String> compatibleRenderers;
        public final List<String> blockedRenderers;

        public Input(String profile, String gpu, boolean shaderEnabled,
                     boolean modernMinecraft, boolean requiresDesktopGl,
                     long memoryMb, int userResolutionPercent,
                     boolean userSustainedPerformance, String baselineRenderer,
                     List<String> compatibleRenderers, List<String> blockedRenderers) {
            this.profile = normalizeProfile(profile);
            this.gpu = gpu == null ? "unknown" : gpu;
            this.shaderEnabled = shaderEnabled;
            this.modernMinecraft = modernMinecraft;
            this.requiresDesktopGl = requiresDesktopGl;
            this.memoryMb = memoryMb;
            this.userResolutionPercent = clamp(userResolutionPercent, 25, 100);
            this.userSustainedPerformance = userSustainedPerformance;
            this.baselineRenderer = baselineRenderer;
            this.compatibleRenderers = compatibleRenderers == null
                    ? Collections.emptyList() : compatibleRenderers;
            this.blockedRenderers = blockedRenderers == null
                    ? Collections.emptyList() : blockedRenderers;
        }

        @NonNull
        public Decision decide() {
            if (PROFILE_CUSTOM.equals(profile)) {
                return new Decision(baselineRenderer, userResolutionPercent,
                        userSustainedPerformance, false, "custom settings");
            }

            String renderer = baselineRenderer;
            int resolution = userResolutionPercent;
            boolean sustained = userSustainedPerformance;
            boolean adapted = false;
            String reason = shaderEnabled ? "shader workload" : "standard workload";

            if (shaderEnabled) {
                String preferred = selectShaderRenderer();
                if (preferred != null) renderer = preferred;
                int cap = resolutionCap();
                resolution = Math.min(userResolutionPercent, cap);
                sustained = true;
                adapted = !equalsNullable(renderer, baselineRenderer)
                        || resolution != userResolutionPercent
                        || !userSustainedPerformance;
            }
            return new Decision(renderer, resolution, sustained, adapted, reason);
        }

        private String selectShaderRenderer() {
            if (!modernMinecraft && !requiresDesktopGl) return baselineRenderer;
            List<String> order = new ArrayList<>();
            int adreno = adrenoModel(gpu);
            if (requiresDesktopGl) {
                add(order, MOBILE_GLUES);
                if (adreno >= 500 && adreno < 700) add(order, FREEDRENO);
            } else if (adreno >= 500 && adreno < 700) {
                add(order, FREEDRENO);
                add(order, MOBILE_GLUES);
            } else {
                // A7xx/A8xx Freedreno can fail before first frame on some vendor kernels.
                add(order, MOBILE_GLUES);
                if (adreno >= 500) add(order, FREEDRENO);
            }
            add(order, KOPPER);
            add(order, ZINK);
            add(order, baselineRenderer);
            for (String renderer : compatibleRenderers) add(order, renderer);
            for (String renderer : order) {
                if (renderer != null && compatibleRenderers.contains(renderer)
                        && !blockedRenderers.contains(renderer)) return renderer;
            }
            return baselineRenderer;
        }

        private int resolutionCap() {
            if (PROFILE_QUALITY.equals(profile)) return userResolutionPercent;
            if (PROFILE_PERFORMANCE.equals(profile)) return 60;
            if (PROFILE_BALANCED.equals(profile)) return 72;
            if (memoryMb > 0 && memoryMb < 5000) return 65;
            if (memoryMb > 0 && memoryMb < 7500) return 70;
            return 75;
        }
    }

    public static final class Decision {
        public final String rendererId;
        public final int resolutionPercent;
        public final boolean enableSustainedPerformance;
        public final boolean adapted;
        public final String reason;

        Decision(String rendererId, int resolutionPercent,
                 boolean enableSustainedPerformance, boolean adapted, String reason) {
            this.rendererId = rendererId;
            this.resolutionPercent = resolutionPercent;
            this.enableSustainedPerformance = enableSustainedPerformance;
            this.adapted = adapted;
            this.reason = reason;
        }
    }

    public static int adrenoModel(String gpu) {
        if (gpu == null) return 0;
        Matcher matcher = ADRENO_MODEL.matcher(gpu);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @NonNull
    public static String cacheSegment(String value) {
        String normalized = value == null ? "unknown" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) return "unknown";
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private static String normalizeProfile(String profile) {
        if (PROFILE_PERFORMANCE.equals(profile) || PROFILE_BALANCED.equals(profile)
                || PROFILE_QUALITY.equals(profile) || PROFILE_CUSTOM.equals(profile)) {
            return profile;
        }
        return PROFILE_AUTO;
    }

    private static void add(List<String> values, String value) {
        if (value != null && !values.contains(value)) values.add(value);
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
