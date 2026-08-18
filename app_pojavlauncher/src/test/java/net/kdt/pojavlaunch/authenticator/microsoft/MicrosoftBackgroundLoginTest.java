package net.kdt.pojavlaunch.authenticator.microsoft;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MicrosoftBackgroundLoginTest {
    @Test
    public void expiryUsesServerLifetimeWithRefreshMargin() {
        assertEquals(86_100_000L,
                MicrosoftSessionTiming.calculateExpiryTime(0L, 86_400L));
    }

    @Test
    public void expiryNeverBecomesImmediatelyExpired() {
        assertEquals(1_054_000L,
                MicrosoftSessionTiming.calculateExpiryTime(1_000_000L, 60L));
    }

    @Test
    public void expiryDoesNotOutliveShortServerToken() {
        long expiry = MicrosoftSessionTiming.calculateExpiryTime(10_000L, 60L);
        assertEquals(true, expiry < 70_000L);
    }
}
