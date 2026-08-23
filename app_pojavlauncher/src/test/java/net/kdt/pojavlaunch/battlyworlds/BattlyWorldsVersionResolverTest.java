package net.kdt.pojavlaunch.battlyworlds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattlyWorldsVersionResolverTest {
    @Test
    public void findsExactProfileWithoutChangingItsVersion() {
        Map<String, MinecraftProfile> profiles = new LinkedHashMap<>();
        MinecraftProfile current = profile("1.7.10");
        MinecraftProfile hosted = profile("1.20.1-forge-47.4.23");
        profiles.put("current", current);
        profiles.put("hosted", hosted);

        assertEquals("hosted", BattlyWorldsVersionResolver.findProfileKey(
                profiles, "1.20.1-forge-47.4.23"));
        assertEquals("1.7.10", current.lastVersionId);
        assertEquals("1.20.1-forge-47.4.23", hosted.lastVersionId);
    }

    @Test
    public void acceptsEquivalentForgeNamingWithoutOverwritingCurrentProfile() {
        Map<String, MinecraftProfile> profiles = new LinkedHashMap<>();
        profiles.put("forge", profile("forge-1.20.1-47.4.23"));

        assertEquals("forge", BattlyWorldsVersionResolver.findProfileKey(
                profiles, "1.20.1-forge-47.4.23"));
        assertEquals("forge", BattlyWorldsVersionResolver.findProfileKey(
                profiles, "1.20.1-forge-47.4.24"));
        assertNull(BattlyWorldsVersionResolver.findProfileKey(profiles, "1.20.2-forge-48.0.1"));
    }

    @Test
    public void resolvesEquivalentInstalledVersionJson() throws Exception {
        File versions = Files.createTempDirectory("battly-worlds-versions").toFile();
        File installed = new File(versions, "forge-1.20.1-47.4.23");
        installed.mkdirs();
        new File(installed, "forge-1.20.1-47.4.23.json").createNewFile();

        assertEquals("forge-1.20.1-47.4.23",
                BattlyWorldsVersionResolver.findInstalledVersionId(
                        versions, "1.20.1-forge-47.4.23"));
    }

    @Test
    public void keepsCompatibleCurrentForgeInstanceWhenHostBuildDiffers() {
        Map<String, MinecraftProfile> profiles = new LinkedHashMap<>();
        profiles.put("other", profile("1.21.1"));
        profiles.put("current", profile("1.20.1-forge-47.3.12"));

        assertEquals("current", BattlyWorldsVersionResolver.findProfileKey(
                profiles, "current", "1.20.1-forge-47.4.23"));
        assertEquals("1.20.1-forge-47.3.12", profiles.get("current").lastVersionId);
    }

    @Test
    public void fallsBackToSameMinecraftVersionWithoutPersistingHostedCustomId() {
        Map<String, MinecraftProfile> profiles = new LinkedHashMap<>();
        profiles.put("current", profile("1.20.1"));

        assertEquals("current", BattlyWorldsVersionResolver.findProfileKey(
                profiles, "current", "1.20.1-forge-47.4.23"));
        assertEquals("1.20.1", profiles.get("current").lastVersionId);
    }

    @Test
    public void unknownCustomIdFallsBackToDownloadableMinecraftBase() throws Exception {
        File versions = Files.createTempDirectory("battly-worlds-empty-versions").toFile();

        assertEquals("1.20.1", BattlyWorldsVersionResolver.findInstalledVersionId(
                versions, "1.20.1-forge-47.4.23"));
    }

    private static MinecraftProfile profile(String version) {
        MinecraftProfile profile = new MinecraftProfile();
        profile.lastVersionId = version;
        return profile;
    }
}
