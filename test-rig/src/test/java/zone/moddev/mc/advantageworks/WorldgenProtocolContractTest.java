package zone.moddev.mc.advantageworks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldgenProtocolContractTest {
    @Test public void samplingAdvancesOnlyFromNativeServerTickEnd() throws Exception {
        String source = source("src/main/java/zone/moddev/mc/advantageworks/WorksController.java");
        assertTrue("Sampling must yield to native server ticks", source.contains("TickEvent.ServerTickEvent"));
        assertTrue(source.contains("TickEvent.Phase.END"));
        String command = source.substring(source.indexOf("synchronized String sampleWorldgen("),
                source.indexOf("List<String> stationIdsWithAll()"));
        assertFalse("The command must not synchronously populate the halo",
                command.contains("for (Long packed : populationChunks)"));
    }

    @Test public void bothRunsWaitForFreshCompletionBeforeSaveAndStop() throws Exception {
        String source = source("src/runner/java/zone/moddev/mc/advantageworks/runner/WorksRunner.java");
        for (String process : new String[] {"first", "second"}) {
            int wait = source.indexOf("awaitWorldgen(" + process + ",");
            int save = source.indexOf("send(" + process + ", \"save-all\")");
            assertTrue(process + " must await its own new completed report", wait >= 0 && wait < save);
        }
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
