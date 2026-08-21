package net.kdt.pojavlaunch.modloaders.modpacks;

import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class WorkspaceVersionSelection {
    private WorkspaceVersionSelection() {
    }

    static List<String> collectLoaders(ModDetail detail) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (detail == null || detail.versionLoaders == null) return new ArrayList<>();
        for (String[] loaders : detail.versionLoaders) {
            if (loaders == null) continue;
            for (String loader : loaders) {
                if (isValid(loader)) result.add(loader.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(result);
    }

    static List<String> collectMinecraftVersions(ModDetail detail, String selectedLoader) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (detail == null || detail.versionNames == null) return new ArrayList<>();
        for (int i = 0; i < detail.versionNames.length; i++) {
            if (!matchesLoader(detail, i, selectedLoader)) continue;
            for (String version : detail.getGameVersions(i)) {
                if (isValid(version)) result.add(version);
            }
        }
        ArrayList<String> sorted = new ArrayList<>(result);
        sorted.sort(WorkspaceVersionSelection::compareVersionDescending);
        return sorted;
    }

    static List<Integer> collectReleaseIndices(ModDetail detail, String selectedLoader,
                                               String selectedMinecraftVersion) {
        ArrayList<Integer> result = new ArrayList<>();
        if (detail == null || detail.versionNames == null) return result;
        for (int i = 0; i < detail.versionNames.length; i++) {
            if (matchesLoader(detail, i, selectedLoader)
                    && detail.supportsMinecraftVersion(i, selectedMinecraftVersion)) {
                result.add(i);
            }
        }
        return result;
    }

    static List<Integer> filterPositions(List<String> labels, String query) {
        ArrayList<Integer> result = new ArrayList<>();
        if (labels == null) return result;
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            if (normalized.isEmpty() || (label != null
                    && label.toLowerCase(Locale.ROOT).contains(normalized))) {
                result.add(i);
            }
        }
        return result;
    }

    private static boolean matchesLoader(ModDetail detail, int versionIndex, String selectedLoader) {
        if (!isValid(selectedLoader)) return true;
        if (detail.versionLoaders == null || versionIndex >= detail.versionLoaders.length) return false;
        String[] loaders = detail.versionLoaders[versionIndex];
        if (loaders == null) return false;
        for (String loader : loaders) {
            if (selectedLoader.equalsIgnoreCase(loader)) return true;
        }
        return false;
    }

    private static int compareVersionDescending(String left, String right) {
        if (left == null) return right == null ? 0 : 1;
        if (right == null) return -1;
        String[] leftParts = left.split("[^0-9]+");
        String[] rightParts = right.split("[^0-9]+");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int leftValue = parsePart(leftParts, i);
            int rightValue = parsePart(rightParts, i);
            if (leftValue != rightValue) return Integer.compare(rightValue, leftValue);
        }
        return right.compareToIgnoreCase(left);
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
