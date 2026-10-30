package zone.moddev.mc.advantageworks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public class Native116CommandRegistrationTest {
    @Test public void everyProbeRegistersOnTheNativeDispatcherRebuildEvent() throws Exception {
        for (String name : new String[] {"ActorProbe", "AdvantageWorksMod", "DimensionEntityProbe",
                "ConnectedProbe", "NativeHarvestProbe", "LifecycleProbe", "MigrationProbe",
                "RecipeUnlockProbe", "ProgressionProbe", "NativeInteropProbe"}) {
            String source = new String(Files.readAllBytes(Paths.get(
                    "src/main/java/zone/moddev/mc/advantageworks/" + name + ".java")), StandardCharsets.UTF_8);
            assertTrue(name, source.contains("import net.minecraftforge.event.RegisterCommandsEvent;"));
            assertTrue(name, source.contains("(RegisterCommandsEvent event)"));
            assertTrue(name, source.contains("event.getDispatcher()"));
            assertFalse(name, source.contains("FMLServerStartingEvent"));
        }
    }
}
