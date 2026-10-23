package zone.moddev.mc.poweradvantage.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class ClientResourceContractTest {
    private static final Path ROOT = Paths.get("src/main/resources");
    private static final Path ASSETS = ROOT.resolve("assets/poweradvantage");

    @Test public void blockstatesResolveLiteralModelPaths() throws IOException {
        for (Path file : jsonFiles(ASSETS.resolve("blockstates"))) {
            verifyModelReferences(read(file), file);
        }
    }

    @Test public void blockstatesDoNotUseRemovedLegacySelectors() throws IOException {
        for (Path file : jsonFiles(ASSETS.resolve("blockstates"))) {
            JsonObject state = read(file);
            assertFalse(file + " uses the removed Forge blockstate format", state.has("forge_marker"));
            if (state.has("variants")) {
                assertFalse(file + " uses the removed normal selector", state.getAsJsonObject("variants").has("normal"));
                assertFalse(file + " uses the removed fluid selector", state.getAsJsonObject("variants").has("fluid"));
            }
        }
    }

    @Test public void modelParentsAndTexturesExistInActualResourcePacks() throws IOException {
        for (Path file : jsonFiles(ASSETS.resolve("models"))) {
            JsonObject model = read(file);
            if (model.has("parent")) verifyReference(model.get("parent").getAsString(), "models", ".json", file);
            if (model.has("textures")) {
                for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
                    verifyReference(texture.getValue().getAsString(), "textures", ".png", file);
                }
            }
        }
    }

    @Test public void oilBucketsAndHiddenFluidItemsHaveModels() {
        for (String id : new String[] {"crude_oil", "refined_oil", "crude_oil_bucket", "refined_oil_bucket"}) {
            assertTrue("Missing item model for " + id, Files.isRegularFile(ASSETS.resolve("models/item/" + id + ".json")));
        }
    }

    @Test public void everyLocaleRetainsLegacyNamesAndProvidesModernAliases() throws IOException {
        for (Path file : jsonFiles(ASSETS.resolve("lang"))) {
            JsonObject locale = read(file);
            for (Map.Entry<String, JsonElement> entry : locale.entrySet()) {
                String key = entry.getKey();
                if ((key.startsWith("tile.poweradvantage.") || key.startsWith("item.poweradvantage."))
                        && key.endsWith(".name")) {
                    String modern = key.substring(0, key.length() - ".name".length());
                    if (modern.startsWith("tile.")) modern = "block." + modern.substring("tile.".length());
                    assertEquals(file + " missing modern alias " + modern, entry.getValue(), locale.get(modern));
                }
            }
        }
    }

    @Test public void englishCoversEveryBlockAndOilBucket() throws IOException {
        JsonObject locale = read(ASSETS.resolve("lang/en_us.json"));
        for (Path file : jsonFiles(ASSETS.resolve("blockstates"))) {
            String id = file.getFileName().toString().replace(".json", "");
            assertTrue("Missing block name: " + id, locale.has("block.poweradvantage." + id));
        }
        for (String id : new String[] {"crude_oil", "refined_oil"}) {
            assertEquals(locale.get("item.bucket." + id), locale.get("item.poweradvantage." + id + "_bucket"));
        }
    }

    private static void verifyModelReferences(JsonElement node, Path file) {
        if (node.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("model")) verifyReference(entry.getValue().getAsString(), "models", ".json", file);
                else verifyModelReferences(entry.getValue(), file);
            }
        } else if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) verifyModelReferences(child, file);
        }
    }

    private static void verifyReference(String reference, String directory, String suffix, Path file) {
        if (reference.startsWith("#")) return;
        String[] parts = reference.split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : "minecraft";
        String path = parts.length == 2 ? parts[1] : parts[0];
        if (directory.equals("models") && namespace.equals("minecraft") && path.startsWith("builtin/")) return;
        String resource = "assets/" + namespace + "/" + directory + "/" + path + suffix;
        boolean found = namespace.equals("poweradvantage") ? Files.isRegularFile(ROOT.resolve(resource))
                : ClientResourceContractTest.class.getClassLoader().getResource(resource) != null;
        assertTrue(file + " references missing resource " + resource, found);
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static List<Path> jsonFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().collect(Collectors.toList());
        }
    }
}
