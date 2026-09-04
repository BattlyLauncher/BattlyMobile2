package net.kdt.pojavlaunch.analytics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FirebaseProcessGuardTest {
    @Test
    public void acceptsDefaultAndLauncherProcesses() {
        assertTrue(FirebaseProcessGuard.isLauncherProcessName("com.battly", "com.battly"));
        assertTrue(FirebaseProcessGuard.isLauncherProcessName("com.battly", "com.battly:launcher"));
    }

    @Test
    public void rejectsGameAndInstallerProcesses() {
        assertFalse(FirebaseProcessGuard.isLauncherProcessName("com.battly", "com.battly:game"));
        assertFalse(FirebaseProcessGuard.isLauncherProcessName("com.battly", "com.battly:installer"));
        assertFalse(FirebaseProcessGuard.isLauncherProcessName("com.battly", null));
    }
}
