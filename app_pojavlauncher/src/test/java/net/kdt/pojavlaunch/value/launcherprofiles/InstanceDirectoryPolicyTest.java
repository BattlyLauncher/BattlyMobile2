package net.kdt.pojavlaunch.value.launcherprofiles;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InstanceDirectoryPolicyTest {
    @Test
    public void createsStableSanitizedCustomInstancePath() {
        assertEquals("./custom_instances/my-pack-123e4567",
                InstanceDirectoryPolicy.isolatedGameDir("My Pack", "123e4567-e89b-12d3"));
    }

    @Test
    public void doesNotReplaceExplicitDirectory() {
        MinecraftProfile profile = new MinecraftProfile();
        profile.name = "Existing";
        profile.gameDir = "./custom_instances/already-there";

        InstanceDirectoryPolicy.applyToNewProfile(profile, "12345678");

        assertEquals("./custom_instances/already-there", profile.gameDir);
    }

    @Test
    public void assignsIndependentDirectoriesToProfilesWithTheSameName() {
        MinecraftProfile first = new MinecraftProfile();
        first.name = "Forge";
        MinecraftProfile second = new MinecraftProfile();
        second.name = "Forge";

        InstanceDirectoryPolicy.applyToNewProfile(first, "11111111-a");
        InstanceDirectoryPolicy.applyToNewProfile(second, "22222222-b");

        assertEquals("./custom_instances/forge-11111111", first.gameDir);
        assertEquals("./custom_instances/forge-22222222", second.gameDir);
    }
}
