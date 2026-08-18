package net.kdt.pojavlaunch.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BattlyComponentUpdaterTest {
    @Test
    public void selectsOnlyAndroidManagedComponents() throws Exception {
        JSONArray manifest = new JSONArray()
                .put(entry("authlib-injector.jar", "android"))
                .put(entry("components/input/bridge.jar", "android"))
                .put(entry("renderer-plugins/mobileglues/config.json", "android"))
                .put(entry("Registro.log", "windows"))
                .put(entry("../secret.txt", "android"));

        List<JSONObject> selected = BattlyComponentUpdater.selectAndroidEntries(manifest);

        assertEquals(3, selected.size());
        assertEquals("authlib-injector.jar", selected.get(0).getString("path"));
    }

    @Test
    public void rejectsAbsoluteAndTraversalPaths() {
        assertFalse(BattlyComponentUpdater.isAllowedDataComponent("../components/a.jar"));
        assertFalse(BattlyComponentUpdater.isAllowedDataComponent("/components/a.jar"));
        assertFalse(BattlyComponentUpdater.isAllowedDataComponent("components/../../a.jar"));
        assertTrue(BattlyComponentUpdater.isAllowedDataComponent("components/input/a.jar"));
    }

    @Test
    public void throttlesBackgroundChecks() {
        long now = 1_000_000_000L;
        assertTrue(BattlyComponentUpdater.shouldCheck(now, 0L));
        assertFalse(BattlyComponentUpdater.shouldCheck(now, now - 60_000L));
        assertTrue(BattlyComponentUpdater.shouldCheck(now, now - 13L * 60L * 60L * 1000L));
        assertTrue(BattlyComponentUpdater.shouldCheck(now, now + 1L));
    }

    private static JSONObject entry(String path, String platform) throws Exception {
        return new JSONObject()
                .put("path", path)
                .put("url", "https://example.invalid/file")
                .put("compatibilities", new JSONArray().put(platform));
    }
}
