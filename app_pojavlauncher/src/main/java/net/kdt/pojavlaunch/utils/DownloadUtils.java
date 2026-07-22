package net.kdt.pojavlaunch.utils;

import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.Callable;

import net.kdt.pojavlaunch.*;
import org.apache.commons.io.*;

@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    public static final String USER_AGENT = Tools.APP_NAME;
    private static final int TIME_OUT = 10000;

    public static void download(String url, OutputStream os) throws IOException {
        download(new URL(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
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
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        download(url, bos);
        bos.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void downloadFile(String url, File out) throws IOException {
        downloadFileMonitored(url, out, null, (current, total) -> { });
    }

    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        downloadFileMonitored(urlInput, outputFile, buffer, monitor, true);
    }

    private static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                              Tools.DownloaderFeedback monitor, boolean allowReset) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);
        long existing = outputFile.isFile() ? outputFile.length() : 0L;
        URLConnection connection = openConnection(new URL(urlInput));
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
                downloadFileMonitored(urlInput, outputFile, buffer, monitor, false);
                return;
            } else if (code < 200 || code >= 300) {
                throw new IOException("Server returned HTTP " + code + " for " + urlInput);
            }
        }
        InputStream readStr = connection.getInputStream();
        try (FileOutputStream fos = new FileOutputStream(outputFile, append)) {
            int current;
            long overall = append ? existing : 0;
            long responseLength = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? connection.getContentLengthLong()
                    : connection.getContentLength();
            long length = responseLength < 0 ? -1 : overall + responseLength;

            if (buffer == null) buffer = new byte[65535];

            while ((current = readStr.read(buffer)) != -1) {
                overall += current;
                fos.write(buffer, 0, current);
                monitor.updateProgress((int) Math.min(Integer.MAX_VALUE, overall),
                        (int) Math.min(Integer.MAX_VALUE, length));
            }
        } catch (IOException e) {
            throw new IOException("Unable to download from " + urlInput, e);
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
        URLConnection connection = openConnection(new URL(url));
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
            ((HttpURLConnection) connection).setRequestProperty("User-Agent", USER_AGENT);
        }
        return connection;
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

