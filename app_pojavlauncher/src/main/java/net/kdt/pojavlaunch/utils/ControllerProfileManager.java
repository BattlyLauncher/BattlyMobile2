package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadMapStore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/** Per-instance controller tuning while retaining the existing remapper format. */
public final class ControllerProfileManager {
    private static final String[] BOOLEAN_KEYS = {
            "enableGyro", "gyroSmoothing", "gyroInvertX", "gyroInvertY",
            "gamepadPassthru", "gamepadPassthruForced"
    };
    private static final String[] INTEGER_KEYS = {
            "gyroSensitivity", "gyroSampleRate", "gamepad_deadzone_scale"
    };

    private ControllerProfileManager() {
    }

    public static void save(MinecraftProfile profile) throws IOException {
        JSONObject root = new JSONObject();
        SharedPreferences preferences = LauncherPreferences.DEFAULT_PREF;
        try {
            root.put("schema", "battly-controller-profile-v1");
            for (String key : BOOLEAN_KEYS) root.put(key, preferences.getBoolean(key, false));
            for (String key : INTEGER_KEYS) root.put(key, preferences.getInt(key, defaultInt(key)));
            root.put("mapFile", GamepadMapStore.getStoreFile().getAbsolutePath());
            File file = fileFor(profile);
            FileUtils.ensureParentDirectory(file);
            Tools.write(file.getAbsolutePath(), root.toString(2));
        } catch (Exception exception) {
            if (exception instanceof IOException) throw (IOException) exception;
            throw new IOException("Unable to save controller profile", exception);
        }
    }

    public static boolean apply(Context context, MinecraftProfile profile) {
        File file = fileFor(profile);
        if (!file.isFile()) return false;
        try {
            JSONObject root = new JSONObject(Tools.read(file.getAbsolutePath()));
            SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
            for (String key : BOOLEAN_KEYS) if (root.has(key)) editor.putBoolean(key, root.getBoolean(key));
            for (String key : INTEGER_KEYS) if (root.has(key)) editor.putInt(key, root.getInt(key));
            editor.apply();
            LauncherPreferences.loadPreferences(context.getApplicationContext());
            GamepadMapStore.invalidate();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static File fileFor(MinecraftProfile profile) {
        String id = profile != null && Tools.isValidString(profile.battlyInstanceId)
                ? profile.battlyInstanceId : "default";
        return new File(Tools.DIR_DATA, "controller_profiles/" + id.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    private static int defaultInt(String key) {
        if ("gyroSensitivity".equals(key) || "gamepad_deadzone_scale".equals(key)) return 100;
        if ("gyroSampleRate".equals(key)) return 16;
        return 0;
    }
}
