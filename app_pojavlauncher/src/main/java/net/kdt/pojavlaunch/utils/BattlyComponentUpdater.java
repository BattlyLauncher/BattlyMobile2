package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.BattlyAuthlibManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Updates launcher-managed data components from Battly's signed file manifest. */
public final class BattlyComponentUpdater {
    public static final String MANIFEST_URL = "https://api.battlylauncher.com/battlylauncher/files";
    private static final String AUTHLIB_PATH = "authlib-injector.jar";
    private static final String PREFS = "battly_component_updates";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final long CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;

    private BattlyComponentUpdater() {
    }

    /** Runs at most twice a day and never blocks launcher startup. */
    public static void scheduleBackgroundCheck(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!shouldCheck(now, preferences.getLong(KEY_LAST_CHECK, 0L))) return;
        preferences.edit().putLong(KEY_LAST_CHECK, now).apply();
        net.kdt.pojavlaunch.PojavApplication.sExecutorService.execute(() -> {
            try {
                Result result = updateAll(appContext, (current, total, component) -> { });
                Log.i("BattlyComponents", "Background check complete: updated="
                        + result.updated.size() + ", current=" + result.current.size());
            } catch (Exception exception) {
                Log.w("BattlyComponents", "Background component check failed", exception);
            }
        });
    }

    static boolean shouldCheck(long now, long lastCheck) {
        return lastCheck <= 0L || now < lastCheck || now - lastCheck >= CHECK_INTERVAL_MS;
    }

    public interface ProgressListener {
        void onProgress(int current, int total, String component);
    }

    @NonNull
    public static Result updateAll(@NonNull Context context, @NonNull ProgressListener listener)
            throws IOException {
        JSONArray manifest;
        try {
            manifest = new JSONArray(DownloadUtils.downloadString(MANIFEST_URL));
        } catch (Exception exception) {
            throw new IOException("Unable to load Battly component manifest", exception);
        }

        List<JSONObject> entries = selectAndroidEntries(manifest);
        ArrayList<String> updated = new ArrayList<>();
        ArrayList<String> current = new ArrayList<>();
        ArrayList<String> skipped = new ArrayList<>();
        int position = 0;
        for (JSONObject entry : entries) {
            position++;
            String path = entry.optString("path", "");
            listener.onProgress(position, entries.size(), path);
            if (AUTHLIB_PATH.equals(path)) {
                File before = new File(Tools.DIR_GAME_HOME, AUTHLIB_PATH);
                String expectedHash = entry.optString("hash", "");
                long expectedSize = entry.optLong("size", -1L);
                boolean wasCurrent = isValid(before, expectedHash, expectedSize);
                BattlyAuthlibManager.ensureAuthlib();
                if (wasCurrent) current.add(path); else updated.add(path);
                continue;
            }
            File destination = resolveDestination(path);
            String sha1 = entry.optString("hash", "");
            long size = entry.optLong("size", -1L);
            if (!isAllowedDataComponent(path)) {
                skipped.add(path);
                continue;
            }
            if (isValid(destination, sha1, size)) {
                current.add(path);
                continue;
            }
            String url = entry.optString("url", "");
            if (url.isEmpty()) {
                skipped.add(path);
                continue;
            }
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Unable to create component directory for " + path);
            }
            File temporary = new File(destination.getAbsolutePath() + ".download");
            if (temporary.exists() && !temporary.delete()) {
                throw new IOException("Unable to clear temporary component " + path);
            }
            DownloadUtils.downloadFile(url, temporary);
            if (!isValid(temporary, sha1, size)) {
                temporary.delete();
                throw new IOException("Component verification failed: " + path);
            }
            atomicReplace(temporary, destination);
            updated.add(path);
        }
        return new Result(Collections.unmodifiableList(updated),
                Collections.unmodifiableList(current), Collections.unmodifiableList(skipped));
    }

    static List<JSONObject> selectAndroidEntries(JSONArray manifest) {
        ArrayList<JSONObject> result = new ArrayList<>();
        if (manifest == null) return result;
        for (int i = 0; i < manifest.length(); i++) {
            JSONObject entry = manifest.optJSONObject(i);
            if (entry == null || !supportsAndroid(entry.optJSONArray("compatibilities"))) continue;
            String path = entry.optString("path", "");
            if (AUTHLIB_PATH.equals(path) || isAllowedDataComponent(path)) result.add(entry);
        }
        return result;
    }

    static boolean isAllowedDataComponent(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("..")) return false;
        return path.startsWith("lwjgl3/")
                || path.startsWith("renderer-plugins/")
                || path.startsWith("components/");
    }

    private static boolean supportsAndroid(JSONArray compatibilities) {
        if (compatibilities == null) return false;
        for (int i = 0; i < compatibilities.length(); i++) {
            if ("android".equalsIgnoreCase(compatibilities.optString(i))) return true;
        }
        return false;
    }

    private static File resolveDestination(String path) throws IOException {
        File base = new File(Tools.DIR_GAME_HOME).getCanonicalFile();
        File destination = new File(base, path).getCanonicalFile();
        if (!destination.getPath().startsWith(base.getPath() + File.separator)) {
            throw new IOException("Unsafe component path: " + path);
        }
        return destination;
    }

    private static boolean isValid(File file, String sha1, long size) {
        if (!file.isFile()) return false;
        if (sha1 != null && !sha1.isEmpty()) return Tools.compareSHA1(file, sha1);
        return size >= 0 && file.length() == size;
    }

    private static void atomicReplace(File temporary, File destination) throws IOException {
        File backup = new File(destination.getAbsolutePath() + ".backup");
        if (backup.exists()) backup.delete();
        boolean existed = destination.isFile();
        if (existed && !destination.renameTo(backup)) throw new IOException("Unable to back up " + destination.getName());
        if (!temporary.renameTo(destination)) {
            if (existed) backup.renameTo(destination);
            throw new IOException("Unable to activate " + destination.getName());
        }
        if (backup.exists()) backup.delete();
    }

    public static final class Result {
        public final List<String> updated;
        public final List<String> current;
        public final List<String> skipped;

        Result(List<String> updated, List<String> current, List<String> skipped) {
            this.updated = updated;
            this.current = current;
            this.skipped = skipped;
        }
    }
}
