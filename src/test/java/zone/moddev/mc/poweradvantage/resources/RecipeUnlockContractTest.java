package zone.moddev.mc.poweradvantage.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecipeUnlockContractTest {
    private static final String MOD = "poweradvantage";
    private static final Path DATA = Paths.get("src/main/resources/data", MOD);
    private static final Set<String> IGNORED = new LinkedHashSet<>(java.util.Arrays.asList(
            "item:minecraft:stick", "item:minecraft:string", "item:minecraft:feather", "tag:forge:rods/wooden"));

    @Test public void everyCraftingRecipeHasExactlyOneUnlockAndProcessingHasNone() throws IOException {
        Set<String> expected = new LinkedHashSet<>();
        for (Path file : files(DATA.resolve("recipes"))) {
            if (crafting(read(file))) expected.add(file.getFileName().toString());
        }
        Set<String> actual = files(DATA.resolve("advancements/recipes")).stream()
                .map(file -> file.getFileName().toString()).collect(Collectors.toSet());
        assertFalse("No crafting recipes found", expected.isEmpty());
        assertEquals("Missing or orphaned recipe unlocks", expected, actual);
    }

    @Test public void discoveriesAreSingleIngredientAlternativesNotAnEntireCraftingGrid() throws IOException {
        for (Path file : files(DATA.resolve("recipes"))) {
            JsonObject recipe = read(file);
            if (!crafting(recipe)) continue;
            JsonObject unlock = read(DATA.resolve("advancements/recipes").resolve(file.getFileName()));
            assertEquals("minecraft:recipes/root", unlock.get("parent").getAsString());
            assertFalse("Recipe unlocks should not create visible advancement tabs", unlock.has("display"));
            JsonObject criteria = unlock.getAsJsonObject("criteria");
            Set<String> actual = new LinkedHashSet<>();
            for (Map.Entry<String, JsonElement> entry : criteria.entrySet()) {
                if (entry.getKey().equals("has_recipe")) continue;
                JsonObject criterion = entry.getValue().getAsJsonObject();
                assertEquals(file.toString(), "minecraft:inventory_changed", criterion.get("trigger").getAsString());
                JsonArray items = criterion.getAsJsonObject("conditions").getAsJsonArray("items");
                assertEquals("Finding ONE ingredient must be enough: " + file, 1, items.size());
                JsonObject predicate = items.get(0).getAsJsonObject();
                assertEquals("Only an item or tag predicate is expected", 1, predicate.size());
                actual.add(predicateKey(predicate));
            }
            List<JsonObject> choices = new ArrayList<>();
            if (recipe.has("key")) {
                for (Map.Entry<String, JsonElement> entry : recipe.getAsJsonObject("key").entrySet())
                    collect(entry.getValue(), choices);
            } else collect(recipe.has("ingredients") ? recipe.get("ingredients") : recipe.get("ingredient"), choices);
            Set<String> expected = choices.stream().map(RecipeUnlockContractTest::predicateKey)
                    .filter(key -> !IGNORED.contains(key)).collect(Collectors.toCollection(LinkedHashSet::new));
            if (expected.isEmpty()) for (JsonObject choice : choices) expected.add(predicateKey(choice));
            assertEquals("Trigger must correspond to a relevant recipe ingredient: " + file, expected, actual);
            JsonArray requirements = unlock.getAsJsonArray("requirements");
            assertEquals("All criteria must form one OR group", 1, requirements.size());
            Set<String> alternatives = new LinkedHashSet<>();
            for (JsonElement key : requirements.get(0).getAsJsonArray()) alternatives.add(key.getAsString());
            assertEquals(criteria.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toSet()), alternatives);
        }
    }

    @Test public void rewardAndRecipeCommandCriterionReferToTheExactVariant() throws IOException {
        for (Path file : files(DATA.resolve("advancements/recipes"))) {
            String id = MOD + ":" + file.getFileName().toString().replace(".json", "");
            JsonObject unlock = read(file);
            JsonArray recipes = unlock.getAsJsonObject("rewards").getAsJsonArray("recipes");
            assertEquals(1, recipes.size());
            assertEquals(id, recipes.get(0).getAsString());
            JsonObject criterion = unlock.getAsJsonObject("criteria").getAsJsonObject("has_recipe");
            assertEquals("minecraft:recipe_unlocked", criterion.get("trigger").getAsString());
            assertEquals(id, criterion.getAsJsonObject("conditions").get("recipe").getAsString());
            assertFalse("Forge recipe conditions are not valid advancement conditions", unlock.has("conditions"));
        }
    }

    @Test public void recipeBookGroupsIdentifyTheOutputNotABroadMachineCategory() throws IOException {
        for (Path file : files(DATA.resolve("recipes"))) {
            JsonObject recipe = read(file);
            if (!crafting(recipe)) continue;
            JsonElement result = recipe.get("result");
            String output = result.isJsonPrimitive() ? result.getAsString()
                    : result.getAsJsonObject().get("item").getAsString();
            assertTrue("Missing output-specific group: " + file, recipe.has("group"));
            assertEquals("Different items must not share a recipe-book cell: " + file,
                    output, recipe.get("group").getAsString());
        }
    }

    private static boolean crafting(JsonObject recipe) {
        String type = recipe.get("type").getAsString();
        return type.equals("minecraft:crafting_shaped") || type.equals("minecraft:crafting_shapeless")
                || type.equals("minecraft:smelting");
    }

    private static String predicateKey(JsonObject predicate) {
        String type = predicate.has("tag") ? "tag" : "item";
        return type + ":" + predicate.get(type).getAsString();
    }

    private static void collect(JsonElement element, List<JsonObject> result) {
        if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) collect(child, result);
        else result.add(element.getAsJsonObject());
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static List<Path> files(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return java.util.Collections.emptyList();
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().collect(Collectors.toList());
        }
    }
}
