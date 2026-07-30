package net.kdt.pojavlaunch.modloaders;

import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ModloaderInstallUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsInstallerWithManifestAndInstallProfile() throws Exception {
        File installer = temporaryFolder.newFile("installer.jar");
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(installer))) {
            writeEntry(output, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");
            writeEntry(output, "install_profile.json", "{}");
        }

        ModloaderInstallUtils.validateInstallerJar(installer);
    }

    @Test
    public void rejectsCorruptInstallerBeforeRuntimeSelection() throws Exception {
        File installer = temporaryFolder.newFile("installer.jar");
        try (FileOutputStream output = new FileOutputStream(installer)) {
            output.write("<html>gateway error</html>".getBytes(StandardCharsets.UTF_8));
        }

        assertThrows(Exception.class,
                () -> ModloaderInstallUtils.validateInstallerJar(installer));
    }

    private static void writeEntry(ZipOutputStream output, String name, String content)
            throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
