package net.kdt.pojavlaunch.analytics;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.google.firebase.FirebaseApp;

import java.util.List;

/** Keeps Firebase APIs inside the launcher process where their providers live. */
public final class FirebaseProcessGuard {
    private FirebaseProcessGuard() {
    }

    public static boolean ensureInitialized(Context context) {
        if (context == null || !isLauncherProcess(context)) return false;
        try {
            FirebaseApp.getInstance();
            return true;
        } catch (IllegalStateException missingDefaultApp) {
            return FirebaseApp.initializeApp(context.getApplicationContext()) != null;
        }
    }

    public static boolean isLauncherProcess(Context context) {
        return context != null && isLauncherProcessName(
                context.getPackageName(), currentProcessName(context));
    }

    static boolean isLauncherProcessName(String packageName, String processName) {
        if (packageName == null || processName == null) return false;
        return processName.equals(packageName) || processName.equals(packageName + ":launcher");
    }

    private static String currentProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return null;
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) return null;
        int pid = android.os.Process.myPid();
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.pid == pid) return process.processName;
        }
        return null;
    }
}
