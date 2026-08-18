package net.kdt.pojavlaunch.authenticator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class BattlyAuthlibManagerTest {
    @Test
    public void selectsAuthlibWhenManifestDeclaresAndroidCompatibility() throws Exception {
        JSONArray files = new JSONArray("["
                + "{\"path\":\"authlib-injector.jar\","
                + "\"url\":\"https://api.battlylauncher.com/battlylauncher/files/files/battly-injector-v2-2.0.0.jar\","
                + "\"size\":508317,"
                + "\"hash\":\"c409dc38d7432fc1b270796880682731ee472ae1\","
                + "\"compatibilities\":[\"android\",\"windows\",\"linux\",\"macos\"]}"
                + "]");

        JSONObject selected = BattlyAuthlibManager.selectAndroidAuthlib(files);

        assertEquals("authlib-injector.jar", selected.getString("path"));
        assertEquals(508317, selected.getLong("size"));
        assertEquals("c409dc38d7432fc1b270796880682731ee472ae1",
                selected.getString("hash"));
    }

    @Test
    public void ignoresAuthlibWithoutAndroidCompatibility() throws Exception {
        JSONArray files = new JSONArray("["
                + "{\"path\":\"authlib-injector.jar\","
                + "\"compatibilities\":[\"windows\",\"linux\",\"macos\"]}"
                + "]");

        assertNull(BattlyAuthlibManager.selectAndroidAuthlib(files));
    }

    @Test
    public void activatesVerifiedDownloadAndRemovesPreviousJar() throws Exception {
        File directory = Files.createTempDirectory("battly-authlib-test").toFile();
        File destination = new File(directory, "authlib-injector.jar");
        File temporary = new File(directory, "authlib-injector.jar.download");
        Files.write(destination.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        Files.write(temporary.toPath(), "new".getBytes(StandardCharsets.UTF_8));

        BattlyAuthlibManager.replaceVerifiedDownload(temporary, destination);

        assertEquals("new", new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8));
        assertFalse(temporary.exists());
        assertFalse(new File(directory, "authlib-injector.jar.backup").exists());
    }
}
