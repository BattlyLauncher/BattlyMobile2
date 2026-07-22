package net.kdt.pojavlaunch.utils;

import android.content.Context;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.LayoutConverter;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;

public final class BattlyControlLayouts {
    public static final String PREF_SELECTED_CONTROL_LAYOUT = "battly_selected_control_layout";
    public static final String CLASSIC = "classic";
    public static final String MODERN = "modern";
    private static final String PREF_PERFORMANCE_WIDGET_MIGRATED =
            "battly_performance_widget_migrated_v1";

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

    public static void migrateDefaultPerformanceWidget() {
        if (LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_PERFORMANCE_WIDGET_MIGRATED, false)) {
            return;
        }
        if (Tools.currentDisplayMetrics == null) {
            return;
        }

        File defaultLayout = new File(Tools.CTRLDEF_FILE);
        if (!defaultLayout.isFile()) {
            return;
        }

        try {
            CustomControls controls = LayoutConverter.loadAndConvertIfNecessary(defaultLayout.getAbsolutePath());
            if (controls == null || controls.mControlDataList == null) {
                return;
            }
            boolean alreadyPresent = false;
            for (ControlData data : controls.mControlDataList) {
                if (data != null && data.isPerformanceWidget()) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                controls.mControlDataList.add(ControlData.createPerformanceWidget());
                controls.save(defaultLayout.getAbsolutePath());
            }
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putBoolean(PREF_PERFORMANCE_WIDGET_MIGRATED, true)
                    .apply();
        } catch (Exception ignored) {
            // Leave the migration pending so a later startup can retry it.
        }
    }
}
