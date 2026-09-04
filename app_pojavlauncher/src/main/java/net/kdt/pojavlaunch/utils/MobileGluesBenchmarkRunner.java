package net.kdt.pojavlaunch.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs one real MobileGlues benchmark while keeping driver failures out of the launcher process. */
public final class MobileGluesBenchmarkRunner implements AutoCloseable {
    private static final int START_SECTIONS = 256;
    private static final int MIN_SECTIONS = 32;
    private static final int MAX_CONTEXT_BACKOFFS = 3;
    private static final long PROGRESS_POLL_MS = 250L;

    public interface Listener {
        void onProgress(@NonNull Progress progress);
        void onRetry(int sections);
        void onComplete(@NonNull MobileGluesBenchmarkResult result);
        void onFailure(@NonNull String message);
    }

    public static final class Progress {
        public final int attempt;
        public final float fraction;

        Progress(int attempt, float fraction) {
            this.attempt = attempt;
            this.fraction = fraction;
        }
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService benchmarkExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService progressExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final String mgDirectory;
    private final String angleDirectory;

    private ServiceConnection connection;
    private IMobileGluesBenchmarkService service;
    private volatile boolean polling;
    private int generation;
    private int contextBackoffs;

    public MobileGluesBenchmarkRunner(@NonNull Context context, @NonNull Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.mgDirectory = new File(Tools.DIR_DATA, "MobileGlues").getAbsolutePath();
        this.angleDirectory = this.context.getApplicationInfo().nativeLibraryDir;
    }

    public void start() {
        bindAndRun(START_SECTIONS, 0);
    }

    private void bindAndRun(int startSections, int maxSections) {
        if (finished.get()) return;
        int runGeneration = ++generation;
        Intent intent = new Intent(context, MobileGluesBenchmarkService.class);
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                if (finished.get() || runGeneration != generation) return;
                service = IMobileGluesBenchmarkService.Stub.asInterface(binder);
                startProgressPolling(runGeneration);
                benchmarkExecutor.execute(() -> runBoundBenchmark(
                        runGeneration, startSections, maxSections));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (!finished.get() && runGeneration == generation) {
                    fail(context.getString(R.string.mg_benchmark_process_ended));
                }
            }

            @Override
            public void onBindingDied(ComponentName name) {
                if (!finished.get() && runGeneration == generation) {
                    fail(context.getString(R.string.mg_benchmark_driver_stopped));
                }
            }

            @Override
            public void onNullBinding(ComponentName name) {
                fail(context.getString(R.string.mg_benchmark_service_failed));
            }
        };
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            fail(context.getString(R.string.mg_benchmark_bind_failed));
        }
    }

    private void runBoundBenchmark(int runGeneration, int startSections, int maxSections) {
        try {
            IMobileGluesBenchmarkService remote = service;
            if (remote == null) throw new RemoteException("benchmark service unavailable");
            String raw = remote.runBenchmark(
                    mgDirectory, angleDirectory, startSections, maxSections);
            if (runGeneration != generation || finished.get()) return;
            MobileGluesBenchmarkResult result = MobileGluesBenchmarkResult.parse(raw);
            if ("context-lost".equals(result.getError())
                    && contextBackoffs < MAX_CONTEXT_BACKOFFS) {
                int crashedAt = result.getSections() > 0 ? result.getSections() : startSections;
                int smaller = crashedAt / 2;
                if (smaller >= MIN_SECTIONS) {
                    contextBackoffs++;
                    mainHandler.post(() -> retryWithSmallerScene(runGeneration, smaller));
                    return;
                }
            }
            complete(result);
        } catch (Throwable throwable) {
            if (runGeneration == generation && !finished.get()) {
                fail(throwable.getMessage() == null
                        ? throwable.getClass().getSimpleName() : throwable.getMessage());
            }
        } finally {
            polling = false;
        }
    }

    private void retryWithSmallerScene(int runGeneration, int sections) {
        if (finished.get() || runGeneration != generation) return;
        listener.onRetry(sections);
        unbindCurrent();
        mainHandler.postDelayed(() -> bindAndRun(sections, sections), 350L);
    }

    private void startProgressPolling(int runGeneration) {
        polling = true;
        progressExecutor.execute(() -> {
            while (polling && !finished.get() && runGeneration == generation) {
                try {
                    IMobileGluesBenchmarkService remote = service;
                    Progress progress = remote == null ? null : decodeProgress(remote.getProgress());
                    if (progress != null) mainHandler.post(() -> listener.onProgress(progress));
                    Thread.sleep(PROGRESS_POLL_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable ignored) {
                    return;
                }
            }
        });
    }

    @Nullable
    static Progress decodeProgress(int raw) {
        if (raw < 0) return null;
        if (raw >= 4000) return new Progress(4, 1f);
        return new Progress(Math.min(4, raw / 1000 + 1), (raw % 1000) / 1000f);
    }

    private void complete(@NonNull MobileGluesBenchmarkResult result) {
        if (!finished.compareAndSet(false, true)) return;
        polling = false;
        mainHandler.post(() -> {
            unbindCurrent();
            listener.onComplete(result);
            shutdownExecutors();
        });
    }

    private void fail(@NonNull String message) {
        if (!finished.compareAndSet(false, true)) return;
        polling = false;
        mainHandler.post(() -> {
            unbindCurrent();
            listener.onFailure(message);
            shutdownExecutors();
        });
    }

    private void unbindCurrent() {
        ServiceConnection current = connection;
        connection = null;
        service = null;
        if (current != null) {
            try {
                context.unbindService(current);
            } catch (IllegalArgumentException ignored) {
                // Already disconnected after a native process failure.
            }
        }
    }

    private void shutdownExecutors() {
        benchmarkExecutor.shutdownNow();
        progressExecutor.shutdownNow();
    }

    @Override
    public void close() {
        if (!finished.compareAndSet(false, true)) return;
        polling = false;
        unbindCurrent();
        shutdownExecutors();
    }
}
