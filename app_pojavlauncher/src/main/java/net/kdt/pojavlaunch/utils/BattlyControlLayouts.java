package net.kdt.pojavlaunch.utils;

import android.content.Context;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.IOException;

public final class BattlyControlLayouts {
    public static final String PREF_SELECTED_CONTROL_LAYOUT = "battly_selected_control_layout";
    public static final String CLASSIC = "classic";
    public static final String MODERN = "modern";

    private BattlyControlLayouts() {
    }

    public static String getSelectedLayout() {
        return LauncherPreferences.DEFAULT_PREF.getString(PREF_SELECTED_CONTROL_LAYOUT, MODERN);
    }

    public static boolean isSelected(String layout) {
        return layout.equals(getSelectedLayout());
    }

    public static void apply(Context context, String layout) throws IOException {
        String assetName = MODERN.equals(layout) ? "default_new.json" : "default.json";
        Tools.copyAssetFile(context, assetName, Tools.CTRLMAP_PATH, "default.json", true);
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = Tools.CTRLDEF_FILE;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString("defaultCtrl", Tools.CTRLDEF_FILE)
                .putString(PREF_SELECTED_CONTROL_LAYOUT, MODERN.equals(layout) ? MODERN : CLASSIC)
                .apply();
    }
}
