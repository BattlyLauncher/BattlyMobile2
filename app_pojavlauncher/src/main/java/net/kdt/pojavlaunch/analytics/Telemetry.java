package net.kdt.pojavlaunch.analytics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.messaging.FirebaseMessaging;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;

public final class Telemetry {
    private static final String TAG = "BattlyTelemetry";
    public static final String PREFS_NAME = "battly_telemetry";
    public static final String PREF_FCM_TOKEN = "fcm_token";
    private static final int MAX_PARAM_LENGTH = 100;
    private static volatile FirebaseAnalytics sAnalytics;
    private static volatile FirebaseCrashlytics sCrashlytics;

    private Telemetry() {
    }

    public static void initialize(Context context) {
        try {
            Context appContext = context.getApplicationContext();
            Tools.buildNotificationChannel(appContext);
            sAnalytics = FirebaseAnalytics.getInstance(appContext);
            sCrashlytics = FirebaseCrashlytics.getInstance();
            sCrashlytics.setCustomKey("launcher_process", context.getPackageName());
            sCrashlytics.setCustomKey("notifications_enabled",
                    NotificationManagerCompat.from(appContext).areNotificationsEnabled());
            initializeMessaging(appContext);
        } catch (Throwable throwable) {
            Log.w(TAG, "Firebase telemetry is unavailable", throwable);
        }
    }

    public static void logProfileSelected(String profileKey) {
        Bundle bundle = new Bundle();
        bundle.putString("profile_key", sanitize(profileKey));
        logEvent("profile_selected", bundle);
    }

    public static void logLaunchRequested(String profileKey, String versionId) {
        Bundle bundle = new Bundle();
        bundle.putString("profile_key", sanitize(profileKey));
        bundle.putString("version_id", sanitize(versionId));
        logEvent("launch_requested", bundle);
    }

    public static void logVersionDownload(String versionId, boolean success, Throwable throwable) {
        Bundle bundle = new Bundle();
        bundle.putString("version_id", sanitize(versionId));
        bundle.putString("result", success ? "success" : "failure");
        if (throwable != null) {
            bundle.putString("error_type", sanitize(throwable.getClass().getSimpleName()));
        }
        logEvent("version_download", bundle);
        if (!success && throwable != null) {
            recordNonFatal("version_download_failed", throwable);
        }
    }

    public static void logContentInstall(int contentType, String title, boolean success, Throwable throwable) {
        Bundle bundle = new Bundle();
        bundle.putString("content_type", contentTypeName(contentType));
        bundle.putString("content_title", sanitize(title));
        bundle.putString("result", success ? "success" : "failure");
        if (throwable != null) {
            bundle.putString("error_type", sanitize(throwable.getClass().getSimpleName()));
        }
        logEvent("content_install", bundle);
        if (!success && throwable != null) {
            recordNonFatal("content_install_failed", throwable);
        }
    }

    public static void logGameLaunch(String versionId, int javaVersion, String renderer) {
        Bundle bundle = new Bundle();
        bundle.putString("version_id", sanitize(versionId));
        bundle.putLong("java_version", javaVersion);
        bundle.putString("renderer", sanitize(renderer));
        logEvent("game_launch", bundle);
        FirebaseCrashlytics crashlytics = sCrashlytics;
        if (crashlytics != null) {
            crashlytics.setCustomKey("minecraft_version", safeValue(versionId));
            crashlytics.setCustomKey("minecraft_java_version", javaVersion);
            crashlytics.setCustomKey("renderer", safeValue(renderer));
        }
    }

    public static void logGameExit(int exitCode) {
        String reason = inferGameExitReason(exitCode);
        Bundle bundle = new Bundle();
        bundle.putLong("exit_code", exitCode);
        bundle.putString("reason", reason);
        logEvent("game_exit", bundle);
        if (exitCode != 0) {
            FirebaseCrashlytics crashlytics = sCrashlytics;
            if (crashlytics != null) {
                crashlytics.setCustomKey("game_exit_code", exitCode);
                crashlytics.setCustomKey("game_exit_reason", reason);
                crashlytics.recordException(new IllegalStateException(
                        "Minecraft exited with code " + exitCode + " (" + reason + ")"));
            }
        }
    }

