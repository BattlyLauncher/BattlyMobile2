package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BattlyRepairManagerTest {
    @Test
    public void recognizesOnlyIncompleteDownloadSuffixes() {
        assertTrue(BattlyRepairManager.isSafeTemporaryFile(new File("forge.jar.download")));
        assertTrue(BattlyRepairManager.isSafeTemporaryFile(new File("asset.PART")));
        assertTrue(BattlyRepairManager.isSafeTemporaryFile(new File("runtime.tmp")));
        assertFalse(BattlyRepairManager.isSafeTemporaryFile(new File("options.txt")));
        assertFalse(BattlyRepairManager.isSafeTemporaryFile(new File("world.zip")));
        assertFalse(BattlyRepairManager.isSafeTemporaryFile(new File("mod.jar")));
    }
}
