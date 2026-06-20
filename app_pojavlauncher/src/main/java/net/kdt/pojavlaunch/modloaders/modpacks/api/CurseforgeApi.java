package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDependency;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchCategory;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    private static final int CURSEFORGE_MODPACK_CLASS_ID = 4471;
    // https://api.curseforge.com/v1/categories?gameId=432 and search for "Mods" (case-sensitive)
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_RESOURCE_PACK_CLASS_ID = 12;
    private static final int CURSEFORGE_SORT_RELEVANCY = 1;
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    public CurseforgeApi(String apiKey) {
        mApiHandler = new ApiHandler("https://api.curseforge.com/v1", apiKey);
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        if (!supportsContentType(searchFilters.contentType)) {
            CurseforgeSearchResult emptyResult = new CurseforgeSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = 0;
            return emptyResult;
        }
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MINECRAFT_GAME_ID);
        params.put("classId", getClassId(searchFilters));
        params.put("searchFilter", searchFilters.name);
        params.put("sortField", CURSEFORGE_SORT_RELEVANCY);
        params.put("sortOrder", "desc");
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        if(searchFilters.category != null && searchFilters.category.source == Constants.SOURCE_CURSEFORGE)
            params.put("categoryId", Integer.parseInt(searchFilters.category.id));
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = response.getAsJsonArray("data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = response.getAsJsonObject("pagination");
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        for(int i = 0; i < dataArray.size(); i++) {
            JsonObject dataElement = dataArray.get(i).getAsJsonObject();
            JsonElement allowModDistribution = dataElement.get("allowModDistribution");
            // Gson automatically casts null to false, which leans to issues
            // So, only check the distribution flag if it is non-null
            if(!allowModDistribution.isJsonNull() && !allowModDistribution.getAsBoolean()) {
                Log.i("CurseforgeApi", "Skipping modpack "+dataElement.get("name").getAsString() + " because curseforge sucks");
                continue;
            }
            ModItem modItem = new ModItem(Constants.SOURCE_CURSEFORGE,
                    searchFilters.contentType,
                    dataElement.get("id").getAsString(),
                    dataElement.get("name").getAsString(),
                    dataElement.get("summary").getAsString(),
                    dataElement.getAsJsonObject("logo").get("thumbnailUrl").getAsString(),
                    extractCategories(dataElement),
                    extractLoaders(dataElement));
            if (!matchesLoaderFilter(searchFilters, modItem.loaders)) {
                continue;
            }
            modItemList.add(modItem);
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        curseforgeSearchResult.totalResultCount = paginationInfo.get("totalCount").getAsInt();
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        while(index != CURSEFORGE_PAGINATION_END_REACHED &&
                index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item.id);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;
        int length = allModDetails.size();
        String[] versionNames = new String[length];
        String[] mcVersionNames = new String[length];
        String[] versionUrls = new String[length];
        String[] versionFileNames = new String[length];
        String[] hashes = new String[length];
        String[][] versionLoaders = new String[length][];
        ModDependency[][] versionDependencies = new ModDependency[length][];
        for(int i = 0; i < allModDetails.size(); i++) {
            JsonObject modDetail = allModDetails.get(i);
            versionNames[i] = modDetail.get("displayName").getAsString();
            versionFileNames[i] = GsonJsonUtils.getStringSafe(modDetail, "fileName");

            JsonElement downloadUrl = modDetail.get("downloadUrl");
            if(downloadUrl != null && !downloadUrl.isJsonNull()) {
                versionUrls[i] = downloadUrl.getAsString();
            } else {
                versionUrls[i] = getDownloadUrl(
                        Long.parseLong(item.id),
                        modDetail.get("id").getAsLong()
                );
            }

            JsonArray gameVersions = modDetail.getAsJsonArray("gameVersions");
            for(JsonElement jsonElement : gameVersions) {
                String gameVersion = jsonElement.getAsString();
                if(!sMcVersionPattern.matcher(gameVersion).matches()) {
                    continue;
                }
                mcVersionNames[i] = gameVersion;
                break;
            }

            hashes[i] = getSha1FromModData(modDetail);
            versionLoaders[i] = extractLoadersFromVersions(gameVersions);
            versionDependencies[i] = parseDependencies(modDetail.getAsJsonArray("dependencies"));
        }
        return new ModDetail(item, versionNames, mcVersionNames, versionUrls, versionFileNames, hashes, versionLoaders, versionDependencies);
    }

    @Override
    public ModItem getModById(int contentType, String projectId) {
        JsonObject response = mApiHandler.get("mods/" + projectId, JsonObject.class);
        JsonObject data = GsonJsonUtils.getJsonObjectSafe(response, "data");
        if (data == null) return null;
        JsonObject logo = GsonJsonUtils.getJsonObjectSafe(data, "logo");
        String logoUrl = logo == null ? null : GsonJsonUtils.getStringSafe(logo, "thumbnailUrl");
        return new ModItem(
                Constants.SOURCE_CURSEFORGE,
                contentType,
                GsonJsonUtils.getStringSafe(data, "id"),
                GsonJsonUtils.getStringSafe(data, "name"),
                GsonJsonUtils.getStringSafe(data, "summary"),
                logoUrl,
                extractCategories(data),
                extractLoaders(data)
        );
    }

    @Override
    public SearchCategory[] getCategories(SearchFilters searchFilters) {
        if (!supportsContentType(searchFilters.contentType)) {
            return new SearchCategory[0];
        }
        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MINECRAFT_GAME_ID);
        JsonObject response = mApiHandler.get("categories", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        if (data == null) {
            return new SearchCategory[0];
        }

        int rootId = getClassId(searchFilters);
        ArrayList<SearchCategory> categories = new ArrayList<>();
        for (JsonElement element : data) {
            JsonObject category = element.getAsJsonObject();
            if (GsonJsonUtils.getIntSafe(category, "parentCategoryId", -1) != rootId) {
                continue;
            }
            categories.add(new SearchCategory(
                    Constants.SOURCE_CURSEFORGE,
                    Integer.toString(GsonJsonUtils.getIntSafe(category, "id", 0)),
                    GsonJsonUtils.getStringSafe(category, "name")
            ));
        }
        return categories.toArray(new SearchCategory[0]);
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
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }

    @Override
    public ModLoader importModpack(Activity activity, Uri zipUri) throws IOException, NoSuchAlgorithmException {
        return ModpackInstaller.importModpack(activity, zipUri, this::installCurseforgeZip);
    }


    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, String modId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);

        JsonObject response = mApiHandler.get("mods/"+modId+"/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;

        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            if(fileInfo.get("isServerPack").getAsBoolean()) continue;
            objectList.add(fileInfo);
        }
        if(data.size() < CURSEFORGE_PAGINATION_SIZE) {
            return CURSEFORGE_PAGINATION_END_REACHED; // we read the remainder! yay!
        }
        return index + data.size();
    }

    private static int getClassId(SearchFilters searchFilters) {
        switch (searchFilters.contentType) {
            case SearchFilters.TYPE_MOD:
                return CURSEFORGE_MOD_CLASS_ID;
            case SearchFilters.TYPE_RESOURCEPACK:
                return CURSEFORGE_RESOURCE_PACK_CLASS_ID;
            case SearchFilters.TYPE_MODPACK:
            default:
                return CURSEFORGE_MODPACK_CLASS_ID;
        }
    }

    private static boolean supportsContentType(int contentType) {
        return contentType == SearchFilters.TYPE_MODPACK
                || contentType == SearchFilters.TYPE_MOD
                || contentType == SearchFilters.TYPE_RESOURCEPACK;
    }

    private static boolean matchesLoaderFilter(SearchFilters filters, String[] loaders) {
        return filters.loader == null
                || filters.loader.isEmpty()
                || loaders == null
                || loaders.length == 0
                || java.util.Arrays.asList(loaders).contains(filters.loader);
    }

    private static String[] extractCategories(JsonObject dataElement) {
        JsonArray categories = GsonJsonUtils.getJsonArraySafe(dataElement, "categories");
        if (categories == null) return new String[0];
        ArrayList<String> values = new ArrayList<>();
        for (JsonElement category : categories) {
            JsonObject object = category.getAsJsonObject();
            String name = GsonJsonUtils.getStringSafe(object, "name");
            if (Tools.isValidString(name)) values.add(name);
        }
        return values.toArray(new String[0]);
    }

    private static String[] extractLoaders(JsonObject dataElement) {
        LinkedHashSet<String> loaders = new LinkedHashSet<>();
        JsonArray latestFiles = GsonJsonUtils.getJsonArraySafe(dataElement, "latestFiles");
        if (latestFiles != null) {
            for (JsonElement latestFileElement : latestFiles) {
                JsonObject latestFile = latestFileElement.getAsJsonObject();
                JsonArray gameVersions = GsonJsonUtils.getJsonArraySafe(latestFile, "gameVersions");
                for (String loader : extractLoadersFromVersions(gameVersions)) {
                    loaders.add(loader);
                }
            }
        }
        return loaders.toArray(new String[0]);
    }

    private static String[] extractLoadersFromVersions(JsonArray gameVersions) {
        LinkedHashSet<String> loaders = new LinkedHashSet<>();
        if (gameVersions == null) return new String[0];
        for (JsonElement jsonElement : gameVersions) {
            String value = jsonElement.getAsString().toLowerCase(Locale.ROOT);
            if (value.contains("forge")) loaders.add("forge");
            else if (value.contains("fabric")) loaders.add("fabric");
            else if (value.contains("quilt")) loaders.add("quilt");
            else if (value.contains("neoforge")) loaders.add("neoforge");
        }
        return loaders.toArray(new String[0]);
    }

    private static ModDependency[] parseDependencies(JsonArray dependencies) {
        if (dependencies == null || dependencies.size() == 0) {
            return new ModDependency[0];
        }

        ArrayList<ModDependency> items = new ArrayList<>();
        for (JsonElement dependencyElement : dependencies) {
            JsonObject dependency = dependencyElement.getAsJsonObject();
            int relationType = GsonJsonUtils.getIntSafe(dependency, "relationType", 1);
            String projectId = Integer.toString(GsonJsonUtils.getIntSafe(dependency, "modId", 0));
            items.add(new ModDependency(projectId, projectId, relationType == 3));
        }
        return items.toArray(new ModDependency[0]);
    }

    private ModLoader installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            ModDownloader modDownloader = new ModDownloader(new File(instanceDestination,"mods"), true);
            int fileCount = curseManifest.files.length;
            for(int i = 0; i < fileCount; i++) {
                final CurseManifest.CurseFile curseFile = curseManifest.files[i];
                modDownloader.submitDownload(()->{
                    String url = getDownloadUrl(curseFile.projectID, curseFile.fileID);
                    if(url == null && curseFile.required)
                        throw new IOException("Failed to obtain download URL for "+curseFile.projectID+" "+curseFile.fileID);
                    else if(url == null) return null;
                    return new ModDownloader.FileInfo(url, FileUtils.getFileName(url), getDownloadSha1(curseFile.projectID, curseFile.fileID));
                });
            }
            modDownloader.awaitFinish((c,m)->
                    ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float)c/m*100,0), R.string.modpack_download_downloading_mods_fc, c, m)
            );
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    private ModLoader createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex+1);
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " "+modLoaderVersion);
        int modLoaderTypeInt;
        switch (modLoaderName) {
            case "forge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FORGE;
                break;
            case "fabric":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FABRIC;
                break;
            case "neoforge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_NEOFORGE;
                break;
            default:
                return null;
            //TODO: Quilt is also Forge? How does that work?
        }
        return new ModLoader(modLoaderTypeInt, modLoaderVersion, minecraft.version);
    }

    private String getDownloadUrl(long projectID, long fileID) {
        // First try the official api endpoint
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID+"/download-url", JsonObject.class);
        if (response != null && !response.get("data").isJsonNull())
            return response.get("data").getAsString();

        // Otherwise, fallback to building an edge link
        JsonObject fallbackResponse = mApiHandler.get(String.format("mods/%s/files/%s", projectID, fileID), JsonObject.class);
        if (fallbackResponse != null && !fallbackResponse.get("data").isJsonNull()){
            JsonObject modData = fallbackResponse.get("data").getAsJsonObject();
            int id = modData.get("id").getAsInt();
            return String.format("https://edge.forgecdn.net/files/%s/%s/%s", id/1000, id % 1000, modData.get("fileName").getAsString());
        }

        return null;
    }

    private @Nullable String getDownloadSha1(long projectID, long fileID) {
        // Try the api endpoint, die in the other case
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        JsonObject data = GsonJsonUtils.getJsonObjectSafe(response, "data");
        if(data == null) return null;
        return getSha1FromModData(data);
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }
}
