package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

/** Reads the physical stick axes saved by the controller setup assistant. */
final class ControllerAxisPreferences {
    private static final String AXIS_PREFERENCES = "battly_controller_axes";
    private static final int SCHEMA_VERSION = 3;

    private ControllerAxisPreferences() {
    }

    static int[] load(Context context, String descriptor, int[] defaults) {
        if (context == null || descriptor == null || descriptor.isEmpty()) return defaults.clone();
        String json = context.getSharedPreferences(AXIS_PREFERENCES, Context.MODE_PRIVATE)
                .getString(descriptor, "");
        return resolve(json, defaults);
    }

    static int[] load(Context context, String descriptor, int[] targets, int[] defaults) {
        if (context == null || descriptor == null || descriptor.isEmpty()) return defaults.clone();
        String json = context.getSharedPreferences(AXIS_PREFERENCES, Context.MODE_PRIVATE)
                .getString(descriptor, "");
        // Do not read axes from the legacy button remapper. Android key codes and MotionEvent
        // axes overlap numerically, which is the source of sticks opening chat/F5 on old maps.
        return resolve(json, targets, defaults);
    }

    static int[] load(Context context, InputDevice device, int[] targets, int[] defaults) {
        if (device == null) return defaults.clone();
        int[] resolved = load(context, device.getDescriptor(), targets, defaults);
        return validateForDevice(resolved, defaults, device);
    }

    static void save(Context context, String descriptor, Map<Integer, Integer> axes) {
        if (context == null || descriptor == null || descriptor.isEmpty()) return;
        context.getSharedPreferences(AXIS_PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(descriptor, serialize(axes)).apply();
    }

    static String serialize(Map<Integer, Integer> axes) {
        JSONObject motionMap = new JSONObject();
        JSONObject root = new JSONObject();
        try {
            if (axes != null) {
                for (Map.Entry<Integer, Integer> entry : axes.entrySet()) {
                    motionMap.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            root.put("schema", SCHEMA_VERSION);
            root.put("motionMap", motionMap);
        } catch (Exception ignored) {
            return "{\"schema\":" + SCHEMA_VERSION + ",\"motionMap\":{}}";
        }
        return root.toString();
    }

    static int[] resolve(String json, int[] defaults) {
        int[] targets = {
                android.view.MotionEvent.AXIS_X,
                android.view.MotionEvent.AXIS_Y,
                android.view.MotionEvent.AXIS_Z,
                android.view.MotionEvent.AXIS_RZ
        };
        return resolve(json, targets, defaults);
    }

    static int[] resolve(String json, int[] targets, int[] defaults) {
        int[] resolved = defaults.clone();
        if (json == null || json.isEmpty() || targets.length != defaults.length) return resolved;
        try {
            JSONObject root = new JSONObject(json);
            // Older maps either mixed Android key codes with motion axes or selected the right
            // stick from the controller's visual style instead of its physical MotionRanges.
            // Reset those maps once so existing users receive the corrected device defaults.
            if (root.optInt("schema", 0) != SCHEMA_VERSION) return resolved;
            JSONObject motionMap = root.optJSONObject("motionMap");
            if (motionMap == null) return resolved;
            for (int i = 0; i < targets.length; i++) {
                int source = findSourceAxis(motionMap, targets[i]);
                int candidate = source >= 0 ? source : defaults[i];
                // One physical axis cannot drive both a stick and a D-pad/trigger action.
                resolved[i] = contains(resolved, candidate, i) ? -1 : candidate;
            }
            sanitizeStickPairs(resolved, defaults);
        } catch (Exception ignored) {
            return defaults.clone();
        }
        return resolved;
    }

    static int[] validateForAvailableAxes(int[] resolved, int[] defaults,
                                          boolean leftAvailable, boolean rightAvailable) {
        int[] validated = resolved.clone();
        if (!leftAvailable) {
            validated[0] = defaults[0];
            validated[1] = defaults[1];
        }
        if (!rightAvailable) {
            validated[2] = defaults[2];
            validated[3] = defaults[3];
        }
        sanitizeStickPairs(validated, defaults);
        return validated;
    }

    private static int[] validateForDevice(int[] resolved, int[] defaults, InputDevice device) {
        boolean leftAvailable = hasAxis(device, resolved[0]) && hasAxis(device, resolved[1]);
        boolean rightAvailable = hasAxis(device, resolved[2]) && hasAxis(device, resolved[3]);
        return validateForAvailableAxes(resolved, defaults, leftAvailable, rightAvailable);
    }

    private static boolean hasAxis(InputDevice device, int axis) {
        if (axis < 0) return false;
        return device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null
                || device.getMotionRange(axis) != null;
    }

    private static void sanitizeStickPairs(int[] resolved, int[] defaults) {
        if (resolved.length < 4 || defaults.length < 4) return;
        // Android's controller contract reserves X/Y for the primary (left) stick. Accepting a
        // different pair here is what allowed Z/RZ or HAT axes to replace movement.
        if (resolved[0] != MotionEvent.AXIS_X || resolved[1] != MotionEvent.AXIS_Y) {
            resolved[0] = defaults[0];
            resolved[1] = defaults[1];
        }
        if (!isRightStickPair(resolved[2], resolved[3])) {
            resolved[2] = defaults[2];
            resolved[3] = defaults[3];
        }
    }

    private static boolean isRightStickPair(int horizontal, int vertical) {
        return horizontal == MotionEvent.AXIS_Z && vertical == MotionEvent.AXIS_RZ
                || horizontal == MotionEvent.AXIS_RX && vertical == MotionEvent.AXIS_RY;
    }

    private static int findSourceAxis(JSONObject motionMap, int targetAxis) {
        Iterator<String> keys = motionMap.keys();
        while (keys.hasNext()) {
            String source = keys.next();
            if (motionMap.optInt(source, Integer.MIN_VALUE) != targetAxis) continue;
            try {
                return Integer.parseInt(source);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static boolean contains(int[] values, int candidate, int beforeIndex) {
        for (int i = 0; i < beforeIndex; i++) {
            if (values[i] == candidate) return true;
        }
        return false;
    }
}
