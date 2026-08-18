package net.kdt.pojavlaunch.value;


import android.graphics.BitmapFactory;
import android.util.Log;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.*;
import com.google.gson.*;
import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.apache.commons.io.IOUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressWarnings("IOStreamConstructor")
@Keep
public class MinecraftAccount {
    private static final Object ACCOUNT_SAVE_LOCK = new Object();
    public String accessToken = "0"; // access token
    public String clientToken = "0"; // clientID: refresh and invalidate
    public String profileId = "00000000-0000-0000-0000-000000000000"; // profile UUID, for obtaining skin
    public String username = "Steve";
    public String selectedVersion = "1.7.10";
    public boolean isMicrosoft = false;
    public String msaRefreshToken = "0";
    public String xuid;
    public long expiresAt;
    public String skinFaceBase64;
    private Bitmap mFaceCache;
    
    void updateSkinFace(String uuid) {
        try {
            File skinFile = getSkinFaceFile(username);
            String faceUrl = isBattly()
                    ? "https://api.battlylauncher.com/api/face/" + URLEncoder.encode(username, StandardCharsets.UTF_8.name())
                    : "https://mc-heads.net/head/" + uuid + "/100";
            Tools.downloadFile(faceUrl, skinFile.getAbsolutePath());
            mFaceCache = null;
            
            Log.i("SkinLoader", "Update skin face success");
        } catch (IOException e) {
            // Skin refresh limit, no internet connection, etc...
            // Simply ignore updating skin face
            Log.w("SkinLoader", "Could not update skin face", e);
        }
    }

    public boolean isLocal(){
        return accessToken.equals("0") && !username.startsWith("Demo.");
    }

    public boolean isBattly() {
        return !isLocal() && !isMicrosoft && !isDemo();
    }

    public boolean isDemo(){
        return username.startsWith("Demo.");
    }

    /**
     * Identifies the placeholder Microsoft profile created by older launcher versions when
     * Minecraft: Java Edition ownership could not be verified.
     */
    public boolean isLegacyMicrosoftDemo() {
        return isMicrosoft && isDemo() && isDefaultProfileId(profileId);
    }
    
    public void updateSkinFace() {
        updateSkinFace(profileId);
    }

    public String getProfileIdForLaunch() {
        if (isBattly() && isDefaultProfileId(profileId)) {
            return getOfflineUuid(username);
        }
        return profileId;
    }
    
    public String save(String outPath) throws IOException {
        File target = new File(outPath);
        File temp = new File(outPath + ".tmp");
        File backup = new File(outPath + ".bak");
        synchronized (ACCOUNT_SAVE_LOCK) {
            FileUtils.ensureParentDirectory(target);
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(Tools.GLOBAL_GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.getFD().sync();
            }

            if (backup.exists() && !backup.delete()) {
                throw new IOException("Unable to remove stale account backup");
            }
            if (target.exists() && !target.renameTo(backup)) {
                throw new IOException("Unable to back up existing account");
            }
            if (!temp.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target);
                throw new IOException("Unable to replace account atomically");
            }
            if (backup.exists() && !backup.delete()) {
                Log.w("MinecraftAccount", "Could not remove account backup after save");
            }
        }
        return username;
    }
    
    public String save() throws IOException {
        return save(Tools.DIR_ACCOUNT_NEW + "/" + username + ".json");
    }
    
    public static MinecraftAccount parse(String content) throws JsonSyntaxException {
        return Tools.GLOBAL_GSON.fromJson(content, MinecraftAccount.class);
    }
    @Nullable
    public static MinecraftAccount load(String name) {
        if(!accountExists(name)) return null;
        try {
            MinecraftAccount acc = parse(Tools.read(Tools.DIR_ACCOUNT_NEW + "/" + name + ".json"));
            if (acc.accessToken == null) {
                acc.accessToken = "0";
            }
            if (acc.clientToken == null) {
                acc.clientToken = "0";
            }
            if (acc.profileId == null) {
                acc.profileId = "00000000-0000-0000-0000-000000000000";
            }
            if (acc.username == null) {
                acc.username = "0";
            }
            if (acc.selectedVersion == null) {
                acc.selectedVersion = "1.7.10";
            }
            if (acc.msaRefreshToken == null) {
                acc.msaRefreshToken = "0";
            }
            if (acc.isBattly() && isDefaultProfileId(acc.profileId)) {
                acc.profileId = getOfflineUuid(acc.username);
            }
            return acc;
        } catch(NullPointerException | IOException | JsonSyntaxException e) {
            Log.e(MinecraftAccount.class.getName(), "Caught an exception while loading the profile",e);
            return null;
        }
    }

    public Bitmap getSkinFace(){
        if(isLocal()) return null;

        File skinFaceFile = getSkinFaceFile(username);
        if (!skinFaceFile.exists()) {
            // Legacy version, storing the head inside the json as base 64
            if(skinFaceBase64 == null) return null;
            byte[] faceIconBytes = Base64.decode(skinFaceBase64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(faceIconBytes, 0, faceIconBytes.length);
        } else {
            if(mFaceCache == null) {
                mFaceCache = BitmapFactory.decodeFile(skinFaceFile.getAbsolutePath());
            }
        }

        return mFaceCache;
    }

    public static Bitmap getSkinFace(String username) {
        return BitmapFactory.decodeFile(getSkinFaceFile(username).getAbsolutePath());
    }

    private static File getSkinFaceFile(String username) {
        return new File(Tools.DIR_CACHE, username + ".png");
    }

    private static boolean isDefaultProfileId(String profileId) {
        return profileId == null
                || profileId.isEmpty()
                || "00000000-0000-0000-0000-000000000000".equals(profileId)
                || "00000000000000000000000000000000".equals(profileId);
    }

    private static String getOfflineUuid(String username) {
        String cleanUsername = username == null ? "Steve" : username.replace("Demo.", "");
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + cleanUsername).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean accountExists(String username){
        return new File(Tools.DIR_ACCOUNT_NEW + "/" + username + ".json").exists();
    }
}
