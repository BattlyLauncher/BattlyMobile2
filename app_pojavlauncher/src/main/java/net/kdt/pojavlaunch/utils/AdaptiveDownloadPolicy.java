package net.kdt.pojavlaunch.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

public final class AdaptiveDownloadPolicy {
    private AdaptiveDownloadPolicy() {
    }

    public static int recommendedWorkers(Context context) {
        int cores = java.lang.Runtime.getRuntime().availableProcessors();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        long ramGiB = memory.totalMem / (1024L * 1024L * 1024L);
        int workers = Math.max(2, Math.min(12, cores));
        if (ramGiB <= 3) workers = Math.min(workers, 4);
        if (isMetered(context)) workers = Math.min(workers, 4);
        return workers;
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
