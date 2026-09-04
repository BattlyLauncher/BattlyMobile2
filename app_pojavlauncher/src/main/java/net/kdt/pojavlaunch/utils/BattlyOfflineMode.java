package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/** Single source of truth for manual and automatic offline operation. */
public final class BattlyOfflineMode {
    public static final String PREF_KEY = "battlyOfflineMode";

    private BattlyOfflineMode() {
    }

    public static boolean isForced(@NonNull Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .getBoolean(PREF_KEY, false);
    }

    public static boolean isOffline(@NonNull Context context) {
        return !canUseNetwork(context);
    }

    public static boolean canUseNetwork(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        boolean forcedOffline = isForced(appContext);
        ConnectivityManager manager = (ConnectivityManager)
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = manager.getActiveNetwork();
            NetworkCapabilities capabilities = activeNetwork == null
                    ? null : manager.getNetworkCapabilities(activeNetwork);
            boolean connected = capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            boolean validated = capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            return OfflineModePolicy.canUseNetwork(
                    forcedOffline, connected, true, validated);
        }

        @SuppressWarnings("deprecation")
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        @SuppressWarnings("deprecation")
        boolean connected = networkInfo != null && networkInfo.isConnected();
        return OfflineModePolicy.canUseNetwork(
                forcedOffline, connected, false, false);
    }
}
