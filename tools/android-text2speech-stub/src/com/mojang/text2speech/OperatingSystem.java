package com.mojang.text2speech;

public enum OperatingSystem {
    LINUX,
    WINDOWS,
    MAC_OS,
    UNSUPPORTED;

    public static OperatingSystem get() {
        return UNSUPPORTED;
    }
}
