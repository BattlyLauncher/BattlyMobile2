package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonObject;

import net.burningtnt.terracotta.TerracottaAndroidAPI;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BattlyWorldsManager {
    private static final String TAG = "BattlyWorldsManager";
    public static final int REQUEST_VPN_PERMISSION = 7201;

    public enum Mode {
        HOST,
        GUEST
    }

    public interface StateListener {
        void onStateChanged(JsonObject state);
    }

    private static final CopyOnWriteArrayList<StateListener> sListeners = new CopyOnWriteArrayList<>();
    private static WeakReference<Activity> sActivity = new WeakReference<>(null);
    private static volatile boolean sInitialized;
    private static volatile boolean sPolling;
    private static volatile JsonObject sLastState;
    private static volatile Mode sMode;
    private static TerracottaAndroidAPI.Metadata sMetadata;

    public static synchronized void initialize(Activity activity) {
        if (!BattlyWorldsFeature.ENABLED) {
            BattlyWorldsFeature.showDisabledMessage(activity);
            return;
        }
        sActivity = new WeakReference<>(activity);
        if (sInitialized) {
            return;
        }

        sMetadata = TerracottaAndroidAPI.initialize(activity.getApplicationContext(), () -> {
            Activity current = sActivity.get();
            if (current == null || current.isFinishing()) {
                rejectPendingVpn();
                return;
            }
            current.runOnUiThread(() -> requestVpnService(current));
        });
        sInitialized = true;
        startPolling();
        setWaiting(activity, false);
    }

    public static void attachActivity(Activity activity) {
        sActivity = new WeakReference<>(activity);
    }

    public static void addStateListener(StateListener listener) {
        sListeners.add(listener);
        JsonObject state = sLastState;
        if (state != null) {
            listener.onStateChanged(state);
        }
    }

    public static void removeStateListener(StateListener listener) {
        sListeners.remove(listener);
    }

    public static void startHost(String player, List<String> nodes) {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        ensureInitialized();
        sMode = Mode.HOST;
        TerracottaAndroidAPI.setScanning(null, player, nodes);
    }

    public static boolean join(String code, String player, List<String> nodes) {
        if (!BattlyWorldsFeature.ENABLED) {
            return false;
        }
        ensureInitialized();
        sMode = Mode.GUEST;
        return TerracottaAndroidAPI.setGuesting(code, player, nodes);
    }

    @Nullable
    public static TerracottaAndroidAPI.RoomType parseRoomCode(String code) {
        if (!sInitialized || code == null) {
            return null;
        }
        return TerracottaAndroidAPI.parseRoomCode(code);
    }

    public static void setWaiting(Context context, boolean stopVpn) {
        if (!sInitialized) {
            return;
        }
        if (stopVpn) {
            stopVpnService(context);
        }
        sMode = null;
        TerracottaAndroidAPI.setWaiting();
    }

    public static void onVpnPermissionResult(Activity activity, int resultCode) {
        if (!BattlyWorldsFeature.ENABLED) {
            rejectPendingVpn();
            return;
        }
        if (resultCode == Activity.RESULT_OK) {
            startVpnService(activity);
        } else {
            rejectPendingVpn();
            setWaiting(activity, true);
            Toast.makeText(activity, R.string.battlyworlds_vpn_permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    public static void updateVpnNotification(Context context, String stateText) {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        Intent intent = new Intent(context, BattlyWorldsVpnService.class);
        intent.setAction(BattlyWorldsVpnService.ACTION_UPDATE_STATE);
        intent.putExtra(BattlyWorldsVpnService.EXTRA_STATE_TEXT, stateText);
        ContextCompat.startForegroundService(context, intent);
    }

    public static Mode getMode() {
        return sMode;
    }

    public static String getMetadataText(Context context) {
        TerracottaAndroidAPI.Metadata metadata = sMetadata;
        if (metadata == null) {
            return context.getString(R.string.battlyworlds_metadata_unknown);
        }
        return context.getString(
                R.string.battlyworlds_metadata,
                metadata.getTerracottaVersion(),
                metadata.getEasyTierVersion()
        );
    }

    public static String collectLogs() {
        if (!sInitialized) {
            return "";
        }
        try (Reader reader = TerracottaAndroidAPI.collectLogs();
             StringWriter writer = new StringWriter()) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, count);
            }
            return writer.toString();
        } catch (IOException e) {
            return "Failed to collect BattlyWorlds logs: " + e.getMessage();
        }
    }

    public static String describeState(Context context, @Nullable JsonObject state) {
        if (state == null || !state.has("state")) {
            return context.getString(R.string.battlyworlds_state_unknown);
        }
        String stateName = state.get("state").getAsString();
        switch (stateName) {
            case "waiting":
                return context.getString(R.string.battlyworlds_state_waiting);
            case "host-scanning":
                return context.getString(R.string.battlyworlds_state_host_scanning);
            case "host-starting":
                return context.getString(R.string.battlyworlds_state_host_starting);
            case "host-ok":
                String room = state.has("room") ? state.get("room").getAsString() : "";
                return context.getString(R.string.battlyworlds_state_host_ok, room);
            case "guest-connecting":
                return context.getString(R.string.battlyworlds_state_guest_connecting);
            case "guest-starting":
                return context.getString(R.string.battlyworlds_state_guest_starting);
            case "guest-ok":
                String url = state.has("url") ? state.get("url").getAsString() : "";
                return context.getString(R.string.battlyworlds_state_guest_ok, url);
            case "exception":
                return describeException(context, state);
            default:
                return stateName;
        }
    }

    private static String describeException(Context context, JsonObject state) {
        String kind = state.has("kind") ? state.get("kind").getAsString() : "";
        switch (kind) {
            case "PingHostFail":
            case "PING_HOST_FAIL":
                return context.getString(R.string.battlyworlds_error_ping_host_fail);
            case "PingHostRst":
            case "PING_HOST_RST":
                return context.getString(R.string.battlyworlds_error_ping_host_rst);
            case "GuestETCrash":
            case "GUEST_ET_CRASH":
                return context.getString(R.string.battlyworlds_error_guest_et_crash);
            case "HostETCrash":
            case "HOST_ET_CRASH":
                return context.getString(R.string.battlyworlds_error_host_et_crash);
            case "PingServerRst":
            case "PING_SERVER_RST":
                return context.getString(R.string.battlyworlds_error_ping_server_rst);
            case "ScaffoldingInvalidResponse":
            case "SCAFFOLDING_INVALID_RESPONSE":
                return context.getString(R.string.battlyworlds_error_scaffolding_invalid_response);
            default:
                int type = state.has("type") ? state.get("type").getAsInt() : -1;
                return describeExceptionType(context, type);
        }
    }

    private static String describeExceptionType(Context context, int type) {
        switch (type) {
            case 0:
                return context.getString(R.string.battlyworlds_error_ping_host_fail);
            case 1:
                return context.getString(R.string.battlyworlds_error_ping_host_rst);
            case 2:
                return context.getString(R.string.battlyworlds_error_guest_et_crash);
            case 3:
                return context.getString(R.string.battlyworlds_error_host_et_crash);
            case 4:
                return context.getString(R.string.battlyworlds_error_ping_server_rst);
            case 5:
                return context.getString(R.string.battlyworlds_error_scaffolding_invalid_response);
            default:
                return context.getString(R.string.battlyworlds_state_exception, type);
        }
    }

    @Nullable
    public static String getHostCode(@Nullable JsonObject state) {
        if (state != null
                && state.has("state")
                && "host-ok".equals(state.get("state").getAsString())
                && state.has("room")) {
            return state.get("room").getAsString();
        }
        return null;
    }

    @Nullable
    public static String getGuestUrl(@Nullable JsonObject state) {
        if (state != null
                && state.has("state")
                && "guest-ok".equals(state.get("state").getAsString())
                && state.has("url")) {
            return state.get("url").getAsString();
        }
        return null;
    }

    private static void requestVpnService(Activity activity) {
        if (!BattlyWorldsFeature.ENABLED) {
            rejectPendingVpn();
            return;
        }
        Intent intent = VpnService.prepare(activity);
        if (intent != null) {
            activity.startActivityForResult(intent, REQUEST_VPN_PERMISSION);
        } else {
            startVpnService(activity);
        }
    }

    private static void startVpnService(Context context) {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        Intent intent = new Intent(context, BattlyWorldsVpnService.class);
        intent.setAction(BattlyWorldsVpnService.ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    private static void stopVpnService(Context context) {
        if (!BattlyWorldsFeature.ENABLED) {
            return;
        }
        if (!BattlyWorldsVpnService.isRunning()) {
            return;
        }
        Intent intent = new Intent(context, BattlyWorldsVpnService.class);
        intent.setAction(BattlyWorldsVpnService.ACTION_STOP);
        ContextCompat.startForegroundService(context, intent);
    }

    private static void rejectPendingVpn() {
        try {
            TerracottaAndroidAPI.getPendingVpnServiceRequest().reject();
        } catch (Throwable throwable) {
            Log.w(TAG, "No pending VPN request to reject", throwable);
        }
    }

    private static void ensureInitialized() {
        if (!sInitialized) {
            throw new IllegalStateException("BattlyWorlds is not initialized");
        }
    }

    private static void startPolling() {
        if (sPolling) {
            return;
        }
        sPolling = true;
        Thread thread = new Thread(() -> {
            int lastIndex = -1;
            while (sPolling) {
                try {
                    String rawState = TerracottaAndroidAPI.getState();
                    JsonObject state = Tools.GLOBAL_GSON.fromJson(rawState, JsonObject.class);
                    int index = state != null && state.has("index") ? state.get("index").getAsInt() : -1;
                    if (index > lastIndex) {
                        lastIndex = index;
                        sLastState = state;
                        notifyStateChanged(state);
                    }
                } catch (Throwable throwable) {
                    Log.w(TAG, "Unable to poll BattlyWorlds state", throwable);
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "BattlyWorlds State Poller");
        thread.setDaemon(true);
        thread.start();
    }

    private static void notifyStateChanged(JsonObject state) {
        Activity activity = sActivity.get();
        Context context = activity == null ? null : activity.getApplicationContext();
        if (context != null && sMode != null && state != null && state.has("state")
                && !"waiting".equals(state.get("state").getAsString())) {
            updateVpnNotification(context, describeState(context, state));
        }
        Tools.MAIN_HANDLER.post(() -> {
            for (StateListener listener : sListeners) {
                listener.onStateChanged(state);
            }
        });
    }

    private BattlyWorldsManager() {
    }
}
