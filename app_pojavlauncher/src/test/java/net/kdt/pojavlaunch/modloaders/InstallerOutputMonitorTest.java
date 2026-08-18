package net.kdt.pojavlaunch.modloaders;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InstallerOutputMonitorTest {
    @Test
    public void recognizesForgeCompletionMarker() {
        assertEquals(InstallerOutputMonitor.State.SUCCESS,
                InstallerOutputMonitor.classify(
                        "Injecting profile\nSuccessfully installed client into launcher."));
    }

    @Test
    public void recognizesUnsupportedRuntimeImmediately() {
        assertEquals(InstallerOutputMonitor.State.UNSUPPORTED_RUNTIME,
                InstallerOutputMonitor.classify(
                        "UnsupportedClassVersionError: class file version 65.0"));
        assertEquals(21, InstallerOutputMonitor.requiredJavaVersion(
                "only recognizes class file versions up to 61.0; class file version 65.0"));
    }

    @Test
    public void ignoresOrdinaryInstallerOutput() {
        assertEquals(InstallerOutputMonitor.State.RUNNING,
                InstallerOutputMonitor.classify("Patching some/class 1/1"));
    }
}
