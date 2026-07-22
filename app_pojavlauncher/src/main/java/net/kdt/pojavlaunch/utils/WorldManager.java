package net.kdt.pojavlaunch.utils;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** World discovery and safe import/export without depending on a Minecraft runtime. */
public final class WorldManager {
    private static final long MAX_WORLD_IMPORT = 16L * 1024L * 1024L * 1024L;
    private static final long CACHE_TTL_MS = 60_000L;
    private static final Map<String, WorldCache> WORLD_CACHE = new ConcurrentHashMap<>();

    private WorldManager() {
    }

    public static List<WorldInfo> list(@NonNull MinecraftProfile profile) {
        File saves = new File(Tools.getGameDirPath(profile), "saves");
        File[] directories = saves.listFiles(file -> file.isDirectory() && new File(file, "level.dat").isFile());
        String cacheKey = cacheKey(saves);
        long signature = signature(saves, directories);
        WorldCache cached = WORLD_CACHE.get(cacheKey);
        if (cached != null && cached.signature == signature
                && System.currentTimeMillis() - cached.createdAt <= CACHE_TTL_MS) {
            return new ArrayList<>(cached.worlds);
        }
        List<WorldInfo> worlds = new ArrayList<>();
        if (directories != null) {
            for (File directory : directories) worlds.add(readInfo(directory));
        }
        worlds.sort(Comparator.comparingLong((WorldInfo world) -> world.lastPlayed).reversed());
        WORLD_CACHE.put(cacheKey, new WorldCache(signature, System.currentTimeMillis(), new ArrayList<>(worlds)));
        return worlds;
    }

    public static void invalidate(@NonNull MinecraftProfile profile) {
        File gameDirectory = Tools.getGameDirPath(profile);
        WORLD_CACHE.remove(cacheKey(new File(gameDirectory, "saves")));
        InstanceManager.invalidateDirectorySize(gameDirectory);
    }

