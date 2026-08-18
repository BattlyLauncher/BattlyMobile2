package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DownloadUtilsTest {
    @Test
    public void retriesTransientNetworkFailuresThroughWrappedCauses() {
        assertTrue(DownloadRetryPolicy.isRetryable(
                new IOException("wrapper", new EOFException("unexpected end of stream"))));
        assertTrue(DownloadRetryPolicy.isRetryable(new SocketTimeoutException("timeout")));
        assertTrue(DownloadRetryPolicy.isRetryable(
                new IOException("Connection reset by peer")));
    }

    @Test
    public void doesNotRetryPermanentClientErrors() {
        assertFalse(DownloadRetryPolicy.isRetryable(new FileNotFoundException("missing")));
        assertFalse(DownloadRetryPolicy.isRetryable(
                new IOException("Server returned HTTP 404")));
    }

    @Test
    public void validatesCompleteArchivesAndRejectsCorruption() throws Exception {
        File valid = File.createTempFile("battly-download", ".jar");
        File invalid = File.createTempFile("battly-download", ".jar");
        try {
            try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(valid))) {
                output.putNextEntry(new ZipEntry("example.txt"));
                output.write("Battly".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
            try (FileOutputStream output = new FileOutputStream(invalid)) {
                output.write("not a zip".getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(DownloadUtils.isValidZipArchive(valid));
            assertFalse(DownloadUtils.isValidZipArchive(invalid));
        } finally {
            valid.delete();
            invalid.delete();
        }
    }
}
