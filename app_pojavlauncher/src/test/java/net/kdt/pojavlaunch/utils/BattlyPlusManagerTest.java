package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.junit.Test;

public class BattlyPlusManagerTest {
    @Test
    public void offlineAccountIsNeverEligibleForBattlyPlus() {
        MinecraftAccount account = new MinecraftAccount();
        account.username = "OfflinePlayer";
        account.accessToken = "0";

        assertFalse(BattlyPlusManager.isBattlyAccountEligible(account));
    }

    @Test
    public void battlyAccountCanUseStoredEntitlement() {
        MinecraftAccount account = new MinecraftAccount();
        account.username = "BattlyPlayer";
        account.accessToken = "12345678901234567890123456789012";
        account.isMicrosoft = false;

        assertTrue(BattlyPlusManager.isBattlyAccountEligible(account));
    }

    @Test
    public void microsoftAccountIsNotBattlyPlusEligible() {
        MinecraftAccount account = new MinecraftAccount();
        account.username = "JavaOwner";
        account.accessToken = "12345678901234567890123456789012";
        account.isMicrosoft = true;

        assertFalse(BattlyPlusManager.isBattlyAccountEligible(account));
    }

    @Test
    public void premiumEntitlementBelongsOnlyToMatchingAccount() {
        MinecraftAccount account = new MinecraftAccount();
        account.username = "PremiumUser";
        account.accessToken = "12345678901234567890123456789012";

        assertTrue(BattlyPlusManager.sameAccount(account, "premiumuser"));
        assertFalse(BattlyPlusManager.sameAccount(account, "AnotherUser"));
    }
}
