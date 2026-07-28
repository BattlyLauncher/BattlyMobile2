package net.kdt.pojavlaunch.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

public final class AdaptiveDownloadPolicy {
    public static final int MIN_WORKERS = 2;
    public static final int MAX_WORKERS = 100;

    private AdaptiveDownloadPolicy() {
    }

    public static int recommendedWorkers(Context context) {
        int cores = java.lang.Runtime.getRuntime().availableProcessors();
        ActivityManager manager = context == null ? null
                : (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        long ramGiB = memory.totalMem / (1024L * 1024L * 1024L);
        int workers = Math.max(8, cores * 8);
        int memoryLimit;
        if (ramGiB <= 3) memoryLimit = 8;
        else if (ramGiB <= 4) memoryLimit = 12;
        else if (ramGiB <= 6) memoryLimit = 24;
        else if (ramGiB <= 8) memoryLimit = 40;
        else if (ramGiB <= 12) memoryLimit = 64;
        else memoryLimit = MAX_WORKERS;

        workers = Math.min(workers, memoryLimit);
        if (context != null && isMetered(context)) workers = Math.min(workers, 12);
        return clamp(workers);
    }

    public static int resolveWorkers(Context context, boolean automatic, int manualWorkers) {
        return automatic ? recommendedWorkers(context) : clamp(manualWorkers);
    }

    public static int resolveWorkers(Context context, boolean automatic, int manualWorkers, int workloadSize) {
        int workers = resolveWorkers(context, automatic, manualWorkers);
        if (workloadSize > 0) workers = Math.min(workers, Math.max(MIN_WORKERS, workloadSize));
        return workers;
    }

    public static int clamp(int workers) {
        return Math.max(MIN_WORKERS, Math.min(MAX_WORKERS, workers));
    }

    public static boolean isMetered(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
            return capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        }
        return manager.isActiveNetworkMetered();
    }
}
