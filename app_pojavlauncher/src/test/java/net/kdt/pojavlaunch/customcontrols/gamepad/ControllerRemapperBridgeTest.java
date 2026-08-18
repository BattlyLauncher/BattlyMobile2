package net.kdt.pojavlaunch.customcontrols.gamepad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.view.MotionEvent;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class ControllerRemapperBridgeTest {
    @Test
    public void mergeMotionMapReplacesStaleStickToDpadMappings() {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        map.put(MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X);
        map.put(MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y);
        map.put(MotionEvent.AXIS_RX, MotionEvent.AXIS_Z);
        map.put(MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ);

        int[] physical = {
                MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
        };
        int[] canonical = {
                MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
        };

        ControllerRemapperBridge.mergeMotionMap(map, physical, canonical);

        for (int axis : canonical) assertEquals(Integer.valueOf(axis), map.get(axis));
        assertFalse(map.containsKey(MotionEvent.AXIS_RX));
        assertFalse(map.containsKey(MotionEvent.AXIS_RY));
    }

    @Test
    public void mergeMotionMapSupportsDualSenseStandardAxes() {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        int[] physical = {
                MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
                MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER
        };

        ControllerRemapperBridge.mergeMotionMap(map, physical, physical);

        assertEquals(8, map.size());
        for (int axis : physical) assertEquals(Integer.valueOf(axis), map.get(axis));
    }
}
