package net.kdt.pojavlaunch.modloaders.modpacks.api;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WorldArchiveInstallerTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installsWrappedWorldIntoSaves() throws Exception {
        File archive = temporaryFolder.newFile("world.zip");
        writeZip(archive, "Example/level.dat", "Example/region/r.0.0.mca");

        File installed = WorldArchiveInstaller.install(
                archive, temporaryFolder.newFolder("saves"), "My World");

        assertTrue(new File(installed, "level.dat").isFile());
        assertTrue(new File(installed, "region/r.0.0.mca").isFile());
    }

    @Test
    public void rejectsZipSlip() throws Exception {
        File archive = temporaryFolder.newFile("unsafe.zip");
        writeZip(archive, "../level.dat");
        try {
            WorldArchiveInstaller.install(archive, temporaryFolder.newFolder("safe"), "Unsafe");
            fail("Expected unsafe archive to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unsafe"));
        }
    }

    private static void writeZip(File destination, String... names) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(destination))) {
            for (String name : names) {
                output.putNextEntry(new ZipEntry(name));
                output.write(new byte[] {1, 2, 3});
                output.closeEntry();
            }
        }
    }
}