    public static void recordLauncherCrash(String threadName, Throwable throwable) {
        FirebaseCrashlytics crashlytics = sCrashlytics;
        if (crashlytics == null || throwable == null) {
            return;
        }
        crashlytics.setCustomKey("crash_thread", safeValue(threadName));
        crashlytics.setCustomKey("crash_reason", throwable.getClass().getSimpleName());
        crashlytics.recordException(throwable);
    }

    public static void logMessagingTokenRefresh() {
        logEvent("fcm_token_refresh", new Bundle());
    }

    public static void saveMessagingToken(Context context, String token) {
        if (!Tools.isValidString(token)) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_FCM_TOKEN, token)
                .apply();
        FirebaseCrashlytics crashlytics = sCrashlytics;
        if (crashlytics != null) {
            crashlytics.setCustomKey("fcm_token_present", true);
        }
        BattlyWorldsInvites.registerDeviceToken(context);
        Log.i(TAG, "FCM token available: " + (BuildConfig.DEBUG ? token : token.substring(0, Math.min(12, token.length())) + "..."));
    }

    public static void logMessageReceived(String source) {
        Bundle bundle = new Bundle();
        bundle.putString("source", sanitize(source));
        logEvent("fcm_message_received", bundle);
    }

    private static void recordNonFatal(String reason, Throwable throwable) {
        FirebaseCrashlytics crashlytics = sCrashlytics;
        if (crashlytics == null) {
            return;
        }
        crashlytics.setCustomKey("nonfatal_reason", reason);
        crashlytics.recordException(throwable);
    }

    private static void initializeMessaging(Context context) {
        FirebaseMessaging messaging = FirebaseMessaging.getInstance();
        messaging.setAutoInitEnabled(true);
        messaging.getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Throwable throwable = task.getException();
                Log.w(TAG, "Unable to fetch FCM token", throwable);
                if (throwable != null) {
                    recordNonFatal("fcm_token_fetch_failed", throwable);
                }
                return;
            }
            saveMessagingToken(context, task.getResult());
        });
    }

    private static void logEvent(String name, Bundle bundle) {
        FirebaseAnalytics analytics = sAnalytics;
        if (analytics == null) {
            return;
        }
        Log.d(TAG, "Logging event: " + name);
        analytics.logEvent(name, bundle);
    }

    private static String contentTypeName(int contentType) {
        switch (contentType) {
            case SearchFilters.TYPE_MODPACK:
                return "modpack";
            case SearchFilters.TYPE_RESOURCEPACK:
                return "resourcepack";
            case SearchFilters.TYPE_SHADER:
                return "shader";
            case SearchFilters.TYPE_DATAPACK:
                return "datapack";
            case SearchFilters.TYPE_MOD:
            default:
                return "mod";
        }
    }

    private static String inferGameExitReason(int exitCode) {
        if (exitCode == 0) {
            return "clean_exit";
        }
        String tail = readLatestLogTail().toLowerCase(Locale.ROOT);
        if (tail.contains("unsupportedclassversionerror") || tail.contains("unsupported class file major version")) {
            return "unsupported_class_version";
        }
        if (tail.contains("outofmemoryerror")) {
            return "out_of_memory";
        }
        if (tail.contains("resolutionexception")) {
            return "module_resolution";
        }
        if (tail.contains("unsatisfiedlinkerror")) {
            return "missing_native_library";
        }
        if (tail.contains("modloadingexception") || tail.contains("mod resolution")) {
            return "mod_loading";
        }
        if (tail.contains("exception in thread") || tail.contains("caused by:")) {
            return "java_exception";
        }
        return "exit_code_" + exitCode;
    }

    private static String readLatestLogTail() {
        File latestLog = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        if (!latestLog.isFile() || !latestLog.canRead()) {
            return "";
        }
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(latestLog, "r")) {
            long length = randomAccessFile.length();
            long offset = Math.max(0, length - 16384);
            randomAccessFile.seek(offset);
            byte[] buffer = new byte[(int) (length - offset)];
            randomAccessFile.readFully(buffer);
            return new String(buffer);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String sanitize(String value) {
        return safeValue(value);
    }

    private static String safeValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_PARAM_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_PARAM_LENGTH);
    }
}
