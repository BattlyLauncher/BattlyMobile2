package net.kdt.pojavlaunch.value.launcherprofiles;

import android.util.Log;
import android.util.AtomicFile;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LauncherProfiles {
    public static MinecraftLauncherProfiles mainProfileJson;
    private static final File launcherProfilesFile = new File(Tools.GAME_PROFILES_FILE);
    private static final AtomicFile launcherProfilesAtomicFile = new AtomicFile(launcherProfilesFile);
    private static final File lastGoodProfilesFile = new File(Tools.GAME_PROFILES_FILE + ".lastgood");
    private static final AtomicFile lastGoodProfilesAtomicFile = new AtomicFile(lastGoodProfilesFile);

    /** Reload the profile from the file, creating a default one if necessary */
    public static synchronized void load(){
        boolean recoveredCorruptFile = false;
        if (launcherProfilesFile.exists()) {
            try {
                mainProfileJson = parseProfiles(Tools.read(launcherProfilesAtomicFile.openRead()));
            } catch (Exception e) {
                Log.e(LauncherProfiles.class.toString(), "Failed to load launcher profiles; preserving the corrupt file", e);
                preserveCorruptFile();
                mainProfileJson = recoverLatestValidProfiles();
                recoveredCorruptFile = true;
            }
        }

        // Fill with default
        if (mainProfileJson == null) mainProfileJson = new MinecraftLauncherProfiles();
        if (mainProfileJson.profiles == null) mainProfileJson.profiles = new HashMap<>();
        if (mainProfileJson.profiles.size() == 0) {
            String key = UUID.randomUUID().toString();
            putNewProfile(key, MinecraftProfile.getDefaultProfile());
        }

        MinecraftLauncherProfiles previousProfiles = recoverLatestValidProfiles();
        if (shouldPreferRecoveredProfiles(mainProfileJson, previousProfiles)) {
            mainProfileJson = previousProfiles;
            recoveredCorruptFile = true;
        }

        boolean changed = normalizeProfileIds(mainProfileJson)
                | ensureBattlyInstanceMetadata(mainProfileJson)
                | !lastGoodProfilesFile.isFile();
        if (recoveredCorruptFile || changed) {
            try {
                write();
            } catch (RuntimeException writeFailure) {
                Log.e(LauncherProfiles.class.toString(), "Failed to persist recovered launcher profiles", writeFailure);
            }
        }

    }

    /** Apply the current configuration into a file */
    public static synchronized void write() {
        try {
            if (mainProfileJson == null) throw new IOException("Launcher profiles are not loaded");
            File parent = launcherProfilesFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Unable to create launcher profile directory");
            }
            byte[] json = mainProfileJson.toJson().getBytes(StandardCharsets.UTF_8);
            writeAtomic(launcherProfilesAtomicFile, json);
            try {
                writeAtomic(lastGoodProfilesAtomicFile, json);
            } catch (IOException backupFailure) {
                Log.e(LauncherProfiles.class.toString(), "Failed to update last known good profiles", backupFailure);
            }
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
        String key = UUID.randomUUID().toString();
        putNewProfile(key, MinecraftProfile.getDefaultProfile());
    }

    /**
     * Insert a new profile into the profile map
     * @param minecraftProfile the profile to insert
     */
    public static String insertMinecraftProfile(MinecraftProfile minecraftProfile) {
        String key = getFreeProfileKey();
        putNewProfile(key, minecraftProfile);
        return key;
    }

    /** Store a newly-created profile without changing paths on existing profiles. */
    public static void putNewProfile(String profileKey, MinecraftProfile minecraftProfile) {
        InstanceDirectoryPolicy.applyToNewProfile(minecraftProfile, profileKey);
        mainProfileJson.profiles.put(profileKey, minecraftProfile);
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
        String selected = LauncherPreferences.DEFAULT_PREF == null ? ""
                : LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");

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
            String replacementKey = freeProfileKey(launcherProfiles.profiles);
            launcherProfiles.profiles.put(replacementKey, currentProfile);
            launcherProfiles.profiles.remove(profileKey);
            if (profileKey.equals(selected) && LauncherPreferences.DEFAULT_PREF != null) {
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, replacementKey)
                        .apply();
                selected = replacementKey;
            }
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

    private static MinecraftLauncherProfiles parseProfiles(String json) throws IOException {
        MinecraftLauncherProfiles profiles = Tools.GLOBAL_GSON.fromJson(json, MinecraftLauncherProfiles.class);
        if (profiles == null || profiles.profiles == null || profiles.profiles.isEmpty()) {
            throw new IOException("Launcher profile index is empty or invalid");
        }
        return profiles;
    }

    private static void preserveCorruptFile() {
        if (!launcherProfilesFile.exists()) return;
        File backup = new File(launcherProfilesFile.getParentFile(),
                launcherProfilesFile.getName() + ".corrupt-" + System.currentTimeMillis());
        if (!launcherProfilesFile.renameTo(backup)) {
            Log.w(LauncherProfiles.class.toString(), "Could not preserve corrupt launcher profiles file");
        }
    }

    private static MinecraftLauncherProfiles recoverLatestValidProfiles() {
        if (lastGoodProfilesFile.isFile()) {
            try {
                return parseProfiles(Tools.read(lastGoodProfilesAtomicFile.openRead()));
            } catch (Exception ignored) {
            }
        }
        File parent = launcherProfilesFile.getParentFile();
        File[] candidates = parent == null ? null : parent.listFiles((dir, name) ->
                name.startsWith(launcherProfilesFile.getName() + ".corrupt-"));
        if (candidates == null) return null;
        Arrays.sort(candidates, Comparator.comparingLong(File::lastModified).reversed());
        for (File candidate : candidates) {
            try {
                MinecraftLauncherProfiles recovered = parseProfiles(Tools.read(candidate));
                Log.w(LauncherProfiles.class.toString(), "Recovered launcher profiles from " + candidate.getName());
                return recovered;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean shouldPreferRecoveredProfiles(MinecraftLauncherProfiles current,
                                                          MinecraftLauncherProfiles recovered) {
        if (recovered == null || recovered.profiles == null || recovered.profiles.isEmpty()) return false;
        if (current == null || current.profiles == null || current.profiles.isEmpty()) return true;
        if (recovered.profiles.size() > current.profiles.size()) return isGeneratedDefaultOnly(current);
        return isGeneratedDefaultOnly(current) && !isGeneratedDefaultOnly(recovered);
    }

    private static boolean isGeneratedDefaultOnly(MinecraftLauncherProfiles profiles) {
        if (profiles == null || profiles.profiles == null || profiles.profiles.size() != 1) return false;
        Map.Entry<String, MinecraftProfile> entry = profiles.profiles.entrySet().iterator().next();
        MinecraftProfile profile = entry.getValue();
        if (profile == null) return false;
        if (Tools.isValidString(profile.gameDir)
                && !InstanceDirectoryPolicy.isolatedGameDir(profile.name, entry.getKey())
                .equals(profile.gameDir)) return false;
        String name = profile.name == null ? "" : profile.name.trim();
        return ("Default".equalsIgnoreCase(name) || "(Default)".equalsIgnoreCase(name))
                && "1.7.10".equals(profile.lastVersionId);
    }

    private static void writeAtomic(AtomicFile file, byte[] content) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(content);
            output.flush();
            file.finishWrite(output);
        } catch (IOException exception) {
            if (output != null) file.failWrite(output);
            throw exception;
        }
    }

    private static String freeProfileKey(Map<String, MinecraftProfile> profiles) {
        String key = UUID.randomUUID().toString();
        while (profiles.containsKey(key)) key = UUID.randomUUID().toString();
        return key;
    }
}
