package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdaptiveDownloadPolicyTest {
    @Test
    public void clampsManualParallelismToSupportedRange() {
        assertEquals(16, AdaptiveDownloadPolicy.clamp(0));
        assertEquals(48, AdaptiveDownloadPolicy.clamp(48));
        assertEquals(AdaptiveDownloadPolicy.MAX_WORKERS, AdaptiveDownloadPolicy.clamp(500));
    }

    @Test
    public void neverCreatesMoreWorkersThanFiles() {
        assertEquals(3, AdaptiveDownloadPolicy.resolveWorkers(80, 3));
        assertEquals(1, AdaptiveDownloadPolicy.resolveWorkers(80, 1));
        assertEquals(80, AdaptiveDownloadPolicy.resolveWorkers(80, 200));
    }
}
