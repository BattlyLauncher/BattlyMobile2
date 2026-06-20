package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDependency;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchCategory;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.ZipUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        facetString.append(String.format("[\"project_type:%s\"]", getProjectType(searchFilters)));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        if(searchFilters.loader != null && !searchFilters.loader.isEmpty())
            facetString.append(String.format(",[\"categories:%s\"]", searchFilters.loader));
        if(searchFilters.category != null && searchFilters.category.source == Constants.SOURCE_MODRINTH)
            facetString.append(String.format(",[\"categories:%s\"]", searchFilters.category.id));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            long downloads = hit.has("downloads") && !hit.get("downloads").isJsonNull() ? hit.get("downloads").getAsLong() : 0;
            long follows = hit.has("follows") && !hit.get("follows").isJsonNull() ? hit.get("follows").getAsLong() : 0;
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    parseContentType(hit.get("project_type").getAsString()),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.get("icon_url").isJsonNull() ? null : hit.get("icon_url").getAsString(),
                    jsonArrayToStrings(hit.getAsJsonArray("categories")),
                    extractLoaders(hit.getAsJsonArray("categories")),
                    downloads,
                    follows
            );
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), JsonArray.class);
        if(response == null) return null;
        String[] names = new String[response.size()];
        String[] mcNames = new String[response.size()];
        String[] urls = new String[response.size()];
        String[] fileNames = new String[response.size()];
        String[] hashes = new String[response.size()];
        String[][] versionLoaders = new String[response.size()][];
        ModDependency[][] versionDependencies = new ModDependency[response.size()][];

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = version.get("name").getAsString();
            mcNames[i] = pickMinecraftVersion(version.get("game_versions").getAsJsonArray());
            JsonObject versionFile = pickPrimaryFile(version.get("files").getAsJsonArray());
            urls[i] = versionFile.get("url").getAsString();
            fileNames[i] = versionFile.get("filename").getAsString();
            versionLoaders[i] = jsonArrayToStrings(version.getAsJsonArray("loaders"));
            versionDependencies[i] = parseDependencies(version.getAsJsonArray("dependencies"));
            // Assume there may not be hashes, in case the API changes
            JsonObject hashesMap = versionFile.get("hashes").getAsJsonObject();
            if(hashesMap == null || hashesMap.get("sha1") == null){
                hashes[i] = null;
                continue;
            }

            hashes[i] = hashesMap.get("sha1").getAsString();
        }

        return new ModDetail(item, names, mcNames, urls, fileNames, hashes, versionLoaders, versionDependencies);
    }

    @Override
    public ModItem getModById(int contentType, String projectId) {
        JsonObject response = mApiHandler.get(String.format("project/%s", projectId), JsonObject.class);
        if (response == null) return null;
        long downloads = response.has("downloads") && !response.get("downloads").isJsonNull() ? response.get("downloads").getAsLong() : 0;
        long follows = response.has("followers") && !response.get("followers").isJsonNull() ? response.get("followers").getAsLong() : 0;
        return new ModItem(
                Constants.SOURCE_MODRINTH,
                contentType,
                response.get("id").getAsString(),
                response.get("title").getAsString(),
                response.get("description").getAsString(),
                response.get("icon_url") == null || response.get("icon_url").isJsonNull() ? null : response.get("icon_url").getAsString(),
                jsonArrayToStrings(response.getAsJsonArray("categories")),
                extractLoaders(response.getAsJsonArray("categories")),
                downloads,
                follows
        );
    }

    @Override
    public SearchCategory[] getCategories(SearchFilters searchFilters) {
        JsonArray categories = mApiHandler.get("tag/category", JsonArray.class);
        if (categories == null) {
            return new SearchCategory[0];
        }

        java.util.ArrayList<SearchCategory> result = new java.util.ArrayList<>();
        String projectType = getProjectType(searchFilters);
        for (JsonElement element : categories) {
            JsonObject category = element.getAsJsonObject();
            if (!projectType.equalsIgnoreCase(category.get("project_type").getAsString())) {
                continue;
            }
            String name = category.get("name").getAsString();
            result.add(new SearchCategory(Constants.SOURCE_MODRINTH, name, toDisplayLabel(name)));
        }
        return result.toArray(new SearchCategory[0]);
    }

    @Override
    public ModLoader installMod(Context context, ModDetail modDetail, int selectedVersion) throws IOException{
        return installMod(context, modDetail, selectedVersion, null);
    }

    @Override
    public ModLoader installMod(Context context,
                                ModDetail modDetail,
                                int selectedVersion,
                                MinecraftProfile targetProfile) throws IOException{
        if (!modDetail.isModpack) {
            ModInstallHelper.installContent(context, modDetail, selectedVersion, targetProfile);
            return null;
        }
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installMrpack);
    }

    @Override
    public ModLoader importModpack(Activity activity, Uri zipUri) throws IOException, NoSuchAlgorithmException {
        return ModpackInstaller.importModpack(activity, zipUri, this::installMrpack);
    }

    private static ModLoader createInfo(ModrinthIndex modrinthIndex) {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_NEOFORGE, modLoaderVersion, mcVersion);
        }
        return null;
    }

    private static String getProjectType(SearchFilters searchFilters) {
        switch (searchFilters.contentType) {
            case SearchFilters.TYPE_MOD:
                return "mod";
            case SearchFilters.TYPE_RESOURCEPACK:
                return "resourcepack";
            case SearchFilters.TYPE_SHADER:
                return "shader";
            case SearchFilters.TYPE_DATAPACK:
                return "datapack";
            case SearchFilters.TYPE_MODPACK:
            default:
                return "modpack";
        }
    }

    private static int parseContentType(String projectType) {
        switch (projectType) {
            case "mod":
                return SearchFilters.TYPE_MOD;
            case "resourcepack":
                return SearchFilters.TYPE_RESOURCEPACK;
            case "shader":
                return SearchFilters.TYPE_SHADER;
            case "datapack":
                return SearchFilters.TYPE_DATAPACK;
            case "modpack":
            default:
                return SearchFilters.TYPE_MODPACK;
        }
    }

    private static String[] jsonArrayToStrings(JsonArray array) {
        if (array == null) return new String[0];
        String[] values = new String[array.size()];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i).getAsString();
        }
        return values;
    }

    private static String[] extractLoaders(JsonArray categories) {
        if (categories == null) return new String[0];
        java.util.ArrayList<String> loaders = new java.util.ArrayList<>();
        for (JsonElement element : categories) {
            String category = element.getAsString().toLowerCase(Locale.ROOT);
            if ("fabric".equals(category) || "forge".equals(category) || "quilt".equals(category) || "neoforge".equals(category)) {
                loaders.add(category);
            }
        }
        return loaders.toArray(new String[0]);
    }

    private static JsonObject pickPrimaryFile(JsonArray files) {
        JsonObject fallback = files.get(0).getAsJsonObject();
        for (JsonElement file : files) {
            JsonObject candidate = file.getAsJsonObject();
            if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                return candidate;
            }
        }
        return fallback;
    }

    private static String pickMinecraftVersion(JsonArray gameVersions) {
        for (JsonElement element : gameVersions) {
            String version = element.getAsString();
            if (version.startsWith("1.") || version.contains("w")) {
                return version;
            }
        }
        return gameVersions.size() > 0 ? gameVersions.get(0).getAsString() : null;
    }

    private static ModDependency[] parseDependencies(JsonArray dependencyArray) {
        if (dependencyArray == null || dependencyArray.size() == 0) {
            return new ModDependency[0];
        }

        java.util.ArrayList<ModDependency> dependencies = new java.util.ArrayList<>();
        for (JsonElement element : dependencyArray) {
            JsonObject dependency = element.getAsJsonObject();
            if (!dependency.has("project_id") || dependency.get("project_id").isJsonNull()) {
                continue;
            }
            String projectId = dependency.get("project_id").getAsString();
            boolean required = dependency.has("dependency_type")
                    && "required".equalsIgnoreCase(dependency.get("dependency_type").getAsString());
            dependencies.add(new ModDependency(projectId, projectId, required));
        }
        return dependencies.toArray(new ModDependency[0]);
    }

    private static String toDisplayLabel(String value) {
        if (!Tools.isValidString(value)) return value;
        String normalized = value.replace('-', ' ');
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private ModLoader installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            
            ModDownloader modDownloader = new ModDownloader(instanceDestination);
            for(ModrinthIndex.ModrinthIndexFile indexFile : modrinthIndex.files) {
                if (indexFile.env != null && "unsupported".equals(indexFile.env.client)) continue;
                String sha1 = indexFile.hashes != null ? indexFile.hashes.sha1 : null;
                modDownloader.submitDownload(indexFile.fileSize, indexFile.path, sha1, indexFile.downloads);
            }
            modDownloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }
}
