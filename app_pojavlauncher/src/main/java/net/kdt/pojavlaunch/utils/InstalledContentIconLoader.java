package net.kdt.pojavlaunch.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class InstalledContentIconLoader {
    private static final int MAX_ICON_BYTES = 4 * 1024 * 1024;
    private static final int TARGET_ICON_SIZE = 192;
    private static final String[] FALLBACK_NAMES = {
            "pack.png", "icon.png", "logo.png", "preview.png"
    };
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private InstalledContentIconLoader() {
    }

    public static @Nullable Bitmap load(File content, @Nullable String preferredPath) {
        if (content == null || !content.exists()) return null;
        String key = content.getAbsolutePath() + ':' + content.lastModified() + ':' + content.length()
                + ':' + normalize(preferredPath);
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) return cached;

        Bitmap bitmap = content.isDirectory()
                ? loadFromDirectory(content, preferredPath)
                : loadFromArchive(content, preferredPath);
        if (bitmap != null) CACHE.put(key, bitmap);
        return bitmap;
    }

    private static @Nullable Bitmap loadFromDirectory(File directory, @Nullable String preferredPath) {
        List<String> candidates = new ArrayList<>();
        if (preferredPath != null) candidates.add(preferredPath);
        for (String fallback : FALLBACK_NAMES) candidates.add(fallback);
        for (String candidate : candidates) {
            File image = new File(directory, normalize(candidate));
            if (!image.isFile() || image.length() <= 0 || image.length() > MAX_ICON_BYTES) continue;
            try (InputStream input = new FileInputStream(image)) {
                Bitmap bitmap = decodeBounded(readBounded(input, image.length()));
                if (bitmap != null) return bitmap;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static @Nullable Bitmap loadFromArchive(File archive, @Nullable String preferredPath) {
        try (ZipFile zip = new ZipFile(archive)) {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) names.add(entry.getName());
            }
            String selected = chooseArchiveEntry(names, preferredPath);
            if (selected == null) return null;
            ZipEntry entry = zip.getEntry(selected);
            if (entry == null || entry.getSize() > MAX_ICON_BYTES) return null;
            try (InputStream input = zip.getInputStream(entry)) {
                return decodeBounded(readBounded(input, entry.getSize()));
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public static @Nullable String chooseArchiveEntry(List<String> entries, @Nullable String preferredPath) {
        if (entries == null || entries.isEmpty()) return null;
        String preferred = normalize(preferredPath);
        if (!preferred.isEmpty()) {
            for (String entry : entries) {
                if (normalize(entry).equalsIgnoreCase(preferred)) return entry;
            }
        }
        for (String fallback : FALLBACK_NAMES) {
            for (String entry : entries) {
                if (normalize(entry).equalsIgnoreCase(fallback)) return entry;
            }
        }
        for (String fallback : new String[]{"icon.png", "logo.png"}) {
            String suffix = '/' + fallback;
            for (String entry : entries) {
                if (normalize(entry).toLowerCase(Locale.ROOT).endsWith(suffix)) return entry;
            }
        }
        return null;
    }

    private static String normalize(@Nullable String path) {
        if (path == null) return "";
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private static byte[] readBounded(InputStream input, long expectedSize) throws Exception {
        int capacity = expectedSize > 0 && expectedSize <= MAX_ICON_BYTES ? (int) expectedSize : 8192;
        ByteArrayOutputStream output = new ByteArrayOutputStream(capacity);
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ICON_BYTES) return null;
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static @Nullable Bitmap decodeBounded(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        int sampleSize = 1;
        while (largest / (sampleSize * 2) >= TARGET_ICON_SIZE) sampleSize *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
