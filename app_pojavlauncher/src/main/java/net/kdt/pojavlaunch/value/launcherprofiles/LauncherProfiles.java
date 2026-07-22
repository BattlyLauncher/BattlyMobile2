package net.kdt.pojavlaunch.value.launcherprofiles;

import android.util.Log;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LauncherProfiles {
    public static MinecraftLauncherProfiles mainProfileJson;
    private static final File launcherProfilesFile = new File(Tools.GAME_PROFILES_FILE);

    /** Reload the profile from the file, creating a default one if necessary */
    public static void load(){
        boolean recoveredCorruptFile = false;
        if (launcherProfilesFile.exists()) {
            try {
                mainProfileJson = Tools.GLOBAL_GSON.fromJson(Tools.read(launcherProfilesFile.getAbsolutePath()), MinecraftLauncherProfiles.class);
            } catch (Exception e) {
                Log.e(LauncherProfiles.class.toString(), "Failed to load launcher profiles; preserving the corrupt file", e);
                File backup = new File(launcherProfilesFile.getParentFile(),
                        launcherProfilesFile.getName() + ".corrupt-" + System.currentTimeMillis());
                if (!launcherProfilesFile.renameTo(backup)) {
                    Log.w(LauncherProfiles.class.toString(), "Could not preserve corrupt launcher profiles file");
                }
                mainProfileJson = null;
                recoveredCorruptFile = true;
            }
        }

        // Fill with default
        if (mainProfileJson == null) mainProfileJson = new MinecraftLauncherProfiles();
        if (mainProfileJson.profiles == null) mainProfileJson.profiles = new HashMap<>();
        if (mainProfileJson.profiles.size() == 0)
            mainProfileJson.profiles.put(UUID.randomUUID().toString(), MinecraftProfile.getDefaultProfile());

        if (recoveredCorruptFile) {
            try {
                write();
            } catch (RuntimeException writeFailure) {
                Log.e(LauncherProfiles.class.toString(), "Failed to persist recovered launcher profiles", writeFailure);
            }
        }

        // Normalize profile names from mod installers
        if(normalizeProfileIds(mainProfileJson) || ensureBattlyInstanceMetadata(mainProfileJson)){
            write();
            load();
        }
    }

    /** Apply the current configuration into a file */
    public static void write() {
        try {
            Tools.write(launcherProfilesFile.getAbsolutePath(), mainProfileJson.toJson());
        } catch (IOException e) {
            Log.e(LauncherProfiles.class.toString(), "Failed to write profile file", e);
            throw new RuntimeException(e);
        }
    }

    public static @NonNull MinecraftProfile getCurrentProfile() {
        if(mainProfileJson == null) LauncherProfiles.load();
        String defaultProfileName = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile profile = mainProfileJson.profiles.get(defaultProfileName);
        if(profile == null) {
            Log.w(LauncherProfiles.class.toString(), "Current profile is missing, falling back to the first available profile: " + defaultProfileName);
            if (mainProfileJson.profiles == null || mainProfileJson.profiles.isEmpty()) {
                LauncherProfiles.load();
            }
            Map.Entry<String, MinecraftProfile> fallback = mainProfileJson.profiles.entrySet().iterator().next();
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, fallback.getKey())
                    .apply();
            profile = fallback.getValue();
        }
        return profile;
    }

    public static void createRecoverableDefault() {
        mainProfileJson = new MinecraftLauncherProfiles();
        if (mainProfileJson.profiles == null) mainProfileJson.profiles = new HashMap<>();
        mainProfileJson.profiles.put(UUID.randomUUID().toString(), MinecraftProfile.getDefaultProfile());
    }

    /**
     * Insert a new profile into the profile map
     * @param minecraftProfile the profile to insert
     */
    public static void insertMinecraftProfile(MinecraftProfile minecraftProfile) {
        mainProfileJson.profiles.put(getFreeProfileKey(), minecraftProfile);
    }

    /**
     * Pick an unused normalized key to store a new profile with
     * @return an unused key
     */
    public static String getFreeProfileKey() {
        Map<String, MinecraftProfile> profileMap = mainProfileJson.profiles;
        String freeKey = UUID.randomUUID().toString();
        while(profileMap.get(freeKey) != null) freeKey = UUID.randomUUID().toString();
        return freeKey;
    }

    /**
     * For all keys to be UUIDs, effectively isolating profile created by installers
     * This avoids certain profiles to be erased by the installer
     * @return Whether some profiles have been normalized
     */
    private static boolean normalizeProfileIds(MinecraftLauncherProfiles launcherProfiles){
        boolean hasNormalized = false;
        ArrayList<String> keys = new ArrayList<>();

        // Detect denormalized keys
        for(String profileKey : launcherProfiles.profiles.keySet()){
            try{
                if(!UUID.fromString(profileKey).toString().equals(profileKey)) keys.add(profileKey);
            }catch (IllegalArgumentException exception){
                keys.add(profileKey);
                Log.w(LauncherProfiles.class.toString(), "Illegal profile uuid: " + profileKey);
            }
        }

        // Swap the new keys
        for(String profileKey : keys){
            MinecraftProfile currentProfile = launcherProfiles.profiles.get(profileKey);
            insertMinecraftProfile(currentProfile);
            launcherProfiles.profiles.remove(profileKey);
            hasNormalized = true;
        }

        return hasNormalized;
    }

    private static boolean ensureBattlyInstanceMetadata(MinecraftLauncherProfiles launcherProfiles) {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, MinecraftProfile> entry : launcherProfiles.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null) continue;
            if (!Tools.isValidString(profile.battlyInstanceId)) {
                profile.battlyInstanceId = entry.getKey();
                changed = true;
            }
            if (profile.battlySchemaVersion < 1) {
                profile.battlySchemaVersion = 1;
                changed = true;
            }
            if (profile.battlyCreatedAt <= 0) {
                profile.battlyCreatedAt = now;
                changed = true;
            }
            if (profile.battlyUpdatedAt <= 0) {
                profile.battlyUpdatedAt = profile.battlyCreatedAt;
                changed = true;
            }
        }
        return changed;
    }
}
