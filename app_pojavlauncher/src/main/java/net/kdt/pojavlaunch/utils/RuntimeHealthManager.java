package net.kdt.pojavlaunch.utils;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuntimeHealthManager {
    private RuntimeHealthManager() {
    }

    public static List<Health> inspectAll() {
        List<Health> result = new ArrayList<>();
        for (Runtime runtime : MultiRTUtils.getRuntimes()) result.add(inspect(runtime.name));
        return Collections.unmodifiableList(result);
    }

    public static Health inspect(String name) {
        File root = new File(Tools.MULTIRT_HOME, name);
        Runtime runtime = MultiRTUtils.forceReread(name);
        List<String> missing = new ArrayList<>();
        check(missing, root, "release");
        check(missing, root, "bin/java");
        if (runtime.javaVersion <= 8) {
            checkAny(missing, root, "lib/" + runtime.arch + "/server/libjvm.so", "lib/server/libjvm.so");
        } else {
            checkAny(missing, root, "lib/server/libjvm.so", "lib/" + runtime.arch + "/server/libjvm.so");
            check(missing, root, "lib/modules");
        }
        return new Health(name, runtime.javaVersion, runtime.versionString,
                Collections.unmodifiableList(missing));
    }

    public static void removeBrokenDownloadDirectories() {
        File[] roots = new File(Tools.MULTIRT_HOME).listFiles(File::isDirectory);
        if (roots == null) return;
        for (File root : roots) {
            String lower = root.getName().toLowerCase();
            if ((lower.equals("downloads") || lower.equals("download") || lower.equals("tmp"))
                    && new File(root, "release").isFile()) {
                // A completed runtime accidentally left in a staging folder should not be shown as corrupt.
                File destination = new File(root.getParentFile(), "jre-imported-" + System.currentTimeMillis());
                root.renameTo(destination);
            }
        }
    }

    private static void check(List<String> missing, File root, String path) {
        if (!new File(root, path).exists()) missing.add(path);
    }

    private static void checkAny(List<String> missing, File root, String first, String second) {
        if (!new File(root, first).exists() && !new File(root, second).exists()) missing.add(first);
    }

    public static final class Health {
        public final String name;
        public final int javaMajor;
        public final String version;
        public final List<String> missing;

        Health(String name, int javaMajor, String version, List<String> missing) {
            this.name = name;
            this.javaMajor = javaMajor;
            this.version = version;
            this.missing = missing;
        }

        public boolean isHealthy() { return version != null && javaMajor > 0 && missing.isEmpty(); }
    }
}
