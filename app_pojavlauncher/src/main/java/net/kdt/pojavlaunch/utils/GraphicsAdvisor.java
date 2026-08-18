package net.kdt.pojavlaunch.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Performs a short, non-invasive device test and recommends a renderer for the active profile. */
public final class GraphicsAdvisor {
    private GraphicsAdvisor() {
    }

    @NonNull
    public static Result run(@NonNull Context context) {
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
        String versionId = profile.lastVersionId == null ? "unknown" : profile.lastVersionId;
        MinecraftCompatibilityEngine.Report compatibility = MinecraftCompatibilityEngine.evaluate(
                context, versionId, null, null);

        GLInfoUtils.GLInfo gl = GLInfoUtils.getGlInfo();
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) activityManager.getMemoryInfo(memoryInfo);
        long memoryMb = memoryInfo.totalMem / 1024L / 1024L;
        long benchmark = runCpuProbe();
        List<String> compatible = Tools.getCompatibleRenderers(context).rendererIds;
        List<String> alternatives = rankCandidates(
                compatible,
                (gl.vendor + " " + gl.renderer).toLowerCase(Locale.ROOT),
                compatibility.family.modern,
                compatibility.family.requiresDesktopGl,
                memoryMb,
                benchmark);
        String recommended = alternatives.isEmpty() ? compatibility.rendererId : alternatives.get(0);
        return new Result(versionId, recommended, profile.pojavRendererName, gl.vendor, gl.renderer,
                gl.glesMajorVersion, memoryMb, benchmark, alternatives, compatibility.issues);
    }

    /** Lower values represent a faster device. */
    static long runCpuProbe() {
        long start = SystemClock.elapsedRealtimeNanos();
        long value = 0x1234ABCDL;
        for (int i = 0; i < 1_250_000; i++) {
            value = (value * 1664525L + 1013904223L) ^ (value >>> 13);
        }
        long elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        if (value == Long.MIN_VALUE) throw new AssertionError();
        return Math.max(1L, elapsed);
    }

    @NonNull
    static List<String> rankCandidates(@NonNull List<String> compatible,
                                       @NonNull String gpu,
                                       boolean modern,
                                       boolean requiresDesktopGl,
                                       long memoryMb,
                                       long benchmarkMs) {
        ArrayList<String> result = new ArrayList<>();
        boolean adreno = gpu.contains("adreno") || gpu.contains("qualcomm");
        boolean lowEnd = (memoryMb > 0 && memoryMb < 4500) || benchmarkMs > 120;

        if (modern || requiresDesktopGl) {
            addIfCompatible(result, compatible, "opengles_mobileglues");
            if (adreno && !lowEnd) {
                addIfCompatible(result, compatible, "opengles3_desktopgl_freedreno");
            }
            addIfCompatible(result, compatible, "opengles3_desktopgl_zink_kopper");
            addIfCompatible(result, compatible, "vulkan_zink");
        }
        if (!modern || lowEnd) {
            addIfCompatible(result, compatible, "opengles2");
            addIfCompatible(result, compatible, "opengles2_5");
        }
        for (String renderer : compatible) addIfCompatible(result, compatible, renderer);
        return Collections.unmodifiableList(result);
    }

    private static void addIfCompatible(List<String> result, List<String> compatible, String id) {
        if (compatible.contains(id) && !result.contains(id)) result.add(id);
    }

    public static void apply(@NonNull String rendererId) {
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
        profile.pojavRendererName = rendererId;
        LauncherProfiles.write();
    }

    public static final class Result {
        public final String versionId;
        public final String recommendedRenderer;
        public final String currentRenderer;
        public final String glVendor;
        public final String glRenderer;
        public final int glesMajor;
        public final long memoryMb;
        public final long benchmarkMs;
        public final List<String> alternatives;
        public final List<String> issues;

        Result(String versionId, String recommendedRenderer, String currentRenderer,
               String glVendor, String glRenderer, int glesMajor, long memoryMb,
               long benchmarkMs, List<String> alternatives, List<String> issues) {
            this.versionId = versionId;
            this.recommendedRenderer = recommendedRenderer;
            this.currentRenderer = currentRenderer;
            this.glVendor = glVendor;
            this.glRenderer = glRenderer;
            this.glesMajor = glesMajor;
            this.memoryMb = memoryMb;
            this.benchmarkMs = benchmarkMs;
            this.alternatives = alternatives;
            this.issues = issues;
        }

        public String deviceSummary() {
            return Build.MANUFACTURER + " " + Build.MODEL + " | " + glRenderer
                    + " | GLES " + glesMajor + " | " + memoryMb + " MB RAM";
        }
    }
}
