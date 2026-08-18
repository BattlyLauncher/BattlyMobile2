package net.kdt.pojavlaunch.customcontrols;

import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.MinecraftGLSurface;

import org.libsdl.app.SDLActivity;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class PerformanceHudStats {
    private static final int PING_TIMEOUT_MS = 1800;
    private static final AtomicInteger ACTIVE_WIDGETS = new AtomicInteger();
    private static final Object EXECUTOR_LOCK = new Object();

    private static volatile int pingMs = -1;
    private static ScheduledExecutorService pingExecutor;

    private PerformanceHudStats() {
    }

    public static void acquire() {
        if (ACTIVE_WIDGETS.incrementAndGet() == 1) {
            startPingWorker();
        }
    }

    public static void release() {
        if (ACTIVE_WIDGETS.decrementAndGet() <= 0) {
            ACTIVE_WIDGETS.set(0);
            stopPingWorker();
        }
    }

    public static int getFps() {
        if (MinecraftGLSurface.isSdlWindowBridgeEnabled()) {
            try {
                int sdlFps = SDLActivity.nativeGetRendererFps();
                if (sdlFps > 0) return sdlFps;
            } catch (UnsatisfiedLinkError ignored) {
                // Older SDL binaries fall back to the classic renderer counter.
            }
        }
        try {
            return JREUtils.getRendererFps();
        } catch (UnsatisfiedLinkError ignored) {
            return 0;
        }
    }

    public static int getPingMs() {
        return pingMs;
    }

    private static void startPingWorker() {
        synchronized (EXECUTOR_LOCK) {
            if (pingExecutor != null && !pingExecutor.isShutdown()) {
                return;
            }
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "Battly performance ping");
                thread.setDaemon(true);
                return thread;
            };
            pingExecutor = Executors.newSingleThreadScheduledExecutor(factory);
            pingExecutor.scheduleWithFixedDelay(
                    PerformanceHudStats::measurePing,
                    0,
                    3,
                    TimeUnit.SECONDS
            );
        }
    }

    private static void stopPingWorker() {
        synchronized (EXECUTOR_LOCK) {
            if (pingExecutor != null) {
                pingExecutor.shutdownNow();
                pingExecutor = null;
            }
            pingMs = -1;
        }
    }

    private static void measurePing() {
        MinecraftServerSessionTracker.Endpoint endpoint =
                MinecraftServerSessionTracker.getEndpoint();
        if (endpoint == null) {
            pingMs = -1;
            return;
        }
        long startedAt = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host, endpoint.port), PING_TIMEOUT_MS);
            pingMs = Math.max(1, (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        } catch (Exception ignored) {
            pingMs = -1;
        }
    }
}
