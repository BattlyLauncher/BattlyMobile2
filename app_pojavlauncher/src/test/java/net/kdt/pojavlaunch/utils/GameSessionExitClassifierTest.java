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
    public void fatalStartupWithoutExitIsUnexpected() {
        assertTrue(GameSessionExitClassifier.endedUnexpectedly("Unable to launch\nException in thread \"main\""));
    }

    @Test
    public void nativeLinkFailureWithoutExitIsUnexpected() {
        assertTrue(GameSessionExitClassifier.endedUnexpectedly("java.lang.UnsatisfiedLinkError: liblwjgl.so"));
    }
}
