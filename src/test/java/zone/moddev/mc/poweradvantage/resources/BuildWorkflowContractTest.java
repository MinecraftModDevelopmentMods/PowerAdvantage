package zone.moddev.mc.poweradvantage.resources;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class BuildWorkflowContractTest {
    @Test public void testFixturesDoNotImportRestrictedUnsafeTypes() throws Exception {
        Pattern restrictedImport = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?sun\\.misc\\.Unsafe\\b");
        assertTrue(restrictedImport.matcher("import sun.misc.Unsafe;").find());
        assertTrue(restrictedImport.matcher("import static sun.misc.Unsafe.INVALID_FIELD_OFFSET;").find());
        assertFalse(restrictedImport.matcher("Class.forName(\"sun.misc.Unsafe\")").find());
        try (Stream<Path> sources = Files.walk(Paths.get("src/test/java"))) {
            for (Path path : (Iterable<Path>) sources.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                assertFalse(path.toString(), restrictedImport.matcher(source).find());
            }
        }
    }

    @Test public void pinnedDependencyCheckoutPolicyPrecedesCheckout() throws Exception {
        for (String name : new String[] {"ci.yml", "codeql-analysis.yml"}) {
            String workflow = new String(Files.readAllBytes(Paths.get(".github/workflows", name)),
                    StandardCharsets.UTF_8);
            int policy = workflow.indexOf("run: git config --global core.autocrlf false");
            int checkout = workflow.indexOf("- name: Check out pinned Base Metals");
            assertTrue(name, policy >= 0 && checkout > policy);
            assertTrue(name, workflow.contains("ref: 995f6cc4dd2d5eecc628a42e5c43fa5390c24bca"));
            assertTrue(name, workflow.contains("BaseMetals-3.1.1.116051-deobf.jar"));
            assertTrue(name, workflow.contains("ref: a113e5d767169189f0e35ade1e173a12d550b00e"));
            assertTrue(name, workflow.contains("master-1.16.5"));
            assertTrue(name, workflow.contains("java-version: '25.0.3+9.0.LTS'"));
            assertTrue(name, workflow.contains("java-version: '17.0.1+12'"));
            assertTrue(name, workflow.contains("java-version: '8.0.502+7'"));
            assertTrue(name, workflow.contains("-Porg.gradle.java.installations.paths="));
            assertTrue(name, workflow.contains("-Porg.gradle.java.installations.auto-detect=false"));
            assertTrue(name, workflow.contains("-Porg.gradle.java.installations.auto-download=false"));
            assertFalse(name, workflow.contains("-Dorg.gradle.java.installations."));
            for (String line : workflow.split("\\R")) {
                if (line.contains("./gradlew ") && !line.contains("chmod")) {
                    assertTrue(name + ": " + line, line.contains("./gradlew $MMD_GRADLE_TOOLCHAINS "));
                }
            }
        }
    }
}
