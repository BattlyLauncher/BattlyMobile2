package net.kdt.pojavlaunch.utils;

public final class FragmentNavigationPolicy {
    private FragmentNavigationPolicy() {
    }

    public static boolean canNavigate(boolean finishing, boolean destroyed,
                                      boolean stateSaved, boolean lifecycleStarted) {
        return !finishing && !destroyed && !stateSaved && lifecycleStarted;
    }
}
