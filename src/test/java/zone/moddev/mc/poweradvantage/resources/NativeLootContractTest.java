package zone.moddev.mc.poweradvantage.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeLootContractTest {
    private static final Path ROOT = Paths.get("src/main/resources");

    @Test public void everyRegisteredBlockHasANativeLootTable() throws Exception {
        try (java.util.stream.Stream<Path> states = Files.list(ROOT.resolve("assets/poweradvantage/blockstates"))) {
            for (Path file : (Iterable<Path>) states.filter(p -> p.toString().endsWith(".json"))::iterator) {
                String id = file.getFileName().toString();
                JsonObject table = read(ROOT.resolve("data/poweradvantage/loot_tables/blocks").resolve(id));
                assertEquals(id, "minecraft:block", table.get("type").getAsString());
                if (id.equals("crude_oil.json") || id.equals("refined_oil.json")) assertEquals(0, table.getAsJsonArray("pools").size());
                else assertEquals(id, 1, table.getAsJsonArray("pools").size());
            }
        }
    }

    @Test public void portableTankCopiesTheExistingItemKeysAndTerminalReturnsAPlainPipe() throws Exception {
        JsonObject table = read(ROOT.resolve("data/poweradvantage/loot_tables/blocks/fluid_storage_tank.json"));
        String serialized = table.toString();
        assertTrue(serialized.contains("\"source\":\"Tank.FluidName\",\"target\":\"FluidID\""));
        assertTrue(serialized.contains("\"source\":\"Tank.Amount\",\"target\":\"Volume\""));
        JsonObject pipe = read(ROOT.resolve("data/poweradvantage/loot_tables/blocks/fluid_pipe_terminal.json"));
        assertEquals("poweradvantage:fluid_pipe", pipe.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
    }

    private static JsonObject read(Path file) throws Exception {
        assertTrue("Missing native loot table " + file, Files.isRegularFile(file));
        return new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
