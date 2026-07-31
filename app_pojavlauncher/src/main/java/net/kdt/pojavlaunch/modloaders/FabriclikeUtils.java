package net.kdt.pojavlaunch.modloaders;

import com.google.gson.JsonSyntaxException;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;

public class FabriclikeUtils {

    public static final FabriclikeUtils FABRIC_UTILS = new FabriclikeUtils("https://meta.fabricmc.net/v2", "fabric", "Fabric", "fabric");
    public static final FabriclikeUtils QUILT_UTILS = new FabriclikeUtils("https://meta.quiltmc.org/v3", "quilt", "Quilt", "quilt");
    public static final FabriclikeUtils LEGACY_FABRIC_UTILS = new FabriclikeUtils(
            "https://meta.legacyfabric.net/v2",
            "legacyfabric",
            "LegacyFabric",
            "legacyfabric",
            true
    );

    private static final String LOADER_METADATA_URL = "%s/versions/loader/%s";
    private static final String GAME_METADATA_URL = "%s/versions/game";

    private static final String JSON_DOWNLOAD_URL = "%s/versions/loader/%s/%s/profile/json";

    private final String mApiUrl;
    private final String mCachePrefix;
    private final String mName;
    private final String mIconName;
    private final boolean mLegacyMetadata;

    private FabriclikeUtils(String mApiUrl, String cachePrefix, String mName, String iconName) {
        this(mApiUrl, cachePrefix, mName, iconName, false);
    }

    private FabriclikeUtils(String mApiUrl, String cachePrefix, String mName, String iconName, boolean legacyMetadata) {
        this.mApiUrl = mApiUrl;
        this.mCachePrefix = cachePrefix;
        this.mIconName = iconName;
        this.mName = mName;
        this.mLegacyMetadata = legacyMetadata;
    }

    public FabricVersion[] downloadGameVersions() throws IOException{
        try {
            if (mLegacyMetadata) {
                return DownloadUtils.downloadStringCached(String.format("%s/versions", mApiUrl), mCachePrefix + "_versions",
                        input -> deserializeLegacyMetadata(input, "game"));
            }
            return DownloadUtils.downloadStringCached(String.format(GAME_METADATA_URL, mApiUrl), mCachePrefix+"_game_versions",
                    FabriclikeUtils::deserializeRawVersions
            );
        }catch (DownloadUtils.ParseException ignored) {}
        return null;
    }

    public FabricVersion[] downloadLoaderVersions(String gameVersion) throws IOException{
        try {
            if (mLegacyMetadata) {
                return DownloadUtils.downloadStringCached(String.format("%s/versions", mApiUrl), mCachePrefix + "_versions",
                        input -> deserializeLegacyMetadata(input, "loader"));
            }
            String urlEncodedGameVersion = URLEncoder.encode(gameVersion, "UTF-8");
            return DownloadUtils.downloadStringCached(String.format(LOADER_METADATA_URL, mApiUrl, urlEncodedGameVersion),
                    mCachePrefix+"_loader_versions."+urlEncodedGameVersion,
                    (input)->{ try {
                        return deserializeLoaderVersions(input);
                    }catch (JSONException e) {
                        throw new DownloadUtils.ParseException(e);
                    }});

        }catch (DownloadUtils.ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String createJsonDownloadUrl(String gameVersion, String loaderVersion) {
        try {
            gameVersion = URLEncoder.encode(gameVersion, "UTF-8");
            loaderVersion = URLEncoder.encode(loaderVersion, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return String.format(JSON_DOWNLOAD_URL, mApiUrl, gameVersion, loaderVersion);
    }

    public String getName() {
        return mName;
    }
    public String getIconName() {
        return mIconName;
    }

    private static FabricVersion[] deserializeLoaderVersions(String input) throws JSONException {
        JSONArray jsonArray = new JSONArray(input);
        FabricVersion[] fabricVersions = new FabricVersion[jsonArray.length()];
        for(int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i).getJSONObject("loader");
            FabricVersion fabricVersion = new FabricVersion();
            fabricVersion.version = jsonObject.getString("version");
            //Quilt has a skill issue and does not say which versions are stable or not
            if(jsonObject.has("stable")) {
                fabricVersion.stable = jsonObject.getBoolean("stable");
            } else {
                fabricVersion.stable = !fabricVersion.version.contains("beta");
            }
            fabricVersions[i] = fabricVersion;
        }
        Arrays.sort(fabricVersions, (left, right) ->
                compareVersionDescending(left.version, right.version));
        return fabricVersions;
    }

    static int compareVersionDescending(String left, String right) {
        return compareVersion(right, left);
    }

    private static int compareVersion(String left, String right) {
        String[] leftParts = left.split("[.+-]");
        String[] rightParts = right.split("[.+-]");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            if (i >= leftParts.length) return isPreReleasePart(rightParts[i]) ? 1 : -1;
            if (i >= rightParts.length) return isPreReleasePart(leftParts[i]) ? -1 : 1;
            String leftPart = leftParts[i];
            String rightPart = rightParts[i];
            boolean leftNumeric = leftPart.matches("\\d+");
            boolean rightNumeric = rightPart.matches("\\d+");
            int result;
            if (leftNumeric && rightNumeric) {
                result = Integer.compare(Integer.parseInt(leftPart), Integer.parseInt(rightPart));
            } else if (leftNumeric != rightNumeric) {
                result = leftNumeric ? 1 : -1;
            } else {
                result = leftPart.compareToIgnoreCase(rightPart);
            }
            if (result != 0) return result;
        }
        return 0;
    }

    private static boolean isPreReleasePart(String part) {
        String lower = part.toLowerCase();
        return lower.startsWith("alpha") || lower.startsWith("beta")
                || lower.startsWith("pre") || lower.startsWith("rc");
    }

    private static FabricVersion[] deserializeRawVersions(String jsonArrayIn) throws DownloadUtils.ParseException {
        try {
            return Tools.GLOBAL_GSON.fromJson(jsonArrayIn, FabricVersion[].class);
        }catch (JsonSyntaxException e) {
            e.printStackTrace();
            throw new DownloadUtils.ParseException(null);
        }
    }

    private static FabricVersion[] deserializeLegacyMetadata(String input, String key) throws DownloadUtils.ParseException {
        try {
            JSONObject root = new JSONObject(input);
            JSONArray versions = root.getJSONArray(key);
            FabricVersion[] fabricVersions = new FabricVersion[versions.length()];
            for (int i = 0; i < versions.length(); i++) {
                JSONObject jsonObject = versions.getJSONObject(i);
                FabricVersion fabricVersion = new FabricVersion();
                fabricVersion.version = jsonObject.getString("version");
                fabricVersion.stable = jsonObject.optBoolean("stable", !fabricVersion.version.toLowerCase().contains("beta"));
                fabricVersions[i] = fabricVersion;
            }
            return fabricVersions;
        } catch (JSONException e) {
            throw new DownloadUtils.ParseException(e);
        }
    }
}
