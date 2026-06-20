package net.kdt.pojavlaunch.tasks;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;

public class AsyncMinecraftDownloader {
    public static String normalizeVersionId(String versionString) {
        JMinecraftVersionList versionList = (JMinecraftVersionList) ExtraCore.getValue(ExtraConstants.RELEASE_TABLE);
        if(versionList == null || versionList.versions == null) return versionString;
        if(MinecraftProfile.LATEST_RELEASE.equals(versionString)) versionString = versionList.latest.get("release");
        if(MinecraftProfile.LATEST_SNAPSHOT.equals(versionString)) versionString = versionList.latest.get("snapshot");
        return versionString;
    }

    public static JMinecraftVersionList.Version getListedVersion(String normalizedVersionString) {
        JMinecraftVersionList versionList = getVersionListFromMemoryOrCache();
        if(versionList == null || versionList.versions == null) return null; // can't have listed versions if there's no list
        for(JMinecraftVersionList.Version version : versionList.versions) {
            if(version.id.equals(normalizedVersionString)) return version;
        }
        return null;
    }

    private static JMinecraftVersionList getVersionListFromMemoryOrCache() {
        JMinecraftVersionList versionList = (JMinecraftVersionList) ExtraCore.getValue(ExtraConstants.RELEASE_TABLE);
        if (versionList != null) {
            return versionList;
        }
        File versionFile = new File(Tools.DIR_CACHE, "version_list.json");
        if (!versionFile.isFile()) {
            return null;
        }
        try {
            versionList = Tools.GLOBAL_GSON.fromJson(Tools.read(versionFile), JMinecraftVersionList.class);
            if (versionList != null) {
                ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versionList);
            }
            return versionList;
        } catch (IOException ignored) {
            return null;
        }
    }

    public interface DoneListener{
        void onDownloadDone();
        void onDownloadFailed(Throwable throwable);
    }
}
