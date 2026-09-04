package net.kdt.pojavlaunch.utils;

import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.kdt.pojavlaunch.*;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.*;

@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    // Keep networking usable from JVM tests and early startup before Tools is initialized.
    public static final String USER_AGENT = "Battly Mobile";
    private static final int TIME_OUT = 30000;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 4;
    private static final long RETRY_DELAY_MS = 350L;
    private static final int DOWNLOAD_BUFFER_SIZE = 64 * 1024;
    private static final OkHttpClient HTTP_CLIENT;

    static {
        // Prefer IPv4 when both routes exist. IPv6-only networks can still use IPv6.
        System.setProperty("java.net.preferIPv6Addresses", "false");
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(AdaptiveDownloadPolicy.MAX_WORKERS);
        dispatcher.setMaxRequestsPerHost(AdaptiveDownloadPolicy.MAX_WORKERS);
        HTTP_CLIENT = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(new ConnectionPool(AdaptiveDownloadPolicy.MAX_WORKERS,
                        5, TimeUnit.MINUTES))
                .connectTimeout(TIME_OUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIME_OUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIME_OUT, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static void download(String url, OutputStream os) throws IOException {
        download(new URL(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
        if (isHttp(url)) {
            Request request = requestBuilder(url.toString()).build();
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                ResponseBody body = requireSuccessfulBody(response, url.toString());
                try (InputStream input = body.byteStream()) {
                    IOUtils.copyLarge(input, os, new byte[DOWNLOAD_BUFFER_SIZE]);
                }
            }
            return;
        }
        InputStream is = null;
        URLConnection connection = null;
        try {
            connection = openConnection(url);
            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                if (httpConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Server returned HTTP " + httpConnection.getResponseCode()
                            + ": " + httpConnection.getResponseMessage());
                }
            }
            is = connection.getInputStream();
            IOUtils.copy(is, os);
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
        }
    }

    public static String downloadString(String url) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                download(url, bos);
                return new String(bos.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                lastError = exception;
                if (!DownloadRetryPolicy.isRetryable(exception) || attempt == MAX_DOWNLOAD_ATTEMPTS) {
                    throw exception;
                }
                waitBeforeRetry(attempt, url, exception);
            }
        }
        throw lastError == null ? new IOException("Unable to download from " + url) : lastError;
    }

    public static void downloadFile(String url, File out) throws IOException {
        downloadFileMonitored(url, out, null, (current, total) -> { });
    }

    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                downloadFileMonitoredAttempt(urlInput, outputFile, buffer, monitor, true);
                return;
            } catch (IOException exception) {
                lastError = exception;
                if (!DownloadRetryPolicy.isRetryable(exception) || attempt == MAX_DOWNLOAD_ATTEMPTS) {
                    throw exception;
                }
                waitBeforeRetry(attempt, urlInput, exception);
            }
        }
        throw lastError == null ? new IOException("Unable to download from " + urlInput) : lastError;
    }

    private static void downloadFileMonitoredAttempt(String urlInput, File outputFile,
                                                     @Nullable byte[] buffer,
                                                     Tools.DownloaderFeedback monitor,
                                                     boolean allowReset) throws IOException {
        URL url = new URL(urlInput);
        if (!isHttp(url)) {
            downloadFileMonitoredUrlConnection(url, outputFile, buffer, monitor, allowReset);
            return;
        }
        FileUtils.ensureParentDirectory(outputFile);
        long existing = outputFile.isFile() ? outputFile.length() : 0L;
        Request.Builder requestBuilder = requestBuilder(urlInput);
        if (existing > 0) requestBuilder.header("Range", "bytes=" + existing + "-");
        try (Response response = HTTP_CLIENT.newCall(requestBuilder.build()).execute()) {
            int code = response.code();
            if (code == 416 && allowReset) {
                if (!outputFile.delete()) throw new IOException("Unable to reset partial download");
                downloadFileMonitoredAttempt(urlInput, outputFile, buffer, monitor, false);
                return;
            }
            ResponseBody body = requireSuccessfulBody(response, urlInput);
            boolean append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0;
            long overall = append ? existing : 0L;
            long responseLength = body.contentLength();
            long length = responseLength < 0 ? -1L : overall + responseLength;
            if (buffer == null) buffer = new byte[DOWNLOAD_BUFFER_SIZE];
            try (InputStream readStr = body.byteStream();
                 FileOutputStream fos = new FileOutputStream(outputFile, append)) {
                copyMonitored(readStr, fos, buffer, monitor, overall, length);
            }
        } catch (IOException e) {
            throw new IOException("Unable to download from " + urlInput, e);
        }
    }

    private static void downloadFileMonitoredUrlConnection(URL url, File outputFile,
                                                            @Nullable byte[] buffer,
                                                            Tools.DownloaderFeedback monitor,
                                                            boolean allowReset) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);
        long existing = outputFile.isFile() ? outputFile.length() : 0L;
        URLConnection connection = openConnection(url);
        if (existing > 0 && connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).setRequestProperty("Range", "bytes=" + existing + "-");
        }
        boolean append = false;
        if (connection instanceof HttpURLConnection) {
            int code = ((HttpURLConnection) connection).getResponseCode();
            if (code == HttpURLConnection.HTTP_PARTIAL) append = existing > 0;
            else if (code == 416 && allowReset) {
                ((HttpURLConnection) connection).disconnect();
                if (!outputFile.delete()) throw new IOException("Unable to reset partial download");
                downloadFileMonitoredUrlConnection(url, outputFile, buffer, monitor, false);
                return;
            } else if (code < 200 || code >= 300) {
                throw new IOException("Server returned HTTP " + code + " for " + url);
            }
        }
        try (InputStream readStr = connection.getInputStream();
             FileOutputStream fos = new FileOutputStream(outputFile, append)) {
            int current;
            long overall = append ? existing : 0;
            long responseLength = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? connection.getContentLengthLong()
                    : connection.getContentLength();
            long length = responseLength < 0 ? -1 : overall + responseLength;

            if (buffer == null) buffer = new byte[DOWNLOAD_BUFFER_SIZE];
            copyMonitored(readStr, fos, buffer, monitor, overall, length);
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
        }
    }

    public static <T> T downloadStringCached(String url, String cacheName, ParseCallback<T> parseCallback) throws IOException, ParseException{
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/"+cacheName);
        if(cacheDestination.isFile() &&
                cacheDestination.canRead() &&
                System.currentTimeMillis() < (cacheDestination.lastModified() + 86400000)) {
            try {
                String cachedString = Tools.read(new FileInputStream(cacheDestination));
                return parseCallback.process(cachedString);
            }catch(IOException e) {
                Log.i("DownloadUtils", "Failed to read the cached file", e);
            }catch (ParseException e) {
                Log.i("DownloadUtils", "Failed to parse the cached file", e);
            }
        }
        String urlContent = DownloadUtils.downloadString(url);
        // if we download the file and fail parsing it, we will yeet outta there
        // and not cache the unparseable sting. We will return this after trying to save the downloaded
        // string into cache
        T parseResult = parseCallback.process(urlContent);

        boolean tryWriteCache;
        if(cacheDestination.exists()) {
            tryWriteCache = cacheDestination.canWrite();
        } else {
            tryWriteCache = FileUtils.ensureParentDirectorySilently(cacheDestination);
        }

        if(tryWriteCache) try {
            Tools.write(cacheDestination.getAbsolutePath(), urlContent);
        }catch(IOException e) {
            Log.i("DownloadUtils", "Failed to cache the string", e);
        }
        return parseResult;
    }

    public static <T> T downloadStringFreshWithCacheFallback(String url, String cacheName, ParseCallback<T> parseCallback)
            throws IOException, ParseException {
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/" + cacheName);
        try {
            String urlContent = DownloadUtils.downloadString(url);
            T parseResult = parseCallback.process(urlContent);
            writeStringCache(cacheDestination, urlContent);
            return parseResult;
        } catch (IOException | ParseException networkError) {
            if (cacheDestination.isFile() && cacheDestination.canRead()) {
                try {
                    String cachedString = Tools.read(new FileInputStream(cacheDestination));
                    return parseCallback.process(cachedString);
                } catch (IOException e) {
                    Log.i("DownloadUtils", "Failed to read fallback cache", e);
                } catch (ParseException e) {
                    Log.i("DownloadUtils", "Failed to parse fallback cache", e);
                }
            }
            throw networkError;
        }
    }

    private static void writeStringCache(File cacheDestination, String value) {
        boolean tryWriteCache;
        if (cacheDestination.exists()) {
            tryWriteCache = cacheDestination.canWrite();
        } else {
            tryWriteCache = FileUtils.ensureParentDirectorySilently(cacheDestination);
        }

        if (tryWriteCache) {
            try {
                Tools.write(cacheDestination.getAbsolutePath(), value);
            } catch (IOException e) {
                Log.i("DownloadUtils", "Failed to cache the string", e);
            }
        }
    }

    private static <T> T downloadFile(Callable<T> downloadFunction) throws IOException{
        try {
            return downloadFunction.call();
        } catch (IOException e){
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyFile(File file, String sha1) {
        return file.exists() && Tools.compareSHA1(file, sha1);
    }

    public static <T> T ensureSha1(File outputFile, @Nullable String sha1, Callable<T> downloadFunction) throws IOException {
        // Skip if needed
        if(sha1 == null) {
            // If the file exists and we don't know it's SHA1, don't try to redownload it.
            if(outputFile.exists()) return null;
            else return downloadFile(downloadFunction);
        }

        int attempts = 0;
        boolean fileOkay = verifyFile(outputFile, sha1);
        T result = null;
        while (attempts < 5 && !fileOkay){
            attempts++;
            downloadFile(downloadFunction);
            fileOkay = verifyFile(outputFile, sha1);
        }
        if(!fileOkay) throw new SHA1VerificationException("SHA1 verifcation failed after 5 download attempts");
        return result;
    }

    /**
     * Get the content length for a given URL.
     * @param url the URL to get the length for
     * @return the length in bytes or -1 if not available
     * @throws IOException if an I/O error occurs.
     */
    public static long getContentLength(String url) throws IOException {
        URL parsedUrl = new URL(url);
        if (isHttp(parsedUrl)) {
            Request request = requestBuilder(url).head().build();
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) return -1L;
                ResponseBody body = response.body();
                return body == null ? -1L : body.contentLength();
            }
        }
        URLConnection connection = openConnection(parsedUrl);
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setRequestMethod("HEAD");
            httpConnection.setDoInput(false);
            httpConnection.setDoOutput(false);
            httpConnection.connect();
            int responseCode = httpConnection.getResponseCode();
            if (responseCode >= 200 && responseCode <= 299) {
                return httpConnection.getContentLength();
            }
            return -1;
        }
        connection.connect();
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? connection.getContentLengthLong()
                : connection.getContentLength();
    }

    private static URLConnection openConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(TIME_OUT);
        connection.setReadTimeout(TIME_OUT);
        connection.setDoInput(true);
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setRequestProperty("User-Agent", USER_AGENT);
            httpConnection.setRequestProperty("Accept-Encoding", "identity");
        }
        return connection;
    }

    public static <T> T readStringCache(String cacheName, ParseCallback<T> parseCallback)
            throws IOException, ParseException {
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/" + cacheName);
        if (!cacheDestination.isFile() || !cacheDestination.canRead()) {
            throw new IOException("Cached data is not available: " + cacheName);
        }
        return parseCallback.process(Tools.read(new FileInputStream(cacheDestination)));
    }

    public static boolean isValidZipArchive(File file) {
        if (file == null || !file.isFile() || file.length() == 0L) return false;
        byte[] buffer = new byte[32768];
        try (ZipFile zipFile = new ZipFile(file)) {
            if (!zipFile.entries().hasMoreElements()) return false;
            for (ZipEntry entry : Collections.list(zipFile.entries())) {
                if (entry.isDirectory()) continue;
                try (InputStream input = zipFile.getInputStream(entry)) {
                    while (input.read(buffer) != -1) {
                        // Reading each entry verifies its CRC and catches truncated archives.
                    }
                }
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static Request.Builder requestBuilder(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Encoding", "identity");
    }

    private static ResponseBody requireSuccessfulBody(Response response, String url)
            throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException("Server returned HTTP " + response.code() + " for " + url);
        }
        ResponseBody body = response.body();
        if (body == null) throw new EOFException("Server returned an empty response for " + url);
        return body;
    }

    private static boolean isHttp(URL url) {
        return "http".equalsIgnoreCase(url.getProtocol())
                || "https".equalsIgnoreCase(url.getProtocol());
    }

    private static void copyMonitored(InputStream input, OutputStream output, byte[] buffer,
                                      Tools.DownloaderFeedback monitor, long initial,
                                      long expectedLength) throws IOException {
        int count;
        long overall = initial;
        while ((count = input.read(buffer)) != -1) {
            overall += count;
            output.write(buffer, 0, count);
            monitor.updateProgress((int) Math.min(Integer.MAX_VALUE, overall),
                    (int) Math.min(Integer.MAX_VALUE, expectedLength));
        }
        if (expectedLength >= 0 && overall < expectedLength) {
            throw new EOFException("Download ended at " + overall + " of "
                    + expectedLength + " bytes");
        }
    }

    private static void waitBeforeRetry(int attempt, String url, IOException exception)
            throws IOException {
        Log.w("DownloadUtils", "Transient download failure (attempt " + attempt + ") for "
                + url + ": " + exception.getMessage());
        try {
            Thread.sleep(RETRY_DELAY_MS * attempt);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("Download retry interrupted", interruptedException);
        }
    }

    public interface ParseCallback<T> {
        T process(String input) throws ParseException;
    }
    public static class ParseException extends Exception {
        public ParseException(Exception e) {
            super(e);
        }
    }

    public static class SHA1VerificationException extends IOException {
        public SHA1VerificationException(String message) {
            super(message);
        }
    }
}

