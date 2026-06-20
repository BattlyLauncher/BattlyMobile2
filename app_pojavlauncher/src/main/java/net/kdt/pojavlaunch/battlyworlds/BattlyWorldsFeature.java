package net.kdt.pojavlaunch.battlyworlds;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import net.kdt.pojavlaunch.R;

public final class BattlyWorldsFeature {
    /**
     * Temporary Google Play kill-switch.
     *
     * Keep the BattlyWorlds implementation in the codebase, but do not expose or
     * start VPN/Terracotta functionality until Google grants access to VPNService.
     */
    public static final boolean ENABLED = false;

    private BattlyWorldsFeature() {
    }

    public static boolean showDisabledMessage(Context context) {
        if (context == null) {
            return true;
        }
        Toast.makeText(context, R.string.battlyworlds_temporarily_disabled_short, Toast.LENGTH_LONG).show();
        return true;
    }

    public static void showDisabledDialog(Context context) {
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.battlyworlds_temporarily_disabled_title)
                .setMessage(R.string.battlyworlds_temporarily_disabled_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
