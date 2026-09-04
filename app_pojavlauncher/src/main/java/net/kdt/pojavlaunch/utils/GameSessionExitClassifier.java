package net.kdt.pojavlaunch.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GameSessionExitClassifier {
    private static final Pattern EXIT_CODE = Pattern.compile("java\\s+exit\\s+code:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);

    private GameSessionExitClassifier() {
    }

    public static boolean endedUnexpectedly(int reportedExitCode, String details, String log) {
        if (details != null && !details.trim().isEmpty()) return true;
        if (reportedExitCode == 0) return false;
        if (log == null || log.trim().isEmpty()) return true;
        return endedUnexpectedly(log);
    }

    public static boolean endedUnexpectedly(String log) {
        if (log == null || log.trim().isEmpty()) return false;
        String lower = log.toLowerCase(Locale.ROOT);
        boolean fatal = lower.contains("game crashed!")
                || lower.contains("---- minecraft crash report ----")
                || lower.contains("exception in thread \"main\"")
                || lower.contains("unable to launch")
                || lower.contains("unsatisfiedlinkerror")
                || lower.contains("fatal exception")
                || lower.contains("error during pre-loading phase")
                || lower.contains("modloadingexception")
                || lower.contains("needs language provider javafml");
        if (fatal) return true;

        if (lower.contains("stopping!") || lower.contains("shutting down") || lower.contains("fastquit. exiting")) {
            return false;
        }
        Integer exitCode = lastExitCode(log);
        return exitCode != null && exitCode != 0;
    }

    static Integer lastExitCode(String log) {
        Matcher matcher = EXIT_CODE.matcher(log);
        Integer result = null;
        while (matcher.find()) {
            try {
                result = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
