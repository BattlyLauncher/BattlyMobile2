package net.kdt.pojavlaunch.modloaders;

import android.content.Context;
import android.content.Intent;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.services.InstallerService;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

public class HeadlessInstallerRunner {
    private static final long RESULT_TIMEOUT_MS = 10 * 60 * 1000L;

    private HeadlessInstallerRunner() {
    }

    public static void run(Context context, String runtimeName, File workDir, ArrayList<String> commands) throws IOException {
        File resultDir = new File(Tools.DIR_CACHE, "installer_results");
        FileUtils.ensureDirectory(resultDir);

        File resultFile = new File(resultDir, UUID.randomUUID() + ".txt");
        if (resultFile.exists() && !resultFile.delete()) {
            throw new IOException("Failed to reset installer result file");
        }

        Intent intent = new Intent(context, InstallerService.class)
                .putExtra(InstallerService.EXTRA_RUNTIME_NAME, runtimeName)
                .putExtra(InstallerService.EXTRA_WORKDIR, workDir.getAbsolutePath())
                .putExtra(InstallerService.EXTRA_RESULT_FILE, resultFile.getAbsolutePath())
                .putStringArrayListExtra(InstallerService.EXTRA_COMMANDS, commands);
        context.startService(intent);

        long startTime = System.currentTimeMillis();
        while (!resultFile.exists()) {
            if (System.currentTimeMillis() - startTime > RESULT_TIMEOUT_MS) {
                throw new IOException("Timed out while waiting for installer result");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
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
            //noinspection ResultOfMethodCallIgnored
            resultFile.delete();
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
}
