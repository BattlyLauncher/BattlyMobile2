package net.kdt.pojavlaunch.authenticator.microsoft;

final class MicrosoftSessionTiming {
    private MicrosoftSessionTiming() {
    }

    static long calculateExpiryTime(long now, long expiresInSeconds) {
        long normalizedLifetime = Math.max(1L, expiresInSeconds);
        long refreshMargin = Math.min(300L, Math.max(1L, normalizedLifetime / 10L));
        long safeLifetimeSeconds = Math.max(1L, normalizedLifetime - refreshMargin);
        return now + safeLifetimeSeconds * 1000L;
    }
}
