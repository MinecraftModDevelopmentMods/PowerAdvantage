package zone.moddev.mc.poweradvantage.resources;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class BuildWorkflowContractTest {
    @Test public void pinnedDependencyCheckoutPolicyPrecedesCheckout() throws Exception {
        for (String name : new String[] {"ci.yml", "codeql-analysis.yml"}) {
            String workflow = new String(Files.readAllBytes(Paths.get(".github/workflows", name)),
                    StandardCharsets.UTF_8);
            int policy = workflow.indexOf("run: git config --global core.autocrlf false");
            int checkout = workflow.indexOf("- name: Check out pinned Base Metals");
            assertTrue(name, policy >= 0 && checkout > policy);
            assertTrue(name, workflow.contains("ref: 1d166d917663d4b4a5a88a86440adc0ae761e393"));
            assertTrue(name, workflow.contains("ref: 5613baf876747ed071cb7ad7e2aff4e0ce4f0a03"));
            assertTrue(name, workflow.contains("master-1.15.2"));
        }
    }
}
