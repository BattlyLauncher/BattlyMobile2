package net.kdt.pojavlaunch.tasks;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;
import static net.kdt.pojavlaunch.utils.DownloadUtils.downloadString;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.BattlyOfflineMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/** Class getting the version list, and that's all really */
public class AsyncVersionList {

    public void getVersionList(@Nullable VersionDoneListener listener, boolean secondPass){
        sExecutorService.execute(() -> {
            File versionFile = new File(Tools.DIR_CACHE + "/version_list.json");
            JMinecraftVersionList versionList = null;
            try{
                boolean offline = BattlyOfflineMode.isOffline(PojavApplication.getAppContext());
                if(!offline && (!versionFile.exists()
                        || System.currentTimeMillis() > versionFile.lastModified() + 86400000)){
                    versionList = downloadVersionList(LauncherPreferences.PREF_VERSION_REPOS);
                }
            }catch (Exception e){
                Log.e("AsyncVersionList", "Refreshing version list failed :" + e);
                e.printStackTrace();
            }

            // Fallback when no network or not needed
            if (versionList == null) {
                try {
                    versionList = Tools.GLOBAL_GSON.fromJson(new JsonReader(new FileReader(versionFile)), JMinecraftVersionList.class);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (JsonIOException | JsonSyntaxException e) {
                    Log.e("AsyncVersionList", "Saved version list is invalid; preserving it for recovery", e);
                }
            }

            if(listener != null)
                listener.onVersionDone(versionList);
        });
    }


    @SuppressWarnings("SameParameterValue")
    private JMinecraftVersionList downloadVersionList(String mirror){
        JMinecraftVersionList list = null;
        try{
            Log.i("ExtVL", "Syncing to external: " + mirror);
            String jsonString = downloadString(mirror);
            list = Tools.GLOBAL_GSON.fromJson(jsonString, JMinecraftVersionList.class);
            Log.i("ExtVL","Downloaded the version list, len=" + list.versions.length);

            // Then save the version list
            //TODO make it not save at times ?
            File destination = new File(Tools.DIR_CACHE, "version_list.json");
            File temporary = new File(Tools.DIR_CACHE, "version_list.json.download");
            File backup = new File(Tools.DIR_CACHE, "version_list.json.backup");
            try (FileOutputStream fos = new FileOutputStream(temporary)) {
                fos.write(jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fos.getFD().sync();
            }
            if (backup.exists() && !backup.delete())
                throw new IOException("Unable to clear old version list backup");
            boolean hadDestination = destination.isFile();
            if (hadDestination && !destination.renameTo(backup))
                throw new IOException("Unable to back up cached version list");
            if (!temporary.renameTo(destination)) {
                if (hadDestination && !backup.renameTo(destination))
                    Log.e("AsyncVersionList", "Unable to restore cached version list backup");
                throw new IOException("Unable to activate cached version list");
            }
            if (backup.exists() && !backup.delete())
                Log.w("AsyncVersionList", "Unable to remove old version list backup");



        }catch (IOException e){
            Log.e("AsyncVersionList", e.toString());
        }
        return list;
    }

    /** Basic listener, acting as a callback */
    public interface VersionDoneListener{
        void onVersionDone(JMinecraftVersionList versions);
    }

}
