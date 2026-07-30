package net.kdt.pojavlaunch.value;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MinecraftAccountTest {

    @Test
    public void legacyMicrosoftDemo_requiresMicrosoftAndPlaceholderProfile() {
        MinecraftAccount account = new MinecraftAccount();
        account.username = "Demo.Player";
        account.isMicrosoft = true;

        assertTrue(account.isLegacyMicrosoftDemo());

        account.isMicrosoft = false;
        assertFalse(account.isLegacyMicrosoftDemo());

        account.isMicrosoft = true;
        account.profileId = "12345678-1234-1234-1234-123456789abc";
        assertFalse(account.isLegacyMicrosoftDemo());
    }
}
