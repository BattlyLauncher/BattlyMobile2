package net.kdt.pojavlaunch.utils;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import androidx.annotation.Nullable;

import java.io.File;

/** One-shot process boundary around the MobileGlues benchmark and graphics driver. */
public final class MobileGluesBenchmarkService extends Service {
    private final IMobileGluesBenchmarkService.Stub binder =
            new IMobileGluesBenchmarkService.Stub() {
                @Override
                public String runBenchmark(String mgDirectory, String angleDirectory,
                                           int startSections, int maxSections) {
                    String nativeDirectory = getApplicationInfo().nativeLibraryDir;
                    String library = new File(nativeDirectory, "libmobileglues.so").getAbsolutePath();
                    return MobileGluesBenchmarkNative.runBenchmark(
                            library, mgDirectory, angleDirectory, startSections, maxSections);
                }

                @Override
                public int getProgress() {
                    return MobileGluesBenchmarkNative.getProgress();
                }
            };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // MobileGlues reads its environment and config during the first dlopen.
        // A fresh process is required for every benchmark to avoid stale settings.
        Process.killProcess(Process.myPid());
        return false;
    }
}
