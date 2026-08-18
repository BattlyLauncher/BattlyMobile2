package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import fr.spse.gamepad_remapper.Remapper;

/** Keeps Battly's setup assistant and the runtime remapper on the same axis map. */
public final class ControllerRemapperBridge {
    private static final String TAG = "BattlyGamepad";

    private static final int[] CANONICAL_AXES = {
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER
    };

    private static final int[] STANDARD_KEYS = {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
    };

    private ControllerRemapperBridge() {
    }

    /**
     * Creates or repairs the official remapper profile before RemapperManager caches it.
     * Button mappings are preserved. Only canonical controller axes are synchronized.
     */
    public static void ensureProfile(Context context, InputDevice device) {
        if (context == null || device == null || device.getDescriptor() == null) return;

        String descriptor = device.getDescriptor();
        SharedPreferences preferences = context.getSharedPreferences(
                Remapper.SHARED_PREFERENCE_KEY, Context.MODE_PRIVATE);
        String existing = preferences.getString(descriptor, "");

        Map<Integer, Integer> keyMap = new LinkedHashMap<>();
        Map<Integer, Integer> motionMap = new LinkedHashMap<>();
        readProfile(existing, keyMap, motionMap);
        if (keyMap.isEmpty()) {
            for (int key : STANDARD_KEYS) keyMap.put(key, key);
        }

        int[] right = ControllerTypeResolver.resolveRightStickAxes(device);
        int[] defaults = {
                MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                right[0], right[1],
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
                MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER
        };
        int[] physicalAxes = ControllerAxisPreferences.load(
                context, device, CANONICAL_AXES, defaults);
        mergeMotionMap(motionMap, physicalAxes, CANONICAL_AXES);

        Remapper repaired = new Remapper(keyMap, motionMap);
        repaired.save(context, descriptor);
        Log.i(TAG, "Runtime remapper ready for " + device.getName()
                + ": axes=" + motionMap);
    }

    static void mergeMotionMap(Map<Integer, Integer> motionMap, int[] physicalAxes,
                               int[] canonicalAxes) {
        if (motionMap == null || physicalAxes == null || canonicalAxes == null
                || physicalAxes.length != canonicalAxes.length) return;

        for (Iterator<Map.Entry<Integer, Integer>> iterator = motionMap.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            if (contains(canonicalAxes, entry.getValue())
                    || contains(physicalAxes, entry.getKey())) {
                iterator.remove();
            }
        }
        for (int i = 0; i < physicalAxes.length; i++) {
            if (physicalAxes[i] >= 0) motionMap.put(physicalAxes[i], canonicalAxes[i]);
        }
    }

    private static void readProfile(String json, Map<Integer, Integer> keyMap,
                                    Map<Integer, Integer> motionMap) {
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(json);
            readMap(root.optJSONObject("keyMap"), keyMap);
            readMap(root.optJSONObject("motionMap"), motionMap);
        } catch (Exception error) {
            Log.w(TAG, "Discarding invalid controller remapper profile", error);
        }
    }

    private static void readMap(JSONObject source, Map<Integer, Integer> output) {
        if (source == null) return;
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                output.put(Integer.parseInt(key), source.getInt(key));
            } catch (Exception ignored) {
                // Ignore only the malformed entry and retain the rest of the profile.
            }
        }
    }

    private static boolean contains(int[] values, int candidate) {
        for (int value : values) {
            if (value == candidate) return true;
        }
        return false;
    }
}
