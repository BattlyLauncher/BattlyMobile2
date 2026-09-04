package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed output of MobileGlues 2.0's in-process MultiDraw benchmark. */
public final class MobileGluesBenchmarkResult {
    private static final Map<String, List<String>> IMPLEMENTED_BACKENDS;

    static {
        LinkedHashMap<String, List<String>> entries = new LinkedHashMap<>();
        entries.put("glMultiDrawArrays", Arrays.asList(
                "multiarrays", "multiindirect", "unroll"));
        entries.put("glMultiDrawElements", Arrays.asList(
                "multiarrays", "multiindirect", "multibasevertex", "indirect", "unroll"));
        entries.put("glMultiDrawElementsBaseVertex", Arrays.asList(
                "multibasevertex", "multiindirect", "indirect", "basevertex", "unroll", "compute"));
        entries.put("glMultiDrawArraysIndirect", Arrays.asList(
                "multiindirect", "indirect"));
        entries.put("glMultiDrawElementsIndirect", Arrays.asList(
                "multiindirect", "indirect"));
        IMPLEMENTED_BACKENDS = Collections.unmodifiableMap(entries);
    }

    private final Map<String, List<String>> rankings;
    private final Map<String, Quality> quality;
    private final double elapsedMs;
    private final boolean angleRequested;
    private final boolean angleInUse;
    private final String renderer;
    private final int sections;
    private final String error;

    private MobileGluesBenchmarkResult(Map<String, List<String>> rankings,
                                       Map<String, Quality> quality,
                                       double elapsedMs,
                                       boolean angleRequested,
                                       boolean angleInUse,
                                       String renderer,
                                       int sections,
                                       String error) {
        this.rankings = Collections.unmodifiableMap(rankings);
        this.quality = Collections.unmodifiableMap(quality);
        this.elapsedMs = elapsedMs;
        this.angleRequested = angleRequested;
        this.angleInUse = angleInUse;
        this.renderer = renderer;
        this.sections = sections;
        this.error = error;
    }

    @NonNull
    public static MobileGluesBenchmarkResult parse(@Nullable String raw) {
        try {
            JSONObject root = new JSONObject(raw == null ? "" : raw);
            int sections = root.optInt("sections", 0);
            String nativeError = root.optString("error", null);
            if (nativeError != null && !nativeError.isEmpty()) {
                return failure(nativeError, sections);
            }

            JSONObject entries = root.optJSONObject("entries");
            if (entries == null) return failure("no entries in result", sections);

            LinkedHashMap<String, List<String>> rankings = new LinkedHashMap<>();
            LinkedHashMap<String, Quality> quality = new LinkedHashMap<>();
            JSONObject qualityObject = root.optJSONObject("quality");
            for (Map.Entry<String, List<String>> definition : IMPLEMENTED_BACKENDS.entrySet()) {
                String function = definition.getKey();
                JSONObject measured = entries.optJSONObject(function);
                if (measured == null) continue;

                ArrayList<MeasuredBackend> ordered = new ArrayList<>();
                for (String backend : definition.getValue()) {
                    if (!measured.has(backend)) continue;
                    double micros = measured.optDouble(backend, Double.NaN);
                    if (Double.isFinite(micros) && micros > 0.0) {
                        ordered.add(new MeasuredBackend(backend, micros));
                    }
                }
                ordered.sort(Comparator.comparingDouble(item -> item.micros));
                if (ordered.isEmpty()) continue;

                ArrayList<String> ranking = new ArrayList<>();
                for (MeasuredBackend item : ordered) ranking.add(item.name);
                for (String backend : definition.getValue()) {
                    if (!ranking.contains(backend)) ranking.add(backend);
                }
                rankings.put(function, Collections.unmodifiableList(ranking));

                JSONObject entryQuality = qualityObject == null
                        ? null : qualityObject.optJSONObject(function);
                if (entryQuality != null) {
                    quality.put(function, new Quality(
                            entryQuality.optDouble("noise", 0.0),
                            entryQuality.optInt("rounds", 0),
                            entryQuality.optInt("attempts", 1),
                            entryQuality.optInt("sections", sections),
                            entryQuality.optBoolean("noisy", false)));
                }
            }
            if (rankings.isEmpty()) return failure("empty result", sections);

            return new MobileGluesBenchmarkResult(
                    rankings,
                    quality,
                    root.optDouble("elapsedMs", 0.0),
                    root.optBoolean("angleRequested", false),
                    root.optBoolean("angleInUse", false),
                    root.optString("renderer", null),
                    sections,
                    null);
        } catch (JSONException exception) {
            return failure("unparseable result", 0);
        }
    }

    private static MobileGluesBenchmarkResult failure(String error, int sections) {
        return new MobileGluesBenchmarkResult(
                new LinkedHashMap<>(), new LinkedHashMap<>(), 0.0,
                false, false, null, sections, error);
    }

    public boolean isSuccessful() {
        return error == null && !rankings.isEmpty();
    }

    @NonNull
    public Map<String, List<String>> getRankings() {
        return rankings;
    }

    @NonNull
    public List<String> getRanking(@NonNull String function) {
        List<String> ranking = rankings.get(function);
        return ranking == null ? Collections.emptyList() : ranking;
    }

    @NonNull
    public String getPreferenceValue(@NonNull String function) {
        return String.join(",", getRanking(function));
    }

    @Nullable
    public Quality getQuality(@NonNull String function) {
        return quality.get(function);
    }

    public boolean hasNoisyEntries() {
        for (Quality value : quality.values()) {
            if (value.noisy) return true;
        }
        return false;
    }

    public boolean hasDriverMismatch() {
        return angleRequested && !angleInUse;
    }

    public double getElapsedMs() {
        return elapsedMs;
    }

    @Nullable
    public String getRenderer() {
        return renderer;
    }

    public int getSections() {
        return sections;
    }

    @Nullable
    public String getError() {
        return error;
    }

    public static final class Quality {
        public final double noise;
        public final int rounds;
        public final int attempts;
        public final int sections;
        public final boolean noisy;

        Quality(double noise, int rounds, int attempts, int sections, boolean noisy) {
            this.noise = noise;
            this.rounds = rounds;
            this.attempts = attempts;
            this.sections = sections;
            this.noisy = noisy;
        }
    }

    private static final class MeasuredBackend {
        final String name;
        final double micros;

        MeasuredBackend(String name, double micros) {
            this.name = name;
            this.micros = micros;
        }
    }
}
