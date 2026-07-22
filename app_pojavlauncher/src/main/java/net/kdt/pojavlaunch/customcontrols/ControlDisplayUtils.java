package net.kdt.pojavlaunch.customcontrols;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import net.kdt.pojavlaunch.utils.LocaleUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ControlDisplayUtils {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_EMOJI = "emoji";
    public static final String TYPE_ICON = "icon";
    public static final String ICON_ASSET_DIR = "items_1.21.11";
    private static final String ICON_INDEX_ASSET = ICON_ASSET_DIR + "/icon_index.json";

    private static List<IconItem> sIconItems;

    private ControlDisplayUtils() {
    }

    public static String getType(ControlData data) {
        if (data == null || TextUtils.isEmpty(data.displayType)) return TYPE_TEXT;
        return data.displayType;
    }

    public static String getText(ControlData data) {
        if (data == null) return "";
        if (TYPE_TEXT.equals(getType(data)) && TextUtils.isEmpty(data.displayValue)) return safe(data.name);
        if (TYPE_ICON.equals(getType(data))) return safe(data.name);
        return TextUtils.isEmpty(data.displayValue) ? safe(data.name) : data.displayValue;
    }

    public static boolean isIcon(ControlData data) {
        return TYPE_ICON.equals(getType(data)) && !TextUtils.isEmpty(data.displayValue);
    }

    public static Drawable loadIcon(Context context, String iconName) {
        String assetName = normalizeIconAssetName(iconName);
        if (TextUtils.isEmpty(assetName)) return null;

        try (InputStream inputStream = context.getAssets().open(ICON_ASSET_DIR + "/" + assetName)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            return bitmap == null ? null : new BitmapDrawable(context.getResources(), bitmap);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static String[] listIconNames(Context context) throws IOException {
        List<IconItem> items = listIconItems(context);
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = items.get(i).iconName;
        }
        return names;
    }

    public static List<IconItem> listIconItems(Context context) throws IOException {
        if (sIconItems != null) return sIconItems;

        List<IconItem> items;
        try {
            items = readIconIndex(context);
        } catch (IOException | JSONException e) {
            items = readIconAssetsFallback(context);
        }

        Collections.sort(items, (left, right) -> left.getDisplayName(context).compareToIgnoreCase(right.getDisplayName(context)));
        sIconItems = Collections.unmodifiableList(items);
        return sIconItems;
    }

    public static List<IconItem> filterIconItems(Context context, List<IconItem> items, String query) {
        if (TextUtils.isEmpty(query)) return new ArrayList<>(items);

        String normalizedQuery = normalizeSearch(query);
        List<IconItem> filteredItems = new ArrayList<>();
        for (IconItem item : items) {
            if (item.matches(context, normalizedQuery)) {
                filteredItems.add(item);
            }
        }
        return filteredItems;
    }

    private static List<IconItem> readIconIndex(Context context) throws IOException, JSONException {
        JSONArray iconArray = new JSONArray(readAssetText(context, ICON_INDEX_ASSET));
        List<IconItem> items = new ArrayList<>(iconArray.length());
        for (int i = 0; i < iconArray.length(); i++) {
            JSONObject iconObject = iconArray.getJSONObject(i);
            Map<String, String> names = new LinkedHashMap<>();
            JSONObject namesObject = iconObject.optJSONObject("names");
            if (namesObject != null) {
                Iterator<String> keys = namesObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    names.put(key, namesObject.optString(key));
                }
            }

            String iconName = iconObject.optString("icon");
            if (!TextUtils.isEmpty(iconName)) {
                items.add(new IconItem(
                        iconName,
                        iconObject.optString("id"),
                        iconObject.optString("fullId"),
                        iconObject.optString("translationKey"),
                        names
                ));
            }
        }
        return items;
    }

    private static List<IconItem> readIconAssetsFallback(Context context) throws IOException {
        String[] assets = context.getAssets().list(ICON_ASSET_DIR);
        if (assets == null) return new ArrayList<>();

        Arrays.sort(assets);
        List<IconItem> items = new ArrayList<>(assets.length);
        for (String asset : assets) {
            if (asset.endsWith(".png")) {
                String iconName = asset.substring(0, asset.length() - 4);
                String id = iconName.startsWith("minecraft_") ? iconName.substring("minecraft_".length()) : iconName;
                Map<String, String> names = new LinkedHashMap<>();
                names.put("en_us", id.replace('_', ' '));
                items.add(new IconItem(iconName, id, id, "", names));
            }
        }
        return items;
    }

    private static String readAssetText(Context context, String assetName) throws IOException {
        try (InputStream inputStream = context.getAssets().open(assetName);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static int typeToIndex(String type) {
        if (TYPE_EMOJI.equals(type)) return 1;
        if (TYPE_ICON.equals(type)) return 2;
        return 0;
    }

    public static String indexToType(int index) {
        if (index == 1) return TYPE_EMOJI;
        if (index == 2) return TYPE_ICON;
        return TYPE_TEXT;
    }

    public static String normalizeIconAssetName(String iconName) {
        if (TextUtils.isEmpty(iconName)) return "";
        String cleanName = iconName.trim().replace('\\', '/');
        int slashIndex = cleanName.lastIndexOf('/');
        if (slashIndex >= 0) cleanName = cleanName.substring(slashIndex + 1);
        return cleanName.endsWith(".png") ? cleanName : cleanName + ".png";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String getLocaleKey(Context context) {
        Locale locale = LocaleUtils.getCurrentLocale(context);

        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        if ("es".equals(language)) return "es_es";
        if ("pt".equals(language) && TextUtils.isEmpty(country)) return "pt_br";
        if (!TextUtils.isEmpty(language) && !TextUtils.isEmpty(country)) {
            String localeKey = language + "_" + country;
            if ("zh_hk".equals(localeKey) || "zh_mo".equals(localeKey)) return "zh_tw";
            return localeKey;
        }

        switch (language) {
            case "es":
                return "es_es";
            case "pt":
                return "pt_br";
            case "fr":
                return "fr_fr";
            case "de":
                return "de_de";
            case "it":
                return "it_it";
            case "ru":
                return "ru_ru";
            case "zh":
                return "zh_cn";
            case "ja":
                return "ja_jp";
            case "ko":
                return "ko_kr";
            case "pl":
                return "pl_pl";
            case "nl":
                return "nl_nl";
            case "tr":
                return "tr_tr";
            case "uk":
                return "uk_ua";
            case "cs":
                return "cs_cz";
            default:
                return "en_us";
        }
    }

    private static String normalizeSearch(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    public static final class IconItem {
        public final String iconName;
        public final String id;
        public final String fullId;
        public final String translationKey;
        private final Map<String, String> names;
        private final Map<String, String> normalizedSearchCache = new LinkedHashMap<>();

        private IconItem(String iconName, String id, String fullId, String translationKey, Map<String, String> names) {
            this.iconName = safe(iconName);
            this.id = safe(id);
            this.fullId = safe(fullId);
            this.translationKey = safe(translationKey);
            this.names = names == null ? new LinkedHashMap<>() : names;
        }

        public String getDisplayName(Context context) {
            String localeName = names.get(getLocaleKey(context));
            if (!TextUtils.isEmpty(localeName)) return localeName;

            String language = getLocaleKey(context).split("_")[0];
            for (Map.Entry<String, String> entry : names.entrySet()) {
                if (entry.getKey().startsWith(language + "_") && !TextUtils.isEmpty(entry.getValue())) {
                    return entry.getValue();
                }
            }

            String englishName = names.get("en_us");
            if (!TextUtils.isEmpty(englishName)) return englishName;
            return TextUtils.isEmpty(id) ? iconName : id.replace('_', ' ');
        }

        private boolean matches(Context context, String normalizedQuery) {
            if (TextUtils.isEmpty(normalizedQuery)) return true;
            return getNormalizedSearchText(context).contains(normalizedQuery);
        }

        private String getNormalizedSearchText(Context context) {
            String localeKey = getLocaleKey(context);
            synchronized (normalizedSearchCache) {
                String cached = normalizedSearchCache.get(localeKey);
                if (cached != null) return cached;

                StringBuilder builder = new StringBuilder();
                appendSearchPart(builder, iconName);
                appendSearchPart(builder, id);
                appendSearchPart(builder, fullId);
                appendSearchPart(builder, translationKey);
                appendSearchPart(builder, getDisplayName(context));
                for (String name : names.values()) {
                    appendSearchPart(builder, name);
                }
                String normalized = normalizeSearch(builder.toString());
                normalizedSearchCache.put(localeKey, normalized);
                return normalized;
            }
        }

        private static void appendSearchPart(StringBuilder builder, String value) {
            if (TextUtils.isEmpty(value)) return;
            if (builder.length() > 0) builder.append(' ');
            builder.append(value);
        }
    }
}
