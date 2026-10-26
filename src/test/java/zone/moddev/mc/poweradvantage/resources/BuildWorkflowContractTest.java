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
            assertTrue(name, workflow.contains("ref: 713802f93f2d1f650e1593fa35c487ea43add4a5"));
            assertTrue(name, workflow.contains("ref: 9c2488cba02d317e3f31e24441dcc58273d8d0e6"));
            assertTrue(name, workflow.contains("master-1.14.4"));
        }
    }
}
