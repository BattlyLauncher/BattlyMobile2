package net.kdt.pojavlaunch.utils;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;

final class DownloadRetryPolicy {
    private DownloadRetryPolicy() {
    }

    static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof FileNotFoundException
                    || current instanceof ProtocolException) {
                return false;
            }
            if (current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof EOFException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("unexpected end of stream")
                        || normalized.contains("connection reset")
                        || normalized.contains("connection closed")
                        || normalized.contains("timed out")) {
                    return true;
                }
                if (normalized.contains("http 408") || normalized.contains("http 429")
                        || normalized.matches(".*http 5\\d\\d.*")) {
                    return true;
                }
                if (normalized.matches(".*http 4\\d\\d.*")) {
                    return false;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
