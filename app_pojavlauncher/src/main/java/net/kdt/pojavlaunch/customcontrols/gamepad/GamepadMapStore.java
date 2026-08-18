package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.util.Log;

import com.google.gson.JsonParseException;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;

public class GamepadMapStore {
    private static final File LEGACY_STORE_FILE = new File(Tools.DIR_DATA, "gamepad_map.json");
    private static GamepadMapStore sMapStore;
    private GamepadMap mInMenuMap;
    private GamepadMap mInGameMap;
    private static GamepadMapStore createDefault() {
        GamepadMapStore mapStore = new GamepadMapStore();
        mapStore.mInGameMap = GamepadMap.getDefaultGameMap();
        mapStore.mInMenuMap = GamepadMap.getDefaultMenuMap();
        return mapStore;
    }

    private static void loadIfNecessary() {
        if(sMapStore == null) load();
    }

    public static void load() {
        GamepadMapStore mapStore = null;
        File storeFile = getStoreFile();
        migrateLegacyStore(storeFile);
        if(storeFile.exists() && storeFile.canRead()) {
            try {
                String storeFileContent = Tools.read(storeFile);
                mapStore = Tools.GLOBAL_GSON.fromJson(storeFileContent, GamepadMapStore.class);
            } catch (JsonParseException | IOException e) {
                Log.w("GamepadMapStore", "Map store failed to load!", e);
            }
        }
        if(mapStore == null) mapStore = createDefault();
        sMapStore = mapStore;
    }

    public static void save() throws IOException {
        if(sMapStore == null) throw new RuntimeException("Must load map store first!");
        File storeFile = getStoreFile();
        FileUtils.ensureParentDirectory(storeFile);
        String jsonData = Tools.GLOBAL_GSON.toJson(sMapStore);
        Tools.write(storeFile.getAbsolutePath(), jsonData);
    }

    public static GamepadMap getGameMap() {
        loadIfNecessary();
        return sMapStore.mInGameMap;
    }

    public static GamepadMap getMenuMap() {
        loadIfNecessary();
        return sMapStore.mInMenuMap;
    }

    public static void applyMinecraftDefaults(ControllerTypeResolver.Style style) {
        loadIfNecessary();
        sMapStore.mInGameMap = MinecraftControllerDefaults.createGameMap(style);
        sMapStore.mInMenuMap = MinecraftControllerDefaults.createMenuMap(style);
    }

    public static void invalidate() {
        sMapStore = null;
    }

    public static File getStoreFile() {
        try {
            LauncherProfiles.load();
            String key = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "default");
            MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
            String identity = profile != null && Tools.isValidString(profile.gamepadProfile)
                    ? profile.gamepadProfile
                    : profile != null && Tools.isValidString(profile.battlyInstanceId)
                    ? profile.battlyInstanceId : key;
            identity = identity == null ? "default" : identity.replaceAll("[^A-Za-z0-9._-]", "_");
            return new File(Tools.DIR_DATA, "gamepad_profiles/" + identity + ".json");
        } catch (Throwable ignored) {
            return LEGACY_STORE_FILE;
        }
    }

    private static void migrateLegacyStore(File target) {
        if (target.equals(LEGACY_STORE_FILE) || target.exists() || !LEGACY_STORE_FILE.isFile()) return;
        try {
            FileUtils.ensureParentDirectory(target);
            org.apache.commons.io.FileUtils.copyFile(LEGACY_STORE_FILE, target);
        } catch (IOException exception) {
            Log.w("GamepadMapStore", "Unable to migrate legacy gamepad map", exception);
        }
    }
}
