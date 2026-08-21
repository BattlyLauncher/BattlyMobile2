package net.kdt.pojavlaunch.battlyworlds;

import android.content.Context;
import android.widget.Toast;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.BuildConfig;

public final class BattlyWorldsFeature {
    /** Build-time kill-switch for the BattlyWorlds UI and Terracotta runtime. */
    public static final boolean ENABLED = BuildConfig.BATTLY_WORLDS_ENABLED;
    /** Legacy TUN transport. Production uses Terracotta Scaffolding without a VPN. */
    public static final boolean VPN_ENABLED = BuildConfig.BATTLY_WORLDS_VPN_ENABLED;

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
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(context)
                .setTitle(R.string.battlyworlds_temporarily_disabled_title)
                .setMessage(R.string.battlyworlds_temporarily_disabled_message)
                .setPositiveButton(android.R.string.ok, null));
    }
}
