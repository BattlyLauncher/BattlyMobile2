package net.kdt.pojavlaunch.modloaders;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstallerOutputMonitor {
    private static final Pattern CLASS_VERSION_PATTERN = Pattern.compile(
            "class file version\\s+(\\d+)(?:\\.0)?", Pattern.CASE_INSENSITIVE);

    enum State {
        RUNNING,
        SUCCESS,
        UNSUPPORTED_RUNTIME
    }

    private InstallerOutputMonitor() {
    }

    static State classify(String output) {
        if (output == null || output.isEmpty()) return State.RUNNING;
        String lower = output.toLowerCase(Locale.ROOT);
        if (lower.contains("unsupportedclassversionerror")
                || lower.contains("compiled by a more recent version of the java runtime")) {
            return State.UNSUPPORTED_RUNTIME;
        }
        if (lower.contains("successfully installed client into launcher")) {
            return State.SUCCESS;
        }
        return State.RUNNING;
    }

    static int requiredJavaVersion(String output) {
        if (output == null) return -1;
        Matcher matcher = CLASS_VERSION_PATTERN.matcher(output);
        if (!matcher.find()) return -1;
        try {
            int classVersion = Integer.parseInt(matcher.group(1));
            return classVersion < 46 ? 2 : classVersion - 44;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
