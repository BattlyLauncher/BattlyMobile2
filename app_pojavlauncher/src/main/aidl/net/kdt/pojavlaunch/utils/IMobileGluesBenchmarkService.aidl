package net.kdt.pojavlaunch.utils;

interface IMobileGluesBenchmarkService {
    String runBenchmark(String mgDirectory, String angleDirectory, int startSections, int maxSections);
    int getProgress();
}
