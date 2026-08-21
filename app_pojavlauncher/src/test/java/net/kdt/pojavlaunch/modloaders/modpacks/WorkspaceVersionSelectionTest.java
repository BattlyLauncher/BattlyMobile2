package net.kdt.pojavlaunch.modloaders.modpacks;

import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class WorkspaceVersionSelectionTest {
    @Test
    public void selectionNarrowsLoaderMinecraftAndReleaseWithoutLosingApiIndices() {
        ModDetail detail = detail();

        assertEquals(Arrays.asList("fabric", "forge"), WorkspaceVersionSelection.collectLoaders(detail));
        assertEquals(Arrays.asList("1.21.4", "1.20.1"),
                WorkspaceVersionSelection.collectMinecraftVersions(detail, "fabric"));
        assertEquals(Arrays.asList(0, 2),
                WorkspaceVersionSelection.collectReleaseIndices(detail, "fabric", "1.21.4"));
        assertEquals(Arrays.asList(1),
                WorkspaceVersionSelection.collectReleaseIndices(detail, "forge", "1.20.1"));
    }

    @Test
    public void searchIsCaseInsensitiveAndReturnsVisiblePositions() {
        List<String> labels = Arrays.asList("2.4.0 (Recommended)", "2.3.1", "Legacy 1.9");
        assertEquals(Arrays.asList(0, 1), WorkspaceVersionSelection.filterPositions(labels, "2."));
        assertEquals(Arrays.asList(2), WorkspaceVersionSelection.filterPositions(labels, "LEGACY"));
    }

    @Test
    public void loaderIndependentContentKeepsEveryCompatibleMinecraftRelease() {
        ModItem item = new ModItem(0, 0, "shader", "Shader", "Description", null);
        ModDetail detail = new ModDetail(item,
                new String[]{"Shader current", "Shader legacy"},
                new String[]{"1.21.4", "1.20.1"},
                new String[][]{{"1.21.4", "1.21.1"}, {"1.20.1"}},
                new String[]{"url0", "url1"},
                new String[]{"current.zip", "legacy.zip"},
                new String[]{null, null},
                new String[][]{null, null},
                null);

        assertEquals(Arrays.asList(), WorkspaceVersionSelection.collectLoaders(detail));
        assertEquals(Arrays.asList("1.21.4", "1.21.1", "1.20.1"),
                WorkspaceVersionSelection.collectMinecraftVersions(detail, null));
        assertEquals(Arrays.asList(0),
                WorkspaceVersionSelection.collectReleaseIndices(detail, null, "1.21.1"));
    }

    private static ModDetail detail() {
        ModItem item = new ModItem(0, 0, "id", "Title", "Description", null);
        return new ModDetail(item,
                new String[]{"Fabric current", "Forge stable", "Fabric older"},
                new String[]{"1.21.4", "1.20.1", "1.21.4"},
                new String[][]{{"1.21.4", "1.20.1"}, {"1.20.1"}, {"1.21.4"}},
                new String[]{"url0", "url1", "url2"},
                new String[]{"a.jar", "b.jar", "c.jar"},
                new String[]{null, null, null},
                new String[][]{{"fabric"}, {"forge"}, {"fabric"}},
                null);
    }
}
