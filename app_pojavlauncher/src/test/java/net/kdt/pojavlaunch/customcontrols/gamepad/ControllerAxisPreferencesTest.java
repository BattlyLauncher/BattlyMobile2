package net.kdt.pojavlaunch.customcontrols.gamepad;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;

import org.junit.Test;

public class ControllerAxisPreferencesTest {
    private static final int[] DEFAULTS = {
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ
    };

    @Test
    public void resolvesPhysicalAxesFromAssistantMapping() {
        String json = "{\"schema\":3,\"motionMap\":{\"0\":0,\"1\":1,\"12\":11,\"13\":14}}";

        assertArrayEquals(new int[]{0, 1, 12, 13},
                ControllerAxisPreferences.resolve(json, DEFAULTS));
    }

    @Test
    public void missingOrDuplicateAxesFallBackSafely() {
        String json = "{\"schema\":3,\"motionMap\":{\"0\":0,\"12\":11,\"13\":14}}";

        assertArrayEquals(new int[]{0, 1, 12, 13},
                ControllerAxisPreferences.resolve(json, DEFAULTS));
    }

    @Test
    public void invalidMappingUsesAndroidDefaults() {
        assertArrayEquals(DEFAULTS, ControllerAxisPreferences.resolve("not-json", DEFAULTS));
    }

    @Test
    public void physicalAxisCanOnlyDriveOneLogicalControl() {
        int[] targets = {
                MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
        };
        int[] defaults = targets.clone();
        String json = "{\"schema\":3,\"motionMap\":{\"0\":0,\"1\":1,\"12\":11,\"13\":14}}";

        assertArrayEquals(new int[]{0, 1, 12, 13,
                        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y},
                ControllerAxisPreferences.resolve(json, targets, defaults));
    }

    @Test
    public void legacyAssistantMapIsResetInsteadOfDisablingSticks() {
        String legacy = "{\"motionMap\":{\"15\":0,\"16\":1,\"12\":11,\"13\":14}}";

        assertArrayEquals(DEFAULTS, ControllerAxisPreferences.resolve(legacy, DEFAULTS));
    }

    @Test
    public void invalidStickPairFallsBackToDefaults() {
        String json = "{\"schema\":3,\"motionMap\":{\"11\":0,\"14\":1,\"15\":11,\"16\":14}}";

        assertArrayEquals(DEFAULTS, ControllerAxisPreferences.resolve(json, DEFAULTS));
    }

    @Test
    public void unavailableSavedAxesFallBackToDeviceDefaults() {
        int[] saved = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_RX, MotionEvent.AXIS_RY};

        assertArrayEquals(DEFAULTS, ControllerAxisPreferences.validateForAvailableAxes(
                saved, DEFAULTS, true, false));
    }

    @Test
    public void serializesPhysicalAxesWithoutUsingTheButtonRemapperNamespace() throws Exception {
        java.util.Map<Integer, Integer> axes = new java.util.LinkedHashMap<>();
        axes.put(MotionEvent.AXIS_X, MotionEvent.AXIS_X);
        axes.put(MotionEvent.AXIS_Y, MotionEvent.AXIS_Y);
        axes.put(MotionEvent.AXIS_RX, MotionEvent.AXIS_Z);
        axes.put(MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ);

        String json = ControllerAxisPreferences.serialize(axes);

        assertArrayEquals(new int[]{MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                        MotionEvent.AXIS_RX, MotionEvent.AXIS_RY},
                ControllerAxisPreferences.resolve(json, DEFAULTS));
        assertEquals(4, new org.json.JSONObject(json)
                .getJSONObject("motionMap").length());
        assertEquals(3, new org.json.JSONObject(json).getInt("schema"));
    }
}
