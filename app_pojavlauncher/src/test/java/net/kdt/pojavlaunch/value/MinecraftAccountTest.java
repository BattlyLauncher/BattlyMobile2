package net.kdt.pojavlaunch.value;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.lang.reflect.Modifier;

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

    @Test
    public void parse_ignoresPersistedVendorBitmapCache() {
        String accountJson = "{"
                + "\"username\":\"VivoUser\","
                + "\"accessToken\":\"token\","
                + "\"mFaceCache\":{\"mNativePtr\":42,\"mVivoBitmap\":{}}"
                + "}";

        MinecraftAccount account = new Gson().fromJson(accountJson, MinecraftAccount.class);

        assertNotNull(account);
        assertTrue("VivoUser".equals(account.username));
    }

    @Test
    public void runtimeBitmapCache_isTransientAndNeverSerialized() throws Exception {
        int modifiers = MinecraftAccount.class.getDeclaredField("mFaceCache").getModifiers();
        String json = new Gson().toJson(new MinecraftAccount());

        assertTrue(Modifier.isTransient(modifiers));
        assertFalse(json.contains("mFaceCache"));
    }
}
