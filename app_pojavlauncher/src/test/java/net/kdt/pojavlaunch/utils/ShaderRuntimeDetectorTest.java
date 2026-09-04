package net.kdt.pojavlaunch.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShaderRuntimeDetectorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void detectsEnabledIrisPack() throws Exception {
        File gameDir = temporaryFolder.newFolder("iris-game");
        File mods = new File(gameDir, "mods");
        File config = new File(gameDir, "config");
        assertTrue(mods.mkdirs());
        assertTrue(config.mkdirs());
        assertTrue(new File(mods, "iris-fabric-1.8.8.jar").createNewFile());
        write(new File(config, "iris.properties"),
                "enableShaders=true\nshaderPack=ComplementaryReimagined_r5.3.zip\n");

        ShaderRuntimeDetector.Result result = ShaderRuntimeDetector.detect(gameDir);

        assertTrue(result.pipelineInstalled);
        assertTrue(result.shaderEnabled);
        assertEquals("ComplementaryReimagined_r5.3.zip", result.packName);
    }

    @Test
    public void installedPipelineWithoutSelectedPackIsNotActive() throws Exception {
        File gameDir = temporaryFolder.newFolder("inactive-game");
        File mods = new File(gameDir, "mods");
        assertTrue(mods.mkdirs());
        assertTrue(new File(mods, "oculus-mc1.20.1.jar").createNewFile());
        write(new File(gameDir, "optionsshaders.txt"), "shaderPack=OFF\n");

        ShaderRuntimeDetector.Result result = ShaderRuntimeDetector.detect(gameDir);

        assertTrue(result.pipelineInstalled);
        assertFalse(result.shaderEnabled);
    }

    @Test
    public void optifineShaderOptionsActivatePack() throws Exception {
        File gameDir = temporaryFolder.newFolder("optifine-game");
        File mods = new File(gameDir, "mods");
        assertTrue(mods.mkdirs());
        assertTrue(new File(mods, "OptiFine_1.20.4_HD_U_I7.jar").createNewFile());
        write(new File(gameDir, "optionsshaders.txt"), "shaderPack=BSL_v8.2.zip\n");

        ShaderRuntimeDetector.Result result = ShaderRuntimeDetector.detect(gameDir);

        assertTrue(result.shaderEnabled);
        assertEquals("BSL_v8.2.zip", result.packName);
    }

    @Test
    public void disabledIrisConfigWinsOverStaleOptifineOptions() throws Exception {
        File gameDir = temporaryFolder.newFolder("disabled-iris-game");
        File mods = new File(gameDir, "mods");
        File config = new File(gameDir, "config");
        assertTrue(mods.mkdirs());
        assertTrue(config.mkdirs());
        assertTrue(new File(mods, "iris-fabric.jar").createNewFile());
        write(new File(config, "iris.properties"),
                "enableShaders=false\nshaderPack=Complementary.zip\n");
        write(new File(gameDir, "optionsshaders.txt"), "shaderPack=OldPack.zip\n");

        ShaderRuntimeDetector.Result result = ShaderRuntimeDetector.detect(gameDir);

        assertTrue(result.pipelineInstalled);
        assertFalse(result.shaderEnabled);
    }

    private static void write(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