    public static WorldInfo readInfo(File directory) {
        MutableInfo info = new MutableInfo();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(new File(directory, "level.dat")))))) {
            int rootType = input.readUnsignedByte();
            if (rootType == 10) {
                input.readUTF();
                readCompound(input, "", info, 0);
            }
        } catch (Throwable ignored) {
        }
        String displayName = Tools.isValidString(info.levelName) ? info.levelName : directory.getName();
        long size = directorySize(directory);
        return new WorldInfo(directory, displayName, info.lastPlayed, info.gameType,
                info.hardcore, info.allowCommands, info.versionName, size);
    }

    public static File duplicate(WorldInfo world, MinecraftProfile profile, String requestedName) throws IOException {
        File saves = new File(Tools.getGameDirPath(profile), "saves");
        ensureDirectory(saves);
        File destination = uniqueDirectory(saves, sanitize(requestedName));
        FileUtils.copyDirectory(world.directory, destination);
        invalidate(profile);
        return destination;
    }

    public static void delete(WorldInfo world) throws IOException {
        FileUtils.deleteDirectory(world.directory);
        WORLD_CACHE.clear();
    }

    public static void exportWorld(WorldInfo world, OutputStream output) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            zipTree(zip, world.directory, world.directory.getName() + "/");
        }
    }

    public static File importWorld(MinecraftProfile profile, InputStream input, String fallbackName) throws IOException {
        File saves = new File(Tools.getGameDirPath(profile), "saves");
        ensureDirectory(saves);
        File staging = new File(saves, ".import-" + UUID.randomUUID());
        ensureDirectory(staging);
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                String relative = stripSingleRoot(entry.getName());
                if (relative.isEmpty()) continue;
                File target = safeChild(staging, relative);
                if (entry.isDirectory()) {
                    ensureDirectory(target);
                } else {
                    ensureDirectory(target.getParentFile());
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read;
                            if (total > MAX_WORLD_IMPORT) throw new IOException("World archive is too large");
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            FileUtils.deleteDirectory(staging);
            throw exception;
        }
        if (!new File(staging, "level.dat").isFile()) {
            File nested = findWorldRoot(staging);
            if (nested == null) {
                FileUtils.deleteDirectory(staging);
                throw new IOException("The archive does not contain a Minecraft world");
            }
            File normalized = new File(saves, ".normalized-" + UUID.randomUUID());
            FileUtils.moveDirectory(nested, normalized);
            FileUtils.deleteDirectory(staging);
            staging = normalized;
        }
        String worldName = Tools.isValidString(fallbackName) ? fallbackName : readInfo(staging).displayName;
        File destination = uniqueDirectory(saves, sanitize(worldName));
        FileUtils.moveDirectory(staging, destination);
        invalidate(profile);
        return destination;
    }

    private static void readCompound(DataInputStream input, String path, MutableInfo info, int depth) throws IOException {
        if (depth > 32) throw new IOException("NBT nesting too deep");
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) return;
            String name = input.readUTF();
            String fullPath = path.isEmpty() ? name : path + "/" + name;
            if (type == 8) {
                String value = input.readUTF();
                if (fullPath.endsWith("Data/LevelName")) info.levelName = value;
                if (fullPath.endsWith("Data/Version/Name")) info.versionName = value;
            } else if (type == 3) {
                int value = input.readInt();
                if (fullPath.endsWith("Data/GameType")) info.gameType = value;
            } else if (type == 4) {
                long value = input.readLong();
                if (fullPath.endsWith("Data/LastPlayed")) info.lastPlayed = value;
            } else if (type == 1) {
                byte value = input.readByte();
                if (fullPath.endsWith("Data/hardcore")) info.hardcore = value != 0;
                if (fullPath.endsWith("Data/allowCommands")) info.allowCommands = value != 0;
            } else if (type == 10) {
                readCompound(input, fullPath, info, depth + 1);
            } else {
                skipPayload(input, type, depth);
            }
        }
    }

    private static void skipPayload(DataInputStream input, int type, int depth) throws IOException {
        switch (type) {
            case 1: input.readByte(); break;
            case 2: input.readShort(); break;
            case 3: input.readInt(); break;
            case 4: input.readLong(); break;
            case 5: input.readFloat(); break;
            case 6: input.readDouble(); break;
            case 7: skipFully(input, input.readInt()); break;
            case 8: input.readUTF(); break;
            case 9:
                int childType = input.readUnsignedByte();
                int length = checkedLength(input.readInt());
                for (int i = 0; i < length; i++) skipPayload(input, childType, depth + 1);
                break;
            case 10: readCompound(input, "", new MutableInfo(), depth + 1); break;
            case 11: skipFully(input, checkedLength(input.readInt()) * 4L); break;
            case 12: skipFully(input, checkedLength(input.readInt()) * 8L); break;
            default: throw new IOException("Unknown NBT tag " + type);
        }
    }

    private static int checkedLength(int length) throws IOException {
        if (length < 0 || length > 64_000_000) throw new IOException("Invalid NBT length");
        return length;
    }

    private static void skipFully(DataInputStream input, long length) throws IOException {
        if (length < 0 || length > MAX_WORLD_IMPORT) throw new IOException("Invalid NBT payload");
        long remaining = length;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() == -1) throw new IOException("Unexpected end of NBT");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static void zipTree(ZipOutputStream zip, File file, String path) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) zipTree(zip, child,
                    path + child.getName() + (child.isDirectory() ? "/" : ""));
            return;
        }
        zip.putNextEntry(new ZipEntry(path));
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    private static File findWorldRoot(File root) {
        if (new File(root, "level.dat").isFile()) return root;
        File[] children = root.listFiles(File::isDirectory);
        if (children != null) for (File child : children) {
            File found = findWorldRoot(child);
            if (found != null) return found;
        }
        return null;
    }

    private static String stripSingleRoot(String entry) {
        if (entry == null) return "";
        String normalized = entry.replace('\\', '/').replaceFirst("^/+", "");
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static File safeChild(File root, String relative) throws IOException {
        File child = new File(root, relative);
        String rootPath = root.getCanonicalPath() + File.separator;
        if (!child.getCanonicalPath().startsWith(rootPath)) throw new IOException("Unsafe world archive path");
        return child;
    }

    private static File uniqueDirectory(File parent, String base) {
        File candidate = new File(parent, base);
        int suffix = 2;
        while (candidate.exists()) candidate = new File(parent, base + "-" + suffix++);
        return candidate;
    }

    private static String sanitize(String value) {
        String clean = value == null ? "world" : value.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "").toLowerCase(Locale.ROOT);
        return clean.isEmpty() ? "world" : clean;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Unable to create " + directory);
    }

    private static long directorySize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += directorySize(child);
        return total;
    }

    private static long signature(File saves, File[] directories) {
        long value = saves.isDirectory() ? saves.lastModified() : 0L;
        if (directories == null) return value;
        value = value * 31L + directories.length;
        for (File directory : directories) {
            File level = new File(directory, "level.dat");
            value = value * 31L + directory.getName().hashCode();
            value = value * 31L + level.lastModified();
            value = value * 31L + level.length();
        }
        return value;
    }

    private static String cacheKey(File directory) {
        try {
            return directory.getCanonicalPath();
        } catch (IOException ignored) {
            return directory.getAbsolutePath();
        }
    }

    private static final class MutableInfo {
        String levelName;
        long lastPlayed;
        int gameType = -1;
        boolean hardcore;
        boolean allowCommands;
        String versionName;
    }

    public static final class WorldInfo {
        public final File directory;
        public final String displayName;
        public final long lastPlayed;
        public final int gameType;
        public final boolean hardcore;
        public final boolean allowCommands;
        public final String versionName;
        public final long sizeBytes;

        WorldInfo(File directory, String displayName, long lastPlayed, int gameType,
                  boolean hardcore, boolean allowCommands, String versionName, long sizeBytes) {
            this.directory = directory;
            this.displayName = displayName;
            this.lastPlayed = lastPlayed;
            this.gameType = gameType;
            this.hardcore = hardcore;
            this.allowCommands = allowCommands;
            this.versionName = versionName;
            this.sizeBytes = sizeBytes;
        }
    }

    private static final class WorldCache {
        final long signature;
        final long createdAt;
        final List<WorldInfo> worlds;

        WorldCache(long signature, long createdAt, List<WorldInfo> worlds) {
            this.signature = signature;
            this.createdAt = createdAt;
            this.worlds = worlds;
        }
    }
}
