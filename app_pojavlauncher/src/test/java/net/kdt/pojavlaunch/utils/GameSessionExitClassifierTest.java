package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameSessionExitClassifierTest {
    @Test
    public void cleanExitWinsOverNonFatalErrorsInLog() {
        String log = "NoClassDefFoundError: optional controller backend\n"
                + "ReportedNbtException: damaged servers.dat\n"
                + "Stopping!\nJava Exit code: 0\n";
        assertFalse(GameSessionExitClassifier.endedUnexpectedly(log));
    }

    @Test
    public void nonZeroExitIsUnexpected() {
        assertTrue(GameSessionExitClassifier.endedUnexpectedly("Java Exit code: 1"));
    }

    @Test
    public void cleanMinecraftShutdownWinsOverNonZeroNativeExit() {
        String log = "Voice transport disconnected\n"
                + "[Render thread/INFO]: Stopping!\n"
                + "Java Exit code: 1\n";

        assertFalse(GameSessionExitClassifier.endedUnexpectedly(1, null, log));
    }

    @Test
    public void fatalCrashStillWinsWhenShutdownHooksRun() {
        String log = "Game crashed! Crash report saved\n"
                + "[Render thread/INFO]: Stopping!\n"
                + "Java Exit code: 1\n";

        assertTrue(GameSessionExitClassifier.endedUnexpectedly(log));
    }

    @Test
    public void explicitLaunchFailureAlwaysReportsDetails() {
        assertTrue(GameSessionExitClassifier.endedUnexpectedly(
                -1, "Unable to prepare LWJGL", "Stopping!"));
    }

    @Test
    public void nonZeroExitWithoutSessionLogRemainsUnexpected() {
        assertTrue(GameSessionExitClassifier.endedUnexpectedly(137, null, null));
    }

    @Test
    public void fatalStartupWithoutExitIsUnexpected() {
        assertTrue(GameSessionExitClassifier.endedUnexpectedly("Unable to launch\nException in thread \"main\""));
    }

    @Test
    public void nativeLinkFailureWithoutExitIsUnexpected() {
        assertTrue(GameSessionExitClassifier.endedUnexpectedly("java.lang.UnsatisfiedLinkError: liblwjgl.so"));
    }
}
