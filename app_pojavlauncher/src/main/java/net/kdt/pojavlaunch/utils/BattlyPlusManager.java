package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BattlyPlusManager {
    public interface Callback {
        void onResult(boolean isPlus);
    }

    public static final String PREFS_NAME = "battly_account";
    public static final String PREF_IS_PREMIUM = "battly_is_premium";
    public static final String PREF_PREMIUM_ACCOUNT = "battly_premium_account";
    public static final String PREF_TOKEN = "battly_token";

    private static final String ENTITLEMENTS_URL = "https://api.battlylauncher.com/api/v2/battlyworlds/entitlements";

    private BattlyPlusManager() {
    }

    public static boolean isPlus(Context context) {
        if (context == null) {
            return false;
        }
        MinecraftAccount currentAccount = PojavProfile.getCurrentProfileContent(
                context.getApplicationContext(), null);
        if (!isBattlyAccountEligible(currentAccount)) {
            return false;
        }
        SharedPreferences preferences = accountPrefs(context);
        String premiumAccount = preferences.getString(PREF_PREMIUM_ACCOUNT, "");
        return preferences.getBoolean(PREF_IS_PREMIUM, false)
                && sameAccount(currentAccount, premiumAccount);
    }

    static boolean isBattlyAccountEligible(MinecraftAccount account) {
        return account != null && account.isBattly();
    }

    static boolean sameAccount(MinecraftAccount account, String username) {
        return account != null
                && account.username != null
                && !account.username.trim().isEmpty()
                && account.username.equalsIgnoreCase(username == null ? "" : username.trim());
    }

    public static String getToken(Context context) {
        List<String> candidates = getTokenCandidates(context);
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    public static List<String> getTokenCandidates(Context context) {
        ArrayList<String> tokens = new ArrayList<>();
        if (context == null) {
            return tokens;
        }
        Set<String> seen = new LinkedHashSet<>();
        Context appContext = context.getApplicationContext();
        MinecraftAccount currentAccount = PojavProfile.getCurrentProfileContent(appContext, null);
        String token = tokenFromAccount(currentAccount);
        if (isUsableBattlyToken(token)) {
            saveResolvedToken(appContext, currentAccount, token);
            addToken(tokens, seen, token);
        }

        String storedUsername = accountPrefs(appContext).getString("battly_username", "");
        if (Tools.isValidString(storedUsername)) {
            MinecraftAccount storedAccount = MinecraftAccount.load(storedUsername);
            token = tokenFromAccount(storedAccount);
            if (isUsableBattlyToken(token)) {
                saveResolvedToken(appContext, storedAccount, token);
                addToken(tokens, seen, token);
            }
        }

        for (MinecraftAccount account : PojavProfile.getAllProfiles()) {
            token = tokenFromAccount(account);
            if (isUsableBattlyToken(token)) {
                saveResolvedToken(appContext, account, token);
                addToken(tokens, seen, token);
            }
        }

        token = accountPrefs(appContext).getString(PREF_TOKEN, "");
        if (isUsableBattlyToken(token)) {
            addToken(tokens, seen, token);
        }
        return tokens;
    }

    public static void setPlus(Context context, boolean plus) {
        if (context == null) {
            return;
        }
        SharedPreferences.Editor editor = accountPrefs(context).edit()
                .putBoolean(PREF_IS_PREMIUM, plus);
        MinecraftAccount currentAccount = PojavProfile.getCurrentProfileContent(
                context.getApplicationContext(), null);
        if (plus && isBattlyAccountEligible(currentAccount)
                && Tools.isValidString(currentAccount.username)) {
            editor.putString(PREF_PREMIUM_ACCOUNT, currentAccount.username);
        } else {
            editor.remove(PREF_PREMIUM_ACCOUNT);
        }
        editor.apply();
    }

    public static void updateFromLoginResponse(Context context, JSONObject response) {
        Boolean premium = findPremiumFlag(response);
        if (premium != null) {
            setPlus(context, premium);
        }
    }

    public static void refreshAsync(Context context, Callback callback) {
        Context appContext = context == null ? null : context.getApplicationContext();
        if (appContext == null) {
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }
        if (BattlyOfflineMode.isOffline(appContext)) {
            if (callback != null) callback.onResult(isPlus(appContext));
            return;
        }
        MinecraftAccount currentAccount = PojavProfile.getCurrentProfileContent(appContext, null);
        if (!isBattlyAccountEligible(currentAccount)) {
            setPlus(appContext, false);
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }
        String activeToken = tokenFromAccount(currentAccount);
        List<String> tokens = new ArrayList<>();
        if (isUsableBattlyToken(activeToken)) {
            saveResolvedToken(appContext, currentAccount, activeToken);
            tokens.add(activeToken);
        }
        if (tokens.isEmpty()) {
            setPlus(appContext, false);
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }
        new Thread(() -> {
            boolean current = isPlus(appContext);
            for (String token : tokens) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(ENTITLEMENTS_URL).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    boolean matchedToken = false;
                    int responseCode = connection.getResponseCode();
                    if (responseCode < 400) {
                        try (InputStream inputStream = connection.getInputStream()) {
                            String body = readFully(inputStream).trim();
                            if (!body.startsWith("{")) {
                                throw new IllegalStateException("Battly+ entitlements returned non-JSON response");
                            }
                            JSONObject response = new JSONObject(body);
                            Boolean premium = findPremiumFlag(response);
                            if (premium != null) {
                                current = premium;
                                setPlus(appContext, premium);
                                accountPrefs(appContext).edit().putString(PREF_TOKEN, token).apply();
                                matchedToken = true;
                            }
                        }
                    }
                    connection.disconnect();
                    if (matchedToken) {
                        break;
                    }
                } catch (Throwable ignored) {
                    current = isPlus(appContext);
                }
            }
            boolean finalCurrent = current;
            if (callback != null) {
                Tools.runOnUiThread(() -> callback.onResult(finalCurrent));
            }
        }, "Battly+ Refresh").start();
    }

    private static Boolean findPremiumFlag(JSONObject root) {
        if (root == null) {
            return null;
        }
        Boolean fallback = findPremiumFlagInObject(root);
        if (Boolean.TRUE.equals(fallback)) return true;
        java.util.Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject child = root.optJSONObject(key);
            if (child == null) {
                continue;
            }
            Boolean nested = findPremiumFlag(child);
            if (Boolean.TRUE.equals(nested)) return true;
            if (fallback == null && nested != null) fallback = nested;
        }
        return fallback;
    }

    private static Boolean findPremiumFlagInObject(JSONObject object) {
        if (object == null) {
            return null;
        }
        String[] trueKeys = {
                "battlyWorldsPlus",
                "battlyPlus",
                "battly_plus",
                "battlyplus",
                "isPremium",
                "is_premium",
                "premium",
                "plus"
        };
        for (String key : trueKeys) {
            if (object.has(key)) {
                Object value = object.opt(key);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }
                if (value instanceof Number) {
                    return ((Number) value).intValue() == 1;
                }
                return isPremiumText(String.valueOf(value));
            }
        }
        String tier = object.optString("tier",
                object.optString("plan", object.optString("role",
                        object.optString("status", object.optString("subscriptionId", "")))));
        if (Tools.isValidString(tier)) {
            return isPremiumText(tier);
        }
        return null;
    }

    private static boolean isPremiumText(String value) {
        if (!Tools.isValidString(value)) {
            return false;
        }
        String text = value.trim();
        return "true".equalsIgnoreCase(text)
                || "1".equals(text)
                || "yes".equalsIgnoreCase(text)
                || "active".equalsIgnoreCase(text)
                || "plus".equalsIgnoreCase(text)
                || "premium".equalsIgnoreCase(text)
                || "battly+".equalsIgnoreCase(text)
                || "forever-boost".equalsIgnoreCase(text)
                || text.toLowerCase(java.util.Locale.ROOT).contains("premium")
                || text.toLowerCase(java.util.Locale.ROOT).contains("plus");
    }

    private static String readFully(InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static SharedPreferences accountPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String tokenFromAccount(MinecraftAccount account) {
        if (account == null || !account.isBattly()) {
            return "";
        }
        return account.accessToken == null ? "" : account.accessToken.trim();
    }

    private static boolean isUsableBattlyToken(String token) {
        return Tools.isValidString(token)
                && !"0".equals(token)
                && !"battly".equalsIgnoreCase(token)
                && token.length() >= 32;
    }

    private static void addToken(List<String> tokens, Set<String> seen, String token) {
        if (seen.add(token)) {
            tokens.add(token);
        }
    }

    private static void saveResolvedToken(Context context, MinecraftAccount account, String token) {
        SharedPreferences.Editor editor = accountPrefs(context).edit().putString(PREF_TOKEN, token);
        if (account != null) {
            editor.putString("battly_username", account.username == null ? "" : account.username);
            editor.putString("battly_uuid", account.profileId == null ? "" : account.profileId);
        }
        editor.apply();
    }
}
