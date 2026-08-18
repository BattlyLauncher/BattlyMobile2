package net.kdt.pojavlaunch.customcontrols;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks the remote Minecraft endpoint reported by the running game process. */
public final class MinecraftServerSessionTracker {
    private static final int DEFAULT_PORT = 25565;
    private static final int MAX_CARRY = 512;
    private static final Pattern BRACKETED_ENDPOINT = Pattern.compile(
            "(?i)connecting to\\s+/?\\[([^]]+)](?::|,\\s*)(\\d{1,5})");
    private static final Pattern HOST_AND_PORT = Pattern.compile(
            "(?i)connecting to\\s+/?([^,\\s:]+)(?::|,\\s*)(\\d{1,5})");
    private static final Pattern HOST_ONLY = Pattern.compile(
            "(?i)connecting to\\s+/?([^,\\s:]+)(?:\\s|$)");

    private static volatile Endpoint endpoint;
    private static String carry = "";

    private MinecraftServerSessionTracker() {
    }

    public static synchronized void reset() {
        endpoint = null;
        carry = "";
    }

    public static synchronized void ingest(String output) {
        if (output == null || output.isEmpty()) return;
        String text = carry + output;
        String[] lines = text.split("\\r?\\n", -1);
        int completeLines = lines.length - 1;
        for (int i = 0; i < completeLines; i++) inspectLine(lines[i]);
        carry = lines[lines.length - 1];
        if (carry.length() > MAX_CARRY) {
            inspectLine(carry);
            carry = carry.substring(carry.length() - MAX_CARRY);
        }
    }

    static synchronized void inspectLine(String line) {
        if (line == null) return;
        String lower = line.toLowerCase(Locale.ROOT);
        if (isLocalWorldLine(lower) || isDisconnectLine(lower)) {
            endpoint = null;
            return;
        }
        if (!lower.contains("connecting to")) return;

        Matcher matcher = BRACKETED_ENDPOINT.matcher(line);
        if (!matcher.find()) matcher = HOST_AND_PORT.matcher(line);
        if (matcher.find(0)) {
            setEndpoint(matcher.group(1), parsePort(matcher.group(2)));
            return;
        }
        matcher = HOST_ONLY.matcher(line);
        if (matcher.find()) setEndpoint(matcher.group(1), DEFAULT_PORT);
    }

    public static Endpoint getEndpoint() {
        return endpoint;
    }

    private static boolean isLocalWorldLine(String lower) {
        return lower.contains("starting integrated minecraft server")
                || lower.contains("starting integrated server")
                || lower.contains("integrated server is now running");
    }

    private static boolean isDisconnectLine(String lower) {
        return lower.contains("disconnecting from server")
                || lower.contains("disconnected from server")
                || lower.contains("connection lost")
                || lower.contains("lost connection")
                || lower.contains("stopping client")
                || lower.contains("stopping!");
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port > 0 && port <= 65535 ? port : DEFAULT_PORT;
        } catch (NumberFormatException ignored) {
            return DEFAULT_PORT;
        }
    }

    private static void setEndpoint(String host, int port) {
        if (host == null) return;
        String cleanHost = host.trim();
        if (cleanHost.startsWith("/")) cleanHost = cleanHost.substring(1);
        if (!cleanHost.isEmpty()) endpoint = new Endpoint(cleanHost, port);
    }

    public static final class Endpoint {
        public final String host;
        public final int port;

        Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
