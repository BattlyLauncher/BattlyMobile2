package net.kdt.pojavlaunch.modloaders;

import android.content.Context;
import android.content.Intent;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.services.InstallerService;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

public class HeadlessInstallerRunner {
    private static final long RESULT_TIMEOUT_MS = 10 * 60 * 1000L;

    private HeadlessInstallerRunner() {
    }

    public static void run(Context context, String runtimeName, File workDir, ArrayList<String> commands) throws IOException {
        run(context, runtimeName, workDir, commands, null);
    }

    public static void run(Context context, String runtimeName, File workDir,
                           ArrayList<String> commands, File expectedOutput) throws IOException {
        File resultDir = new File(Tools.DIR_CACHE, "installer_results");
        FileUtils.ensureDirectory(resultDir);

        File resultFile = new File(resultDir, UUID.randomUUID() + ".txt");
        File statusFile = new File(resultDir, UUID.randomUUID() + ".status");
        if (resultFile.exists() && !resultFile.delete()) {
            throw new IOException("Failed to reset installer result file");
        }

        Intent intent = new Intent(context, InstallerService.class)
                .putExtra(InstallerService.EXTRA_RUNTIME_NAME, runtimeName)
                .putExtra(InstallerService.EXTRA_WORKDIR, workDir.getAbsolutePath())
                .putExtra(InstallerService.EXTRA_RESULT_FILE, resultFile.getAbsolutePath())
                .putExtra(InstallerService.EXTRA_STATUS_FILE, statusFile.getAbsolutePath())
                .putStringArrayListExtra(InstallerService.EXTRA_COMMANDS, commands);
        context.startService(intent);

        long startTime = System.currentTimeMillis();
        long previousOutputSize = -1;
        int stableOutputChecks = 0;
        while (!resultFile.exists()) {
            String installerOutput = readInstallerOutput(statusFile);
            InstallerOutputMonitor.State state = InstallerOutputMonitor.classify(installerOutput);
            if (state == InstallerOutputMonitor.State.UNSUPPORTED_RUNTIME) {
                context.stopService(intent);
                cleanup(resultFile, statusFile);
                int requiredJava = InstallerOutputMonitor.requiredJavaVersion(installerOutput);
                throw new IOException(requiredJava > 0
                        ? "Installer requires Java " + requiredJava + " or newer"
                        : "Installer requires a newer Java runtime");
            }
            if (state == InstallerOutputMonitor.State.SUCCESS) {
                context.stopService(intent);
                cleanup(resultFile, statusFile);
                return;
            }
            if (expectedOutput != null && expectedOutput.isFile() && expectedOutput.length() > 0) {
                long outputSize = expectedOutput.length();
                stableOutputChecks = outputSize == previousOutputSize ? stableOutputChecks + 1 : 0;
                previousOutputSize = outputSize;
                if (stableOutputChecks >= 2) {
                    context.stopService(intent);
                    cleanup(resultFile, statusFile);
                    return;
                }
            }
            if (System.currentTimeMillis() - startTime > RESULT_TIMEOUT_MS) {
                context.stopService(intent);
                cleanup(resultFile, statusFile);
                throw new IOException("Timed out while waiting for installer result");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                context.stopService(intent);
                cleanup(resultFile, statusFile);
                Thread.currentThread().interrupt();
                throw new IOException("Installer wait interrupted", e);
            }
        }

        int exitCode;
        String errorMessage = null;
        String logFile = null;
        try {
            String resultContent = Tools.read(resultFile).trim();
            if (resultContent.contains("=")) {
                Properties properties = new Properties();
                properties.load(new StringReader(resultContent));
                exitCode = Integer.parseInt(properties.getProperty("exitCode", "-1").trim());
                errorMessage = properties.getProperty("errorMessage");
                logFile = properties.getProperty("logFile");
            } else {
                exitCode = Integer.parseInt(resultContent);
            }
        } catch (Exception e) {
            throw new IOException("Installer returned an invalid exit result", e);
        } finally {
            cleanup(resultFile, statusFile);
        }

        if (exitCode != 0) {
            StringBuilder message = new StringBuilder("Installer exited with code ").append(exitCode);
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                message.append(": ").append(errorMessage.trim());
            }
            if (logFile != null && !logFile.trim().isEmpty()) {
                message.append(". Log: ").append(logFile.trim());
            }
            throw new IOException(message.toString());
        }
    }

    private static String readInstallerOutput(File statusFile) {
        if (!statusFile.isFile()) return "";
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(Tools.read(statusFile)));
            String logPath = properties.getProperty("logFile");
            if (logPath == null) return "";
            File logFile = new File(logPath);
            if (!logFile.isFile()) return "";
            try (RandomAccessFile input = new RandomAccessFile(logFile, "r")) {
                long start = Math.max(0, input.length() - 64 * 1024L);
                input.seek(start);
                byte[] bytes = new byte[(int) (input.length() - start)];
                input.readFully(bytes);
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void cleanup(File resultFile, File statusFile) {
        //noinspection ResultOfMethodCallIgnored
        resultFile.delete();
        //noinspection ResultOfMethodCallIgnored
        statusFile.delete();
    }
}
