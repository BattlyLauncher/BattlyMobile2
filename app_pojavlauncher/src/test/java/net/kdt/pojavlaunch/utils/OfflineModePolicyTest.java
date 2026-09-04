package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OfflineModePolicyTest {
    @Test
    public void forcedOfflineAlwaysBlocksNetwork() {
        assertFalse(OfflineModePolicy.canUseNetwork(true, true, true, true));
    }

    @Test
    public void disconnectedNetworkIsOffline() {
        assertFalse(OfflineModePolicy.canUseNetwork(false, false, true, false));
    }

    @Test
    public void modernNetworkMustBeValidated() {
        assertFalse(OfflineModePolicy.canUseNetwork(false, true, true, false));
        assertTrue(OfflineModePolicy.canUseNetwork(false, true, true, true));
    }

    @Test
    public void legacyConnectedNetworkRemainsSupported() {
        assertTrue(OfflineModePolicy.canUseNetwork(false, true, false, false));
    }
}
