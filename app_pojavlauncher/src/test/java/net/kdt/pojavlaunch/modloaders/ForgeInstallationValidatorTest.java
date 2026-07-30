package net.kdt.pojavlaunch.modloaders;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;

public class ForgeInstallationValidatorTest {
    private static final File UNUSED_MINECRAFT_DIRECTORY = new File("unused");

    @Test
    public void ignoresNeoForgeProfilesUsingForgeClientLaunchTarget() {
        JsonObject profile = JsonParser.parseString("{"
                + "\"libraries\":[{\"name\":\"net.neoforged:neoforge:21.1.0\"}],"
                + "\"arguments\":{\"game\":[\"--launchTarget\",\"forgeclient\"]}"
                + "}").getAsJsonObject();

        ForgeInstallationValidator.Status status =
                ForgeInstallationValidator.inspectJson(UNUSED_MINECRAFT_DIRECTORY, profile);

        assertFalse(status.forge);
        assertTrue(status.complete);
    }

    @Test
    public void validatesOnlyActualMinecraftForgeProfiles() {
        JsonObject profile = JsonParser.parseString("{"
                + "\"libraries\":[{\"name\":\"net.minecraftforge:forge:1.20.1-47.3.0\"}],"
                + "\"arguments\":{\"game\":["
                + "\"--launchTarget\",\"forgeclient\","
                + "\"--fml.mcVersion\",\"1.20.1\","
                + "\"--fml.forgeVersion\",\"47.3.0\","
                + "\"--fml.mcpVersion\",\"20230612.114412\""
                + "]}}").getAsJsonObject();

        ForgeInstallationValidator.Status status =
                ForgeInstallationValidator.inspectJson(UNUSED_MINECRAFT_DIRECTORY, profile);

        assertTrue(status.forge);
        assertFalse(status.complete);
    }
}
