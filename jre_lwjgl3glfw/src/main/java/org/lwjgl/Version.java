package org.lwjgl;

/**
 * Stub that overrides the version constants in lwjgl.jar so that
 * Sodium's LWJGL version check passes (requires 3.4.1).
 *
 * Compiled sources are added to the fat jar before the zipTree libs,
 * so DuplicatesStrategy.EXCLUDE keeps this class instead of the one
 * from lwjgl.jar.
 */
public final class Version {

    public static final int VERSION_MAJOR    = 3;
    public static final int VERSION_MINOR    = 4;
    public static final int VERSION_REVISION = 1;

    private static final String VERSION_STRING = "3.4.1";

    private Version() {}

    public static String getVersion() {
        return VERSION_STRING;
    }

    public static void main(String[] args) {
        System.out.println("LWJGL version: " + getVersion());
    }
}
