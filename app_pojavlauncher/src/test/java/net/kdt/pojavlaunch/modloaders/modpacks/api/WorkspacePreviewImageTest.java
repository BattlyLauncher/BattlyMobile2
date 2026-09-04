package net.kdt.pojavlaunch.modloaders.modpacks.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertArrayEquals;

import com.google.gson.JsonParser;

import org.junit.Test;

public class WorkspacePreviewImageTest {
    @Test
    public void modrinthSearchGalleryUsesFirstUrl() {
        assertEquals("https://cdn.example/first.webp", ModrinthApi.firstGalleryImage(
                JsonParser.parseString("[\"https://cdn.example/first.webp\",\"https://cdn.example/second.webp\"]")
                        .getAsJsonArray()));
    }

    @Test
    public void modrinthProjectGalleryPrefersFeaturedImage() {
        assertEquals("https://cdn.example/featured.webp", ModrinthApi.firstGalleryImage(
                JsonParser.parseString("[{\"url\":\"https://cdn.example/first.webp\",\"featured\":false},"
                                + "{\"url\":\"https://cdn.example/featured.webp\",\"featured\":true}]")
                        .getAsJsonArray()));
    }

    @Test
    public void modrinthProjectGalleryKeepsEveryImageWithFeaturedFirst() {
        assertArrayEquals(new String[]{
                        "https://cdn.example/featured.webp",
                        "https://cdn.example/first.webp",
                        "https://cdn.example/third.webp"
                }, ModrinthApi.galleryImages(JsonParser.parseString(
                        "[{\"url\":\"https://cdn.example/first.webp\",\"featured\":false},"
                                + "{\"url\":\"https://cdn.example/featured.webp\",\"featured\":true},"
                                + "{\"url\":\"https://cdn.example/third.webp\",\"featured\":false}]")
                        .getAsJsonArray()));
    }

    @Test
    public void modrinthFullGalleryUsesRawImagesAndKeepsFeaturedFirst() {
        assertArrayEquals(new String[]{"featured.png", "first.png"},
                ModrinthApi.galleryRawImages(JsonParser.parseString(
                        "[{\"url\":\"first.webp\",\"raw_url\":\"first.png\",\"featured\":false},"
                                + "{\"url\":\"featured.webp\",\"raw_url\":\"featured.png\",\"featured\":true}]")
                        .getAsJsonArray()));
    }

    @Test
    public void curseforgeScreenshotUsesSizedThumbnail() {
        assertEquals("https://cdn.example/thumb.jpg", CurseforgeApi.firstScreenshotImage(
                JsonParser.parseString("[{\"url\":\"https://cdn.example/full.jpg\","
                                + "\"thumbnailUrl\":\"https://cdn.example/thumb.jpg\"}]")
                        .getAsJsonArray()));
    }

    @Test
    public void curseforgeGalleryKeepsEveryScreenshot() {
        assertArrayEquals(new String[]{"one-thumb.jpg", "two.jpg"},
                CurseforgeApi.screenshotImages(JsonParser.parseString(
                        "[{\"url\":\"one.jpg\",\"thumbnailUrl\":\"one-thumb.jpg\"},"
                                + "{\"url\":\"two.jpg\"}]").getAsJsonArray()));
    }

    @Test
    public void curseforgeFullGalleryUsesOriginalImages() {
        assertArrayEquals(new String[]{"one.jpg", "two.jpg"},
                CurseforgeApi.screenshotFullImages(JsonParser.parseString(
                        "[{\"url\":\"one.jpg\",\"thumbnailUrl\":\"one-thumb.jpg\"},"
                                + "{\"url\":\"two.jpg\",\"thumbnailUrl\":\"two-thumb.jpg\"}]")
                        .getAsJsonArray()));
    }

    @Test
    public void missingGalleryHasNoPreview() {
        assertNull(ModrinthApi.firstGalleryImage(null));
        assertNull(CurseforgeApi.firstScreenshotImage(null));
    }
}
