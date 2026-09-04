package net.kdt.pojavlaunch.utils;

final class MobileGluesBenchmarkNative {
    static {
        System.loadLibrary("mobileglues_benchmark");
    }

    private MobileGluesBenchmarkNative() {
    }

    static native String runBenchmark(String libraryPath, String mgDirectory,
                                      String angleDirectory, int startSections, int maxSections);

    static native int getProgress();
}
