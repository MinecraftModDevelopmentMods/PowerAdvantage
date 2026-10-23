package zone.moddev.mc.advantageworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProfileManifestTest {
    @Test
    public void profilesPinEverySuppliedArtifactAndCoverRecipeModes() throws Exception {
        File directory = new File("profiles");
        File[] profiles = directory.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(profiles);
        assertEquals(10, profiles.length);
        Set<String> recipeModes = new HashSet<>();
        Set<String> profileNames = new HashSet<>();

        for (File file : profiles) {
            JsonObject profile;
            try (FileReader reader = new FileReader(file)) {
                profile = new JsonParser().parse(reader).getAsJsonObject();
            }
            assertTrue(file.getName(), profileNames.add(text(profile, "name")));
            assertEquals("working-tree-from-40811a0", text(profile, "sourceCommit"));
            assertEquals("2.4.4.113021", text(profile, "sourceVersion"));
            recipeModes.add(text(profile, "recipeMode"));

            if ("packaged".equals(text(profile, "mode"))) {
                JsonObject forge = profile.getAsJsonObject("forge");
                assertNotNull(file.getName(), forge);
                assertEquals(64, text(forge, "sha256").length());
            }
            if (text(profile, "name").startsWith("worldgen-")) {
                assertFalse(file.getName(), profile.get("buildWorks").getAsBoolean());
                assertEquals("DEFAULT", text(profile, "levelType"));
                assertTrue(file.getName(), profile.get("worldgenSampleRadius").getAsInt() > 0);
            }
            if ("worldgen-legacy-import".equals(text(profile, "name"))) {
                JsonArray exclusions = profile.getAsJsonArray("stableWorldgenCountExclusions");
                assertNotNull(exclusions);
                assertEquals(1, exclusions.size());
                assertEquals("poweradvantage:crude_oil", exclusions.get(0).getAsString());
                JsonArray fixtures = profile.getAsJsonArray("configFixtures");
                assertNotNull(fixtures);
                assertEquals(2, fixtures.size());
                for (JsonElement fixtureElement : fixtures) {
                    JsonObject fixture = fixtureElement.getAsJsonObject();
                    File source = new File(directory.getParentFile(),
                            text(fixture, "source")).getCanonicalFile();
                    assertTrue("Missing config fixture " + source, source.isFile());
                }
            }

            JsonArray mods = profile.getAsJsonArray("mods");
            assertNotNull(mods);
            int oreSpawnCount = 0;
            for (JsonElement element : mods) {
                JsonObject mod = element.getAsJsonObject();
                if ("orespawn".equals(text(mod, "id"))) {
                    oreSpawnCount++;
                    assertEquals("4.1.0.113021", text(mod, "version"));
                    assertEquals("OreSpawn-4.1.0.113021.jar", text(mod, "file"));
                }
                assertFalse(file.getName(), text(mod, "id").isEmpty());
                assertFalse(file.getName(), text(mod, "file").isEmpty());
                assertFalse(file.getName(), text(mod, "version").isEmpty());
                String path = text(mod, "path");
                assertFalse("Hard-coded drive path in " + file, path.matches("^[A-Za-z]:.*"));
                boolean workspaceProduced = mod.has("workspaceProduced")
                        && mod.get("workspaceProduced").getAsBoolean();
                if (!workspaceProduced) assertEquals(file.getName(), 64, text(mod, "sha256").length());
            }
            if ("packaged".equals(text(profile, "mode"))) {
                assertEquals("Every packaged profile requires exactly one pinned OreSpawn 4 jar: " + file,
                        1, oreSpawnCount);
            }
        }

        assertTrue(profileNames.contains("packaged-mineralogy"));
        assertTrue(profileNames.contains("worldgen-advantage"));
        assertTrue(profileNames.contains("worldgen-mineralogy"));
        assertTrue(profileNames.contains("worldgen-legacy-import"));
        assertTrue(recipeModes.contains("NORMAL"));
        assertTrue(recipeModes.contains("TECH_PROGRESSION"));
        assertTrue(recipeModes.contains("APOCALYPTIC"));
    }

    private static String text(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsString() : "";
    }
}
