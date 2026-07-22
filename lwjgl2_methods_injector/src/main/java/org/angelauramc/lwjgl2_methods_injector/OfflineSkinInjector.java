package org.angelauramc.lwjgl2_methods_injector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class OfflineSkinInjector {
    private static volatile byte[] skinBytes;
    private static String username;
    private static String uuid;
    private static boolean slim;

    private OfflineSkinInjector() {
    }

    public static void installIfConfigured() {
        if (!Boolean.getBoolean("battly.offlineSkin.enabled")) {
            return;
        }
        String skinPath = System.getProperty("battly.offlineSkin.path", "");
        File skinFile = new File(skinPath);
        if (!skinFile.isFile()) {
            return;
        }
        try {
            skinBytes = readAll(skinFile);
            username = safe(System.getProperty("battly.offlineSkin.username", "Steve"));
            uuid = safe(System.getProperty("battly.offlineSkin.uuid", ""));
            slim = Boolean.parseBoolean(System.getProperty("battly.offlineSkin.slim", "false"));
            URL.setURLStreamHandlerFactory(new SkinUrlStreamHandlerFactory());
            System.out.println("[Battly/INFO] Offline skin injector enabled for " + username);
        } catch (Error error) {
            System.out.println("[Battly/WARNING] Offline skin URL factory already installed: " + error.getMessage());
        } catch (Throwable throwable) {
            System.out.println("[Battly/WARNING] Offline skin injector failed: " + throwable.getMessage());
        }
    }

    private static boolean shouldServeSkin(URL url) {
        String host = safe(url.getHost()).toLowerCase(Locale.ROOT);
        String path = safe(url.getPath()).toLowerCase(Locale.ROOT);
        String user = username == null ? "" : username.toLowerCase(Locale.ROOT);
        return (host.contains("textures.minecraft.net") && path.contains("battly-offline"))
                || (path.endsWith("/" + user + ".png") && (host.contains("minecraftskins") || host.contains("skins.minecraft.net") || host.contains("s3.amazonaws.com")))
                || (host.contains("minotar.net") && path.contains(user));
    }

    private static boolean shouldServeProfile(URL url) {
        String host = safe(url.getHost()).toLowerCase(Locale.ROOT);
        String path = safe(url.getPath()).toLowerCase(Locale.ROOT);
        return host.contains("sessionserver.mojang.com")
                && path.contains("/session/minecraft/profile/")
                && skinBytes != null;
    }

    private static byte[] profileJson() {
        String skinUrl = "http://textures.minecraft.net/texture/battly-offline/" + username;
        String metadata = slim ? ",\"metadata\":{\"model\":\"slim\"}" : "";
        String textures = "{\"timestamp\":" + System.currentTimeMillis()
                + ",\"profileId\":\"" + json(uuid) + "\""
                + ",\"profileName\":\"" + json(username) + "\""
                + ",\"textures\":{\"SKIN\":{\"url\":\"" + json(skinUrl) + "\"" + metadata + "}}}";
        String value = Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8));
        String json = "{\"id\":\"" + json(uuid) + "\",\"name\":\"" + json(username)
                + "\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value + "\"}]}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String json(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static byte[] readAll(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static final class SkinUrlStreamHandlerFactory implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                return null;
            }
            URLStreamHandler fallback = createFallbackHandler(protocol);
            return new SkinUrlStreamHandler(fallback);
        }
    }

    private static URLStreamHandler createFallbackHandler(String protocol) {
        try {
            Class<?> handlerClass = Class.forName("sun.net.www.protocol." + protocol + ".Handler");
            Constructor<?> constructor = handlerClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (URLStreamHandler) constructor.newInstance();
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static final class SkinUrlStreamHandler extends URLStreamHandler {
        private final URLStreamHandler fallback;

        SkinUrlStreamHandler(URLStreamHandler fallback) {
            this.fallback = fallback;
        }

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            if (shouldServeSkin(url)) {
                return new MemoryURLConnection(url, "image/png", skinBytes);
            }
            if (shouldServeProfile(url)) {
                return new MemoryURLConnection(url, "application/json", profileJson());
            }
            if (fallback != null) {
                return new URL(null, url.toString(), fallback).openConnection();
            }
            throw new IOException("No fallback URL handler available for " + url.getProtocol());
        }
    }

    private static final class MemoryURLConnection extends HttpURLConnection {
        private final String contentType;
        private final byte[] data;

        MemoryURLConnection(URL url, String contentType, byte[] data) {
            super(url);
            this.contentType = contentType;
            this.data = data == null ? new byte[0] : data;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public int getResponseCode() {
            return HTTP_OK;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public int getContentLength() {
            return data.length;
        }

        @Override
        public long getContentLengthLong() {
            return data.length;
        }

        @Override
        public ByteArrayInputStream getInputStream() {
            return new ByteArrayInputStream(data);
        }
    }
}
