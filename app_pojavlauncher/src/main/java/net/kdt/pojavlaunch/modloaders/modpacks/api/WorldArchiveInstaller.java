package net.kdt.pojavlaunch.modloaders.modpacks.api;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class WorldArchiveInstaller {
    private static final long MAX_UNPACKED_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 100_000;

    private WorldArchiveInstaller() {
    }

    static File install(File archive, File savesDirectory, String requestedName) throws IOException {
        ensureDirectory(savesDirectory);
        File staging = new File(savesDirectory, ".battly-world-" + UUID.randomUUID());
        ensureDirectory(staging);
        try {
            extract(archive, staging);
            File worldRoot = findWorldRoot(staging);
            if (worldRoot == null) throw new IOException("The archive does not contain a Minecraft level.dat file");
            File destination = uniqueDestination(savesDirectory, sanitize(requestedName));
            try {
                if (!worldRoot.renameTo(destination)) {
                    org.apache.commons.io.FileUtils.copyDirectory(worldRoot, destination);
                }
            } catch (IOException failure) {
                org.apache.commons.io.FileUtils.deleteDirectory(destination);
                throw failure;
            }
            return destination;
        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(staging);
        }
    }

    private static void extract(File archive, File destination) throws IOException {
        long unpacked = 0;
        int count = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) throw new IOException("World archive contains too many files");
                String name = normalize(entry.getName());
                if (name.isEmpty()) continue;
                File target = safeChild(destination, name);
                if (entry.isDirectory()) {
                    ensureDirectory(target);
                    continue;
                }
                ensureDirectory(target.getParentFile());
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
                     BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        unpacked += read;
                        if (unpacked > MAX_UNPACKED_BYTES) throw new IOException("World archive is too large");
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static File findWorldRoot(File directory) {
        return findWorldRoot(directory, 0);
    }

    private static File findWorldRoot(File directory, int depth) {
        if (new File(directory, "level.dat").isFile()) return directory;
        if (depth >= 4) return null;
        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) return null;
        for (File child : children) {
            File found = findWorldRoot(child, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static File uniqueDestination(File parent, String name) {
        File candidate = new File(parent, name);
        int suffix = 2;
        while (candidate.exists()) candidate = new File(parent, name + " " + suffix++);
        return candidate;
    }

    private static String sanitize(String value) {
        String clean = value == null ? "" : value.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("[. ]+$", "");
        return clean.isEmpty() ? "Battly World" : clean;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/').replaceFirst("^/+", "");
    }

    private static File safeChild(File parent, String relative) throws IOException {
        File child = new File(parent, relative);
        String root = parent.getCanonicalPath() + File.separator;
        if (!child.getCanonicalPath().startsWith(root)) throw new IOException("Unsafe world archive path");
        return child;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create directory: " + directory);
        }
    }
}
