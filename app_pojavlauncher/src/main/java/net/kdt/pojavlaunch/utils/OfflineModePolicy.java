package net.kdt.pojavlaunch.utils;

/** Pure decision policy kept separate so offline behavior can be unit tested. */
public final class OfflineModePolicy {
    private OfflineModePolicy() {
    }

    static boolean canUseNetwork(boolean forcedOffline, boolean connected,
                                 boolean validationKnown, boolean validated) {
        if (forcedOffline || !connected) return false;
        return !validationKnown || validated;
    }
}
