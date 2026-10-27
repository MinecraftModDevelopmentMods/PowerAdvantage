package zone.moddev.mc.poweradvantage.resources;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class BuildWorkflowContractTest {
    @Test public void pinnedDependencyCheckoutPolicyPrecedesCheckout() throws Exception {
        for (String name : new String[] {"ci.yml", "codeql-analysis.yml"}) {
            String workflow = new String(Files.readAllBytes(Paths.get(".github/workflows", name)),
                    StandardCharsets.UTF_8);
            int policy = workflow.indexOf("run: git config --global core.autocrlf false");
            int checkout = workflow.indexOf("- name: Check out pinned Base Metals");
            assertTrue(name, policy >= 0 && checkout > policy);
            assertTrue(name, workflow.contains("ref: ac1781e7cade15045ffe0135077087b4d1722603"));
            assertTrue(name, workflow.contains("BaseMetals-3.0.2.115021-deobf.jar"));
            assertTrue(name, workflow.contains("ref: 5613baf876747ed071cb7ad7e2aff4e0ce4f0a03"));
            assertTrue(name, workflow.contains("master-1.15.2"));
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
