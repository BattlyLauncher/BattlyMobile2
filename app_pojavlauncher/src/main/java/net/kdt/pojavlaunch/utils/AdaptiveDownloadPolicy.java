package net.kdt.pojavlaunch.utils;

public final class AdaptiveDownloadPolicy {
    public static final int DEFAULT_WORKERS = 64;
    public static final int MIN_WORKERS = 16;
    public static final int MAX_WORKERS = 100;

    private AdaptiveDownloadPolicy() {
    }

    public static int resolveWorkers(int configuredWorkers) {
        return clamp(configuredWorkers);
    }

    public static int resolveWorkers(int configuredWorkers, int workloadSize) {
        int workers = resolveWorkers(configuredWorkers);
        return workloadSize > 0 ? Math.min(workers, workloadSize) : workers;
    }

    public static int clamp(int workers) {
        return Math.max(MIN_WORKERS, Math.min(MAX_WORKERS, workers));
    }
}
