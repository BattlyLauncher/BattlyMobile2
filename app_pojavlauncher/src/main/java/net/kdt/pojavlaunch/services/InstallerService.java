package net.kdt.pojavlaunch.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

public class InstallerService extends Service {
    public static final String EXTRA_RUNTIME_NAME = "runtimeName";
    public static final String EXTRA_WORKDIR = "workDir";
    public static final String EXTRA_RESULT_FILE = "resultFile";
    public static final String EXTRA_STATUS_FILE = "statusFile";
    public static final String EXTRA_COMMANDS = "commands";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        ArrayList<String> commands = intent.getStringArrayListExtra(EXTRA_COMMANDS);
        String runtimeName = intent.getStringExtra(EXTRA_RUNTIME_NAME);
        String workDirPath = intent.getStringExtra(EXTRA_WORKDIR);
        String resultFilePath = intent.getStringExtra(EXTRA_RESULT_FILE);
        String statusFilePath = intent.getStringExtra(EXTRA_STATUS_FILE);

        if (commands == null || runtimeName == null || workDirPath == null
                || resultFilePath == null || statusFilePath == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        new Thread(() -> {
            int exitCode;
            String errorMessage = null;
            String failureStage = null;
            File installerLog = new File(Tools.DIR_CACHE, "installer_logs/" + UUID.randomUUID() + ".log");
            try {
                FileUtils.ensureParentDirectory(installerLog);
                writeProperties(new File(statusFilePath), -1, "running", null,
                        installerLog.getAbsolutePath());
                appendLog(installerLog, "Installer service started");
                try {
                    Logger.begin(installerLog.getAbsolutePath());
                    appendLog(installerLog, "Native logger initialized");
                } catch (Throwable loggerError) {
                    appendLog(installerLog, "Native logger init failed: " + loggerError);
                }
                failureStage = "runtime";
                Runtime runtime = MultiRTUtils.forceReread(runtimeName);
                appendLog(installerLog, "Using runtime " + runtime.name + " (" + runtime.versionString + ")");
                failureStage = "launch";
                exitCode = JREUtils.launchApiInstaller(getApplicationContext(), runtime, new File(workDirPath), new ArrayList<>(commands));
                appendLog(installerLog, "Installer exited with code " + exitCode);
            } catch (Throwable throwable) {
                Log.e("InstallerService", "Failed to execute installer", throwable);
                exitCode = -1;
                errorMessage = (failureStage == null ? "" : "[" + failureStage + "] ") + throwable;
                appendLog(installerLog, "Installer failure: " + Log.getStackTraceString(throwable));
            }

            try {
                writeProperties(new File(resultFilePath), exitCode, "finished", errorMessage,
                        installerLog.getAbsolutePath());
            } catch (IOException ignored) {
            }

            stopSelf(startId);
        }, "InstallerService").start();

        return START_NOT_STICKY;
    }

    private static void writeProperties(File file, int exitCode, String state,
                                        String errorMessage, String logFile) throws IOException {
        FileUtils.ensureParentDirectory(file);
        Properties properties = new Properties();
        properties.setProperty("exitCode", Integer.toString(exitCode));
        properties.setProperty("state", state);
        properties.setProperty("logFile", logFile);
        if (errorMessage != null) properties.setProperty("errorMessage", errorMessage);
        StringBuilder content = new StringBuilder();
        for (String name : properties.stringPropertyNames()) {
            content.append(name).append('=').append(properties.getProperty(name)).append('\n');
        }
        File temporary = new File(file.getAbsolutePath() + ".tmp");
        Tools.write(temporary.getAbsolutePath(), content.toString());
        if (!temporary.renameTo(file)) {
            Tools.write(file.getAbsolutePath(), content.toString());
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
        }
    }

    private static void appendLog(File logFile, String message) {
        try {
            FileUtils.ensureParentDirectory(logFile);
            try (FileOutputStream outputStream = new FileOutputStream(logFile, true)) {
                outputStream.write((message + '\n').getBytes());
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        android.os.Process.killProcess(android.os.Process.myPid());
        super.onDestroy();
    }
}
