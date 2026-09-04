package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.NonNull;

public class ModItem extends ModSource {

    public String id;
    public String title;
    public String description;
    public String imageUrl;
    public String previewImageUrl;
    public String[] previewImageUrls;
    public String[] galleryImageUrls;
    public String[] categories;
    public String[] loaders;
    public long downloadCount;
    public long followCount;

    public ModItem(int apiSource, int contentType, String id, String title, String description, String imageUrl) {
        this(apiSource, contentType, id, title, description, imageUrl, null, null, 0, 0);
    }

    public ModItem(int apiSource, int contentType, String id, String title, String description, String imageUrl, String[] categories, String[] loaders) {
        this(apiSource, contentType, id, title, description, imageUrl, categories, loaders, 0, 0);
    }

    public ModItem(int apiSource, int contentType, String id, String title, String description, String imageUrl, String[] categories, String[] loaders, long downloadCount, long followCount) {
        this(apiSource, contentType, id, title, description, imageUrl, categories, loaders,
                downloadCount, followCount, new String[0]);
    }

    public ModItem(int apiSource, int contentType, String id, String title, String description,
                   String imageUrl, String[] categories, String[] loaders, long downloadCount,
                   long followCount, String[] previewImageUrls) {
        this.apiSource = apiSource;
        this.contentType = contentType;
        this.isModpack = contentType == SearchFilters.TYPE_MODPACK;
        this.id = id;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        setPreviewImages(previewImageUrls);
        this.categories = categories;
        this.loaders = loaders;
        this.downloadCount = downloadCount;
        this.followCount = followCount;
    }

    public void setPreviewImages(String[] previewImageUrls) {
        this.previewImageUrls = previewImageUrls == null ? new String[0] : previewImageUrls;
        this.previewImageUrl = this.previewImageUrls.length == 0 ? null : this.previewImageUrls[0];
        this.galleryImageUrls = this.previewImageUrls;
    }

    public void setGalleryImages(String[] previewImageUrls, String[] galleryImageUrls) {
        setPreviewImages(previewImageUrls);
        this.galleryImageUrls = galleryImageUrls == null || galleryImageUrls.length == 0
                ? this.previewImageUrls
                : galleryImageUrls;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", previewImageUrl='" + previewImageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", contentType=" + contentType +
                ", isModpack=" + isModpack +
                ", downloadCount=" + downloadCount +
                ", followCount=" + followCount +
                '}';
    }

    public String getIconCacheTag() {
        return apiSource+"_"+id;
    }
}
