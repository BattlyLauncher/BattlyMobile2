package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class BattlyNativeAdHelperTest {
    @Test
    public void createsTwoStableFullWidthPositionsForCatalog() {
        List<Integer> first = BattlyNativeAdHelper.randomAdPositions(20, 2, 42L);
        List<Integer> second = BattlyNativeAdHelper.randomAdPositions(20, 2, 42L);

        assertEquals(first, second);
        assertEquals(2, first.size());
        assertTrue(first.get(0) >= 2);
        assertTrue(first.get(0) < first.get(1));
        assertTrue(first.get(1) <= 21);
    }

    @Test
    public void doesNotCreateAdsForEmptyCatalog() {
        assertTrue(BattlyNativeAdHelper.randomAdPositions(0, 2, 42L).isEmpty());
    }
}
