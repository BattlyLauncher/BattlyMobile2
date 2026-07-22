package net.kdt.pojavlaunch.utils;

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RendererPluginRegistry {
    private static final String ASSET_MANIFEST = "renderer_plugins.json";
    private static final String EXTERNAL_MANIFEST = "renderer_plugins.json";

    private RendererPluginRegistry() {
    }

    public static List<Entry> load(Context context) {
        List<Entry> entries = new ArrayList<>();
        readManifest(context, true, null, entries);
        if (Tools.isValidString(Tools.DIR_GAME_HOME)) {
            readManifest(context, false, new File(Tools.DIR_GAME_HOME, EXTERNAL_MANIFEST), entries);
        }
        return entries;
    }

    public static List<Entry> compatible(Context context) {
        List<Entry> compatible = new ArrayList<>();
        for (Entry entry : load(context)) {
            if (entry.isCompatible(context)) {
                compatible.add(entry);
            }
        }
        return compatible;
    }

    public static String description(Context context, String rendererId) {
        for (Entry entry : load(context)) {
            if (entry.id.equals(rendererId)) {
                return entry.description;
            }
        }
        return "";
    }

    private static void readManifest(Context context, boolean asset, File file, List<Entry> output) {
        try {
            String content;
            if (asset) {
                try (InputStream inputStream = context.getAssets().open(ASSET_MANIFEST)) {
                    content = readFully(inputStream);
                }
            } else {
                if (file == null || !file.isFile()) {
                    return;
                }
                try (InputStream inputStream = new java.io.FileInputStream(file)) {
                    content = readFully(inputStream);
                }
            }
            JSONObject root = new JSONObject(content);
            JSONArray renderers = root.optJSONArray("renderers");
            if (renderers == null) {
                return;
            }
            for (int i = 0; i < renderers.length(); i++) {
                JSONObject object = renderers.optJSONObject(i);
                Entry entry = Entry.fromJson(object);
                if (entry != null && !contains(output, entry.id)) {
                    output.add(entry);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean contains(List<Entry> entries, String id) {
        for (Entry entry : entries) {
            if (entry.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String readFully(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    public static final class Entry {
        public final String id;
        public final String name;
        public final String description;
        public final String runtimeRenderer;
        public final int minSdk;
        public final boolean requiresVulkan;
        public final boolean requiresGles3;
        public final boolean requiresArm64;
        public final boolean requiresAdreno;
        public final boolean enabled;
        public final List<String> requiredLibraries;

        private Entry(String id, String name, String description, int minSdk, boolean requiresVulkan,
                      boolean requiresGles3, boolean requiresArm64, boolean requiresAdreno,
                      boolean enabled, String runtimeRenderer, List<String> requiredLibraries) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.runtimeRenderer = Tools.isValidString(runtimeRenderer) ? runtimeRenderer : id;
            this.minSdk = minSdk;
            this.requiresVulkan = requiresVulkan;
            this.requiresGles3 = requiresGles3;
            this.requiresArm64 = requiresArm64;
            this.requiresAdreno = requiresAdreno;
            this.enabled = enabled;
            this.requiredLibraries = requiredLibraries;
        }

        private static Entry fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            String id = object.optString("id", "").trim();
            String name = object.optString("name", id).trim();
            if (!Tools.isValidString(id) || !Tools.isValidString(name)) {
                return null;
            }
            List<String> libraries = new ArrayList<>();
            JSONArray libraryArray = object.optJSONArray("requiredLibraries");
            if (libraryArray != null) {
                for (int i = 0; i < libraryArray.length(); i++) {
                    String library = libraryArray.optString(i, "").trim();
                    if (Tools.isValidString(library)) {
                        libraries.add(library);
                    }
                }
            }
            return new Entry(
                    id,
                    name,
                    object.optString("description", ""),
                    object.optInt("minSdk", 0),
                    object.optBoolean("requiresVulkan", false),
                    object.optBoolean("requiresGles3", false),
                    object.optBoolean("requiresArm64", false),
                    object.optBoolean("requiresAdreno", false),
                    object.optBoolean("enabled", true),
                    object.optString("runtimeRenderer", id),
                    Collections.unmodifiableList(libraries)
            );
        }

        public boolean isCompatible(Context context) {
            if (!enabled) {
                return false;
            }
            if (minSdk > 0 && SDK_INT < minSdk) {
                return false;
            }
            if (requiresVulkan && !Tools.checkVulkanSupport(context.getPackageManager())) {
                return false;
            }
            if (requiresGles3 && JREUtils.getDetectedVersion() < 3) {
                return false;
            }
            if (requiresArm64 && Architecture.getDeviceArchitecture() != Architecture.ARCH_ARM64) {
                return false;
            }
            if (requiresAdreno && !GLInfoUtils.getGlInfo().isAdreno()) {
                return false;
            }
            for (String library : requiredLibraries) {
                if (!hasNativeLibrary(context, library)) {
                    return false;
                }
            }
            return true;
        }

        public boolean looksLikeTextureViewRenderer() {
            String normalized = id.toLowerCase(Locale.ROOT);
            return normalized.contains("zink")
                    || normalized.contains("mobileglues")
                    || normalized.contains("freedreno")
                    || normalized.contains("vulkan")
                    || normalized.contains("opengles3");
        }

        private boolean hasNativeLibrary(Context context, String libraryName) {
            if (Tools.isValidString(Tools.NATIVE_LIB_DIR)
                    && new File(Tools.NATIVE_LIB_DIR, libraryName).isFile()) {
                return true;
            }
            try {
                String nativeDir = context.getApplicationInfo().nativeLibraryDir;
                return Tools.isValidString(nativeDir) && new File(nativeDir, libraryName).isFile();
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    public static String runtimeRendererFor(Context context, String rendererId) {
        if (!Tools.isValidString(rendererId)) {
            return rendererId;
        }
        for (Entry entry : load(context)) {
            if (entry.id.equals(rendererId)) {
                return entry.runtimeRenderer;
            }
        }
        return rendererId;
    }
}
