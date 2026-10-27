package zone.moddev.mc.advantageworks.runner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/** Keep generated compiler output separate from disposable-world evidence. */
public class HarnessOutputIsolationTest {
    @Test public void gradleCleanupCannotTargetTheSharedEvidenceRoot() throws Exception {
        String script = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);
        assertTrue(script.contains("layout.buildDirectory = layout.projectDirectory.dir('build/gradle')"));
        assertTrue(script.contains("targetFiles.files.any { it.canonicalFile != expected }"));
        assertTrue(script.contains("Refusing to clean outside the isolated harness compiler output"));
    }
}
