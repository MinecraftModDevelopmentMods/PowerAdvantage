package zone.moddev.mc.poweradvantage.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class StarchRecipeTest {
    @Test public void craftingAcceptsOneOrdinaryOrPoisonousPotatoWithoutAnotherMod() throws Exception {
        JsonObject recipe = read("starch_from_potato");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertFalse(recipe.has("conditions"));
        JsonArray inputs = recipe.getAsJsonArray("ingredients");
        assertEquals(1, inputs.size());
        assertEquals(potatoes(), items(inputs.get(0).getAsJsonArray()));
        assertEquals("poweradvantage:starch", recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test public void existingCrushingKeepsBothPotatoesAndItsOneToOneRatio() throws Exception {
        JsonObject recipe = read("crushing_potato_starch");
        assertEquals("basemetals:crushing", recipe.get("type").getAsString());
        assertEquals(potatoes(), items(recipe.getAsJsonArray("ingredient")));
        assertEquals("poweradvantage:starch", recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    private static JsonObject read(String name) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(Paths.get(
                "src/main/resources/data/poweradvantage/recipes/" + name + ".json")), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static Set<String> potatoes() {
        return new HashSet<>(java.util.Arrays.asList("minecraft:potato", "minecraft:poisonous_potato"));
    }
    private static Set<String> items(JsonArray values) {
        Set<String> items = new HashSet<>();
        values.forEach(value -> items.add(value.getAsJsonObject().get("item").getAsString()));
        return items;
    }
}
