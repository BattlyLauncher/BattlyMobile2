package net.kdt.pojavlaunch.utils;

import androidx.annotation.Nullable;

/** Pure decisions shared by the Android surface and frame-pacing tests. */
public final class FramePacingPolicy {
    private FramePacingPolicy() {
    }

    public static float selectSurfaceFrameRate(float currentRefreshRate,
                                               @Nullable float[] alternativeRefreshRates) {
        if (isValidRefreshRate(currentRefreshRate)) {
            return currentRefreshRate;
        }

        float bestFallback = 0f;
        if (alternativeRefreshRates != null) {
            for (float refreshRate : alternativeRefreshRates) {
                if (isValidRefreshRate(refreshRate)) {
                    bestFallback = Math.max(bestFallback, refreshRate);
                }
            }
        }
        return bestFallback;
    }

    public static boolean useSurfaceView(boolean alternateSurfaceEnabled) {
        return alternateSurfaceEnabled;
    }

    private static boolean isValidRefreshRate(float refreshRate) {
        return !Float.isNaN(refreshRate)
                && !Float.isInfinite(refreshRate)
                && refreshRate > 0f;
    }
}
