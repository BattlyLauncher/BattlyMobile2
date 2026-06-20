package net.kdt.pojavlaunch.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.TestStorageActivity;
public final class BattlyAppIconManager {
    public static final String PREF_APP_ICON = "battly_plus_app_icon";

    public static final String ICON_DEFAULT = "default";
    public static final String ICON_CUSTOM_1 = "custom_1";
    public static final String ICON_CUSTOM_2 = "custom_2";
    public static final String ICON_CUSTOM_3 = "custom_3";
    public static final String ICON_CUSTOM_4 = "custom_4";
    public static final String ICON_CUSTOM_5 = "custom_5";
    public static final String ICON_CUSTOM_6 = "custom_6";

    private static final IconOption[] OPTIONS = new IconOption[]{
            new IconOption(ICON_DEFAULT, R.string.battly_plus_app_icon_default,
                    R.drawable.logo, "LauncherDefaultAlias"),
            new IconOption(ICON_CUSTOM_1, R.string.battly_plus_app_icon_custom_1,
                    R.drawable.ic_launcher_battly_custom_1, "LauncherCustom1Alias"),
            new IconOption(ICON_CUSTOM_2, R.string.battly_plus_app_icon_custom_2,
                    R.drawable.ic_launcher_battly_custom_2, "LauncherCustom2Alias"),
            new IconOption(ICON_CUSTOM_3, R.string.battly_plus_app_icon_custom_3,
                    R.drawable.ic_launcher_battly_custom_3, "LauncherCustom3Alias"),
            new IconOption(ICON_CUSTOM_4, R.string.battly_plus_app_icon_custom_4,
                    R.drawable.ic_launcher_battly_custom_4, "LauncherCustom4Alias"),
            new IconOption(ICON_CUSTOM_5, R.string.battly_plus_app_icon_custom_5,
                    R.drawable.ic_launcher_battly_custom_5, "LauncherCustom5Alias"),
            new IconOption(ICON_CUSTOM_6, R.string.battly_plus_app_icon_custom_6,
                    R.drawable.ic_launcher_battly_custom_6, "LauncherCustom6Alias")
    };

    private BattlyAppIconManager() {
    }

    public static IconOption[] getOptions() {
        return OPTIONS.clone();
    }

    public static String getSelectedIconId(Context context) {
        String iconId = prefs(context).getString(PREF_APP_ICON, ICON_DEFAULT);
        return findOption(iconId) == null ? ICON_DEFAULT : iconId;
    }

    public static void applyIcon(Context context, String selectedId) {
        if (context == null || selectedId == null) {
            return;
        }
        if (findOption(selectedId) == null) {
            selectedId = ICON_DEFAULT;
        }
        Context appContext = context.getApplicationContext();
        PackageManager packageManager = appContext.getPackageManager();
        for (IconOption option : OPTIONS) {
            ComponentName component = resolveAliasComponent(appContext, packageManager, option.aliasName);
            if (component == null) {
                continue;
            }
            int state = option.id.equals(selectedId)
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            try {
                packageManager.setComponentEnabledSetting(
                        component,
                        state,
                        PackageManager.DONT_KILL_APP);
            } catch (IllegalArgumentException ignored) {
                // Component aliases are declared in the manifest and can be package-renamed by
                // Gradle variants. Never crash the UI if a stale install exposes a different set.
            }
        }
        prefs(appContext).edit().putString(PREF_APP_ICON, selectedId).apply();
    }

    private static ComponentName resolveAliasComponent(Context context, PackageManager packageManager, String aliasName) {
        String namespacePrefix = TestStorageActivity.class.getPackage().getName();
        String appPackage = context.getPackageName();
        String[] candidates = new String[] {
                namespacePrefix + "." + aliasName,
                appPackage + "." + aliasName
        };
        for (String candidate : candidates) {
            ComponentName componentName = new ComponentName(appPackage, candidate);
            try {
                ActivityInfo ignored = packageManager.getActivityInfo(componentName, PackageManager.MATCH_DISABLED_COMPONENTS);
                return componentName;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(BattlyPlusManager.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static IconOption findOption(String iconId) {
        for (IconOption option : OPTIONS) {
            if (option.id.equals(iconId)) {
                return option;
            }
        }
        return null;
    }

    public static final class IconOption {
        public final String id;
        public final int titleRes;
        public final int iconRes;
        public final String aliasName;

        private IconOption(String id, int titleRes, int iconRes, String aliasName) {
            this.id = id;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
            this.aliasName = aliasName;
        }
    }
}
