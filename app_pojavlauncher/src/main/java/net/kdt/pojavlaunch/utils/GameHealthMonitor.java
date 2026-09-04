package net.kdt.pojavlaunch.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

import net.kdt.pojavlaunch.Logger;

import java.util.Locale;

/** Records low-frequency process health data for long Minecraft sessions. */
public final class GameHealthMonitor {
    private static final String TAG = "BattlyGameHealth";
    private static final long SAMPLE_INTERVAL_MS = 60_000L;

    private final Context context;
    private final String renderer;
    private final String version;
    private HandlerThread thread;
    private Handler handler;
    private boolean started;

    public GameHealthMonitor(Context context, String renderer, String version) {
        this.context = context.getApplicationContext();
        this.renderer = safe(renderer);
        this.version = safe(version);
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        thread = new HandlerThread("BattlyGameHealth");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(sample);
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        handler = null;
        thread = null;
    }

    public void recordTrimMemory(int level) {
        write("trim=" + level + ", " + snapshot());
    }

    private final Runnable sample = new Runnable() {
        @Override
        public void run() {
            synchronized (GameHealthMonitor.this) {
                if (!started || handler == null) return;
            }
            write(snapshot());
            synchronized (GameHealthMonitor.this) {
                if (started && handler != null) handler.postDelayed(this, SAMPLE_INTERVAL_MS);
            }
        }
    };

    private String snapshot() {
        Runtime runtime = Runtime.getRuntime();
        long javaUsedMb = bytesToMb(runtime.totalMemory() - runtime.freeMemory());
        long javaCommittedMb = bytesToMb(runtime.totalMemory());
        long javaMaxMb = bytesToMb(runtime.maxMemory());
        long nativeMb = bytesToMb(Debug.getNativeHeapAllocatedSize());

        int pssMb = -1;
        long availableMb = -1;
        boolean lowMemory = false;
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            Debug.MemoryInfo[] processInfo = manager.getProcessMemoryInfo(new int[]{Process.myPid()});
            if (processInfo.length > 0 && processInfo[0] != null) {
                pssMb = processInfo[0].getTotalPss() / 1024;
            }
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memoryInfo);
            availableMb = bytesToMb(memoryInfo.availMem);
            lowMemory = memoryInfo.lowMemory;
        }

        int thermal = -1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null) thermal = power.getCurrentThermalStatus();
        }
        return String.format(Locale.ROOT,
                "version=%s, renderer=%s, pss=%dMB, java=%d/%d/%dMB, native=%dMB, available=%dMB, lowMemory=%s, thermal=%d",
                version, renderer, pssMb, javaUsedMb, javaCommittedMb, javaMaxMb,
                nativeMb, availableMb, lowMemory, thermal);
    }

    private static void write(String message) {
        String line = "Game health: " + message;
        Log.i(TAG, line);
        Logger.appendToLog(line);
    }

    private static long bytesToMb(long bytes) {
        return bytes < 0 ? -1 : bytes / 1024L / 1024L;
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
    }
}
