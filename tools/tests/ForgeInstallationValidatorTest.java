package net.kdt.pojavlaunch.modloaders;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.file.Files;

public final class ForgeInstallationValidatorTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("battly-forge-validator").toFile();
        String json = "{"
                + "\"arguments\":{\"game\":["
                + "\"--launchTarget\",\"forgeclient\","
                + "\"--fml.forgeVersion\",\"47.3.12\","
                + "\"--fml.mcVersion\",\"1.20.1\","
                + "\"--fml.mcpVersion\",\"20230612.114412\"]}}";
        JsonObject version = JsonParser.parseString(json).getAsJsonObject();

        ForgeInstallationValidator.Status missing =
                ForgeInstallationValidator.inspectJson(root, version);
        check(missing.forge, "Forge version must be detected");
        check(!missing.complete, "Missing processor outputs must fail validation");
        check(missing.missingFiles.size() == 3, "All three generated files must be required");
        check("1.20.1-47.3.12".equals(missing.forgeCoordinate), "Forge coordinate must be parsed");

        for (String path : missing.missingFiles) {
            File file = new File(path);
            check(file.getParentFile().mkdirs() || file.getParentFile().isDirectory(),
                    "Output directory must be created");
            Files.write(file.toPath(), new byte[]{1});
        }

        ForgeInstallationValidator.Status complete =
                ForgeInstallationValidator.inspectJson(root, version);
        check(complete.complete, "Non-empty processor outputs must pass validation");

        JsonObject vanilla = JsonParser.parseString(
                "{\"arguments\":{\"game\":[\"--username\",\"Player\"]}}").getAsJsonObject();
        check(!ForgeInstallationValidator.inspectJson(root, vanilla).forge,
                "Vanilla versions must not be treated as Forge");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
