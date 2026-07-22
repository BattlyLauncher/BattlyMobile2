package net.kdt.pojavlaunch.battlyworlds;

import android.content.Context;
import android.widget.Toast;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public final class BattlyWorldsFeature {
    /** Central kill-switch kept for emergency rollback without removing the integration. */
    public static final boolean ENABLED = true;

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
