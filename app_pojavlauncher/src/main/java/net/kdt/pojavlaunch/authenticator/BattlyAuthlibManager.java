package net.kdt.pojavlaunch.authenticator;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.utils.BattlyOfflineMode;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public final class BattlyAuthlibManager {
    private static final String TAG = "BattlyAuthlib";
    private static final String FILES_URL = "https://api.battlylauncher.com/battlylauncher/files";
    private static final String AUTHLIB_PATH = "authlib-injector.jar";
    private static final String AUTH_SERVER = "https://api.battlylauncher.com";
    private static final String DOWNLOAD_SUFFIX = ".download";
    private static final String BACKUP_SUFFIX = ".backup";

    private BattlyAuthlibManager() {
    }

    public static File ensureAuthlib() throws IOException {
        File cachedAuthlib = new File(Tools.DIR_GAME_HOME, AUTHLIB_PATH);
        if (PojavApplication.getAppContext() != null
                && BattlyOfflineMode.isOffline(PojavApplication.getAppContext())) {
            if (cachedAuthlib.isFile()) return cachedAuthlib;
            throw new IOException("Battly authlib is not installed and offline mode is active");
        }
        JSONArray files;
        try {
            files = new JSONArray(DownloadUtils.downloadString(FILES_URL));
        } catch (IOException | JSONException e) {
            if (cachedAuthlib.isFile()) {
                Log.w(TAG, "Using cached Battly authlib because manifest could not be refreshed", e);
                return cachedAuthlib;
            }
            throw new IOException("Unable to load Battly files manifest", e);
        }
        JSONObject authlibEntry = selectAndroidAuthlib(files);

        if (authlibEntry == null) {
            throw new IOException("Battly authlib is not available for android");
        }

        File destination = resolveDestination(authlibEntry.optString("path", AUTHLIB_PATH));
        String sha1 = authlibEntry.optString("hash", "");
        long size = authlibEntry.optLong("size", -1);
        if (isValid(destination, sha1, size)) {
            return destination;
        }

        String url = authlibEntry.optString("url", "");
        if (url.isEmpty()) {
            throw new IOException("Battly authlib url is empty");
        }
        File temporary = new File(destination.getAbsolutePath() + DOWNLOAD_SUFFIX);
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Unable to clear stale Battly authlib download");
        }
        DownloadUtils.downloadFile(url, temporary);
        if (!isValid(temporary, sha1, size)) {
            if (!temporary.delete()) {
                Log.w(TAG, "Could not delete invalid authlib download: " + temporary.getAbsolutePath());
            }
            throw new IOException("Battly authlib verification failed");
        }
        replaceVerifiedDownload(temporary, destination);
        Log.i(TAG, "Battly authlib updated from API: " + sha1);
        return destination;
    }

    static JSONObject selectAndroidAuthlib(JSONArray files) {
        if (files == null) {
            return null;
        }
        for (int i = 0; i < files.length(); i++) {
            JSONObject entry = files.optJSONObject(i);
            if (entry == null || !AUTHLIB_PATH.equals(entry.optString("path"))) {
                continue;
            }
            if (supportsAndroid(entry.optJSONArray("compatibilities"))) {
                return entry;
            }
        }
        return null;
    }

    public static void addJvmArgumentsIfAvailable(java.util.List<String> javaArgList) {
        try {
            File authlib = ensureAuthlib();
            String authlibPath = authlib.getAbsolutePath();
            javaArgList.add("-Dbattly.api.url=" + AUTH_SERVER);
            javaArgList.add("-javaagent:" + authlibPath + "=" + AUTH_SERVER);
            javaArgList.add("-Xbootclasspath/a:" + authlibPath);
            Log.i(TAG, "Battly authlib attached: " + authlibPath);
            Logger.appendToLog("Info: Battly authlib attached: " + authlibPath);
        } catch (Exception e) {
            Log.w(TAG, "Could not attach Battly authlib", e);
            Logger.appendToLog("Warning: Battly authlib could not be attached: " + e.getMessage());
        }
    }

    private static boolean supportsAndroid(JSONArray compatibilities) {
        if (compatibilities == null) {
            return false;
        }
        for (int i = 0; i < compatibilities.length(); i++) {
            if ("android".equalsIgnoreCase(compatibilities.optString(i))) {
                return true;
            }
        }
        return false;
    }

    static void replaceVerifiedDownload(File temporary, File destination) throws IOException {
        File backup = new File(destination.getAbsolutePath() + BACKUP_SUFFIX);
        if (backup.exists() && !backup.delete()) {
            throw new IOException("Unable to clear stale Battly authlib backup");
        }

        boolean hadDestination = destination.isFile();
        if (hadDestination && !destination.renameTo(backup)) {
            throw new IOException("Unable to back up the current Battly authlib");
        }
        if (!temporary.renameTo(destination)) {
            if (hadDestination && !backup.renameTo(destination)) {
                Log.e(TAG, "Unable to restore Battly authlib backup: " + backup.getAbsolutePath());
            }
            throw new IOException("Unable to activate the downloaded Battly authlib");
        }
        if (backup.exists() && !backup.delete()) {
            Log.w(TAG, "Could not delete old Battly authlib backup: " + backup.getAbsolutePath());
        }
    }

    private static File resolveDestination(String path) throws IOException {
        File base = new File(Tools.DIR_GAME_HOME).getCanonicalFile();
        File destination = new File(base, path).getCanonicalFile();
        String basePath = base.getPath();
        String destinationPath = destination.getPath();
        if (!destinationPath.equals(basePath) && !destinationPath.startsWith(basePath + File.separator)) {
            throw new IOException("Battly authlib path escapes storage root: " + path);
        }
        return destination;
    }

    private static boolean isValid(File file, String sha1, long size) {
        if (!file.isFile()) {
            return false;
        }
        if (sha1 != null && !sha1.isEmpty()) {
            return Tools.compareSHA1(file, sha1);
        }
        if (size >= 0 && file.length() != size) {
            return false;
        }
        return true;
    }
}
