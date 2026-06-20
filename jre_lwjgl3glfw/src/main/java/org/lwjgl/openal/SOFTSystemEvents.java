/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.openal;

/**
 * Stub for the ALC_SOFT_system_events OpenAL extension (LWJGL 3.3.3+).
 * On Android this extension is not supported; all methods return no-op values
 * so Minecraft gracefully falls back to polling mode.
 */
public class SOFTSystemEvents {

    /** Not constructible. */
    private SOFTSystemEvents() {}

    /**
     * Enables or disables specific system event types.
     * Stub: returns {@code false} (not supported on Android).
     */
    public static boolean alcEventControlSOFT(int[] eventTypes, boolean enable) {
        return false;
    }

    /**
     * Registers a system event callback.
     * Stub: no-op.
     */
    public static void alcEventCallbackSOFT(SOFTSystemEventProcI callback, long userParam) {
        // not supported on Android
    }

    /**
     * Queries whether a specific event type/device-type combination is supported.
     * Stub: returns {@code 0} (not supported on Android).
     */
    public static int alcEventIsSupportedSOFT(int eventType, int deviceType) {
        return 0;
    }
}
