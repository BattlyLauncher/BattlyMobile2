package net.kdt.pojavlaunch.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public final class LogTailReader {
    private LogTailReader() {
    }

    public static String readUtf8Tail(File file, int maxBytes) throws IOException {
        if (file == null || !file.isFile() || maxBytes <= 0) return "";
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            int bytesToRead = (int) Math.min(length, maxBytes);
            byte[] data = new byte[bytesToRead];
            input.seek(length - bytesToRead);
            input.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
