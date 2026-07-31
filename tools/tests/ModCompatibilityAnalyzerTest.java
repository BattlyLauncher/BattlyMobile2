import net.kdt.pojavlaunch.utils.ModCompatibilityAnalyzer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class ModCompatibilityAnalyzerTest {
    public static void main(String[] args) throws Exception {
        parsesFabricResolutionAndIgnoresSecondaryAwtFailure();
        parsesForgeLanguageProviderMismatch();
        parsesMixinFailuresAndNamesTheMods();
        parsesOutdatedQuiltBytecodeFailure();
        leavesUnrelatedCrashesUntouched();
        if (args.length > 0) parsesRealLog(args[0]);
        System.out.println("ModCompatibilityAnalyzer tests passed");
    }

    private static void parsesFabricResolutionAndIgnoresSecondaryAwtFailure() {
        String log = "[main/WARN]: Mod resolution failed\n"
                + "[main/INFO]: Reason: [HARD_DEP fabric-api 0.116.14+1.21.1 "
                + "{depends minecraft @ [>=1.21- <1.21.2-]}, "
                + "HARD_DEP forgeconfigapiport 21.1.6 {depends minecraft @ [1.21.1]}]\n"
                + "[main/INFO]: Fix: add [], remove [], replace "
                + "[[minecraft 1.21.8] -> add:minecraft 1.21.1 ([[1.21.1,1.21.1]])]\n"
                + "[main/ERROR]: Incompatible mods found!\n"
                + "net.fabricmc.loader.impl.FormattedException: Some of your mods are incompatible!\n"
                + "\t - Cambia 'Minecraft' (minecraft) 1.21.8 por la versión 1.21.1.\n"
                + "Dependencias no satisfechas:\n"
                + "\t - ¡El mod 'Fabric API' (fabric-api) 0.116.14+1.21.1 "
                + "necesita la versión 1.21.1 de 'Minecraft' (minecraft), "
                + "pero sólo tienes una versión incorrecta: 1.21.8!\n"
                + "java.lang.UnsatisfiedLinkError: 'void java.awt.Insets.initIDs()'\n"
                + "\tat net.fabricmc.loader.impl.gui.FabricMainWindow.open(FabricMainWindow.java:87)";

        ModCompatibilityAnalyzer.Analysis analysis = ModCompatibilityAnalyzer.analyze(log);
        check(analysis.detected, "Fabric incompatibility was not detected");
        check("Fabric".equals(analysis.loader), "Fabric loader was not identified");
        check("1.21.8".equals(analysis.currentMinecraftVersion), "Current Minecraft mismatch");
        check("1.21.1".equals(analysis.recommendedMinecraftVersion), "Recommended Minecraft mismatch");
        check("Fabric API".equals(analysis.issues.get(0).modName), "Readable mod name was not parsed");
        check(!analysis.primaryExcerpt.contains("Insets.initIDs"), "AWT error leaked into primary excerpt");
        check(!analysis.primaryExcerpt.contains("FabricMainWindow.open"), "Fabric GUI leaked into primary excerpt");
    }

    private static void leavesUnrelatedCrashesUntouched() {
        ModCompatibilityAnalyzer.Analysis analysis = ModCompatibilityAnalyzer.analyze(
                "java.lang.OutOfMemoryError: Java heap space");
        check(!analysis.detected, "Unrelated crash was classified as a mod incompatibility");
    }

    private static void parsesForgeLanguageProviderMismatch() {
        String log = "net.minecraftforge.fml.ModLoadingException\n"
                + "Mod File geckolib-forge-1.20.1-4.8.4.jar "
                + "needs language provider javafml:47 or above to load\n"
                + "We have found 36.2";
        ModCompatibilityAnalyzer.Analysis analysis = ModCompatibilityAnalyzer.analyze(log);
        check(analysis.detected, "Forge incompatibility was not detected");
        check("Forge".equals(analysis.loader), "Forge loader was not identified");
        check("geckolib-forge-1.20.1-4.8.4.jar".equals(analysis.issues.get(0).modName),
                "Forge mod filename was not parsed");
        check("47+".equals(analysis.issues.get(0).requirement), "Forge requirement mismatch");
        check("36.2".equals(analysis.issues.get(0).currentVersion), "Forge installed version mismatch");
    }

    private static void parsesMixinFailuresAndNamesTheMods() {
        String log = "[main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n"
                + "\t- sodium 0.9.1+mc26.1.2\n"
                + "\t- fabric-lifecycle-events-v1 4.1.0\n"
                + "[main/ERROR]: Mixin apply for mod sodium failed config from mod sodium "
                + "InvalidInjectionException\n"
                + "[main/ERROR]: Mixin apply for mod fabric-lifecycle-events-v1 failed config "
                + "from mod fabric-lifecycle-events-v1 InvalidInjectionException\n"
                + "java.lang.UnsatisfiedLinkError: 'void java.awt.Insets.initIDs()'";
        ModCompatibilityAnalyzer.Analysis analysis = ModCompatibilityAnalyzer.analyze(log);
        check(analysis.detected, "Mixin incompatibility was not detected");
        check(analysis.issues.size() == 2, "Mixin failures did not produce two mod rows");
        check("sodium".equals(analysis.issues.get(0).modId), "Sodium was not identified");
        check("0.9.1+mc26.1.2".equals(analysis.issues.get(0).installedVersion),
                "Sodium version was not identified");
        check(!analysis.primaryExcerpt.contains("Insets.initIDs"),
                "Secondary AWT failure leaked into Mixin excerpt");
    }

    private static void parsesOutdatedQuiltBytecodeFailure() {
        String log = "Loading Minecraft 26.1.2 with Quilt Loader 0.24.0\n"
                + "java.lang.IllegalArgumentException: Unsupported class file major version 69\n"
                + "Quilt Loader Version: 0.24.0\n"
                + "FabricGuiEntry.open";
        ModCompatibilityAnalyzer.Analysis analysis = ModCompatibilityAnalyzer.analyze(log);
        check(analysis.detected, "Outdated Quilt bytecode failure was not detected");
        check("Quilt".equals(analysis.loader), "Quilt loader was not identified");
        check(analysis.summary.contains("Quilt 0.24.0"), "Quilt version is missing");
        check(analysis.summary.contains("Java 25"), "Java version was not derived");
        check(analysis.solution.contains("newer Quilt"), "Quilt upgrade was not recommended");
    }

    private static void parsesRealLog(String path) throws Exception {
        String log = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        ModCompatibilityAnalyzer.Analysis analysis = ModCompatibilityAnalyzer.analyze(log);
        check(analysis.detected, "Real log incompatibility was not detected");
        check(!analysis.issues.isEmpty(), "Real log did not produce incompatible mod rows");
        check("1.21.8".equals(analysis.currentMinecraftVersion), "Real log current Minecraft mismatch");
        check("1.21.1".equals(analysis.recommendedMinecraftVersion), "Real log recommendation mismatch");
        check(!analysis.primaryExcerpt.contains("Insets.initIDs"), "Real log exposed secondary AWT error");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
