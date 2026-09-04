package net.kdt.pojavlaunch.utils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Non-destructive updates for Minecraft's line-based options.txt format. */
public final class MinecraftOptionsFile {
    private MinecraftOptionsFile() {
    }

    public static String merge(String current, Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) return current == null ? "" : current;
        String source = current == null ? "" : current;
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        boolean endsWithNewline = source.endsWith("\n");
        String[] lines = source.split("\r?\n", -1);
        StringBuilder result = new StringBuilder(source.length() + updates.size() * 24);
        Set<String> applied = new HashSet<>();

        int lineCount = endsWithNewline ? lines.length - 1 : lines.length;
        for (int i = 0; i < lineCount; i++) {
            String line = lines[i];
            int separator = line.indexOf(':');
            String key = separator < 0 ? null : line.substring(0, separator);
            if (key != null && updates.containsKey(key)) {
                result.append(key).append(':').append(updates.get(key));
                applied.add(key);
            } else {
                result.append(line);
            }
            result.append(newline);
        }

        if (!endsWithNewline && lineCount > 0 && result.length() >= newline.length()) {
            result.setLength(result.length() - newline.length());
        }
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (applied.contains(entry.getKey())) continue;
            if (result.length() > 0 && !endsInNewline(result)) result.append(newline);
            result.append(entry.getKey()).append(':').append(entry.getValue()).append(newline);
        }
        return result.toString();
    }

    private static boolean endsInNewline(StringBuilder value) {
        return value.length() > 0 && value.charAt(value.length() - 1) == '\n';
    }
}
