package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.DependentLibrary;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class OfflineSkinManager {
    private static final String PREFS_NAME = "battly_offline_skins";
    private static final String KEY_KEEP_ENABLED = "keep_selected_skin_offline";
    private static final String KEY_SLIM_PREFIX = "slim_";

    private OfflineSkinManager() {
    }

    public static boolean isKeepEnabled(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_ENABLED, true);
    }

    public static void setKeepEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEEP_ENABLED, enabled).apply();
    }

    public static File getSkinFile(String username) {
        String cleanName = sanitizeUsername(username);
        return new File(Tools.DIR_GAME_HOME, "offline-skins/" + cleanName + ".png");
    }

    public static boolean hasSkinFor(MinecraftAccount account) {
        return account != null && getSkinFile(account.username).isFile();
    }

    public static void saveSkin(Context context, String username, byte[] pngBytes) throws IOException {
        if (!isPng(pngBytes)) {
            throw new IOException("Invalid PNG skin");
        }
        File target = getSkinFile(username);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create offline skin directory");
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(pngBytes);
        }
        setKeepEnabled(context, true);
        Logger.appendToLog("Info: Offline skin saved for " + sanitizeUsername(username));
    }

    public static void saveSkin(Context context, String username, Bitmap bitmap) throws IOException {
        if (bitmap == null) {
            throw new IOException("Invalid skin bitmap");
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
            throw new IOException("Could not encode skin PNG");
        }
        saveSkin(context, username, outputStream.toByteArray());
    }

    public static void appendJvmArgs(Context context, MinecraftAccount account,
                                     JMinecraftVersionList.Version version, List<String> javaArgs) {
        if (context == null || account == null || javaArgs == null || !isKeepEnabled(context)) {
            return;
        }
        if (usesReservedUrlFactory(version)) {
            Logger.appendToLog("Info: Offline skin injector skipped for "
                    + (version == null ? "unknown version" : version.id)
                    + " because its mod loader owns the URL handler factory");
            return;
        }
        File skinFile = getSkinFile(account.username);
        if (!skinFile.isFile()) {
            return;
        }
        String username = account.username == null ? "Steve" : account.username.replace("Demo.", "");
        String uuid = account.getProfileIdForLaunch();
        javaArgs.add("-Dbattly.offlineSkin.enabled=true");
        javaArgs.add("-Dbattly.offlineSkin.path=" + skinFile.getAbsolutePath());
        javaArgs.add("-Dbattly.offlineSkin.username=" + username);
        javaArgs.add("-Dbattly.offlineSkin.uuid=" + (uuid == null ? "" : uuid.replace("-", "")));
        javaArgs.add("-Dbattly.offlineSkin.slim=" + prefs(context).getBoolean(KEY_SLIM_PREFIX + sanitizeUsername(username), false));
        Logger.appendToLog("Info: Offline skin injector enabled for " + username);
    }

    private static boolean usesReservedUrlFactory(JMinecraftVersionList.Version version) {
        if (version == null) {
            return true;
        }
        String id = version.id == null ? "" : version.id.toLowerCase(Locale.ROOT);
        String inherited = version.inheritsFrom == null ? "" : version.inheritsFrom.toLowerCase(Locale.ROOT);
        String mainClass = version.mainClass == null ? "" : version.mainClass.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher minecraftVersion = java.util.regex.Pattern
                .compile("(?:^|[^0-9])(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
                .matcher(id + " " + inherited);
        if (!minecraftVersion.find()) {
            return true;
        }
        int major = parseInt(minecraftVersion.group(1));
        int minor = parseInt(minecraftVersion.group(2));
        int patch = parseInt(minecraftVersion.group(3));
        if (major != 1 || minor < 7 || (minor == 7 && patch < 10) || minor >= 17) {
            return true;
        }
        if (id.contains("forge") || id.contains("neoforge")
                || mainClass.contains("modlauncher") || mainClass.contains("bootstraplauncher")) {
            return true;
        }
        if (version.libraries == null) {
            return false;
        }
        for (DependentLibrary library : version.libraries) {
            if (library == null || library.name == null) {
                continue;
            }
            String name = library.name.toLowerCase(Locale.ROOT);
            if (name.startsWith("net.minecraftforge:") || name.startsWith("net.neoforged:")
                    || name.contains("securejarhandler") || name.contains("modlauncher")) {
                return true;
            }
        }
        return false;
    }

    private static int parseInt(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isPng(byte[] bytes) {
        return bytes != null
                && bytes.length > 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String sanitizeUsername(String username) {
        String value = username == null ? "Steve" : username.replace("Demo.", "");
        return value.replaceAll("[^A-Za-z0-9_\\-.]", "_").toLowerCase(Locale.ROOT);
    }
}
