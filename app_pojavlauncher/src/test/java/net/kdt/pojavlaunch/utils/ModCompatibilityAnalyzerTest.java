package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModCompatibilityAnalyzerTest {
    @Test
    public void identifiesMixinModsAndHidesSecondaryAwtFailure() {
        String log = "Loading Minecraft 26.2 with Fabric Loader 0.19.3\n"
                + "\t- sodium 0.9.1+mc26.1.2\n"
                + "Mixin apply for mod sodium failed: InvalidInjectionException\n"
                + "java.lang.UnsatisfiedLinkError: 'void java.awt.Insets.initIDs()'";

        ModCompatibilityAnalyzer.Analysis result = ModCompatibilityAnalyzer.analyze(log);

        assertTrue(result.detected);
        assertEquals("sodium", result.issues.get(0).modId);
        assertEquals("0.9.1+mc26.1.2", result.issues.get(0).installedVersion);
        assertFalse(result.primaryExcerpt.contains("Insets.initIDs"));
    }

    @Test
    public void explainsOutdatedQuiltLoaderForJava25Bytecode() {
        String log = "Loading Minecraft 26.1.2 with Quilt Loader 0.24.0\n"
                + "IllegalArgumentException: Unsupported class file major version 69";

        ModCompatibilityAnalyzer.Analysis result = ModCompatibilityAnalyzer.analyze(log);

        assertTrue(result.detected);
        assertEquals("Quilt", result.loader);
        assertTrue(result.summary.contains("Quilt 0.24.0"));
        assertTrue(result.summary.contains("Java 25"));
        assertTrue(result.solution.contains("newer Quilt"));
    }
}
