package net.kdt.pojavlaunch.battlyworlds;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BattlyWorldsPreferencesTest {
    @Test
    public void freeDurationCannotExceedSixHours() {
        assertEquals(6, BattlyWorldsPreferences.clampDuration(72, 6));
    }

    @Test
    public void plusDurationKeepsRequestedValueWithinLimit() {
        assertEquals(24, BattlyWorldsPreferences.clampDuration(24, 72));
    }

    @Test
    public void durationIsNeverBelowOneHour() {
        assertEquals(1, BattlyWorldsPreferences.clampDuration(0, 6));
    }

    @Test
    public void durationOptionsIncludeAccountSpecificMaximum() {
        assertArrayEquals(new int[]{1, 3, 6, 10}, BattlyWorldsPreferences.durationOptions(10));
    }
}
