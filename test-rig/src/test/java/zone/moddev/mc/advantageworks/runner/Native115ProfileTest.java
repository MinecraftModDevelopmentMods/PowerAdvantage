package zone.moddev.mc.advantageworks.runner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class Native115ProfileTest {
    @Test
    public void profilesSeparateUnqualifiedDestinationsFromHistoricalSources() throws Exception {
        File[] files = new File("profiles/native115").listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(files);
        assertEquals(10, files.length);
        int ported = 0, blocked = 0;
        for (File file : files) {
            JsonObject raw;
            try (FileReader reader = new FileReader(file)) {
                raw = new JsonParser().parse(reader).getAsJsonObject();
            }
            assertEquals("1.15.2", raw.get("minecraftVersion").getAsString());
            assertEquals("UNQUALIFIED", raw.get("qualificationStatus").getAsString());
            assertEquals("build/works-native115", raw.get("runtimeBase").getAsString());
            assertEquals("1.15.2-31.2.57", raw.getAsJsonObject("forge").get("version").getAsString());
            assertEquals(64, raw.getAsJsonObject("forge").get("sha256").getAsString().length());
            String source = raw.get("legacySourceProfile").getAsString();
            assertTrue(source, source.startsWith("../../../PA113/PowerAdvantage/test-rig/profiles/"));
            assertEquals(new File(".").getCanonicalFile(), WorksRunner.findTestRig(file));
            if (raw.get("portStatus").getAsString().equals("UNPORTED")) {
                blocked++;
                try { WorksRunner.readProfile(file); fail("Unported profile was accepted: " + file); }
                catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("not ported")); }
            } else {
                ported++;
                WorksRunner.readProfile(file);
                int harness = 0;
                for (JsonElement element : raw.getAsJsonArray("mods")) {
                    JsonObject mod = element.getAsJsonObject();
                    assertTrue(file.toString(), mod.get("version").getAsString().endsWith(".115021"));
                    assertFalse(file.toString(), mod.get("path").getAsString().contains("113"));
                    assertTrue(mod.get("workspaceProduced").getAsBoolean());
                    if (mod.get("id").getAsString().equals("advantageworkstest")) harness++;
                }
                assertEquals(1, harness);
            }
        }
        assertEquals(10, ported);
        assertEquals(0, blocked);
    }

    @Test
    public void explicitlyUnportedProfilesStillFailClosed() throws Exception {
        File file = File.createTempFile("unported-native115-", ".json");
        try {
            Files.write(file.toPath(), ("{\"name\":\"blocked\",\"mode\":\"packaged\","
                    + "\"minecraftVersion\":\"1.15.2\",\"portStatus\":\"UNPORTED\","
                    + "\"portBlocker\":\"missing genuine native dependency\"}").getBytes(StandardCharsets.UTF_8));
            try { WorksRunner.readProfile(file); fail("Unported profile was accepted"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("not ported")); }
        } finally {
            Files.delete(file.toPath());
        }
    }

    @Test
    public void pendingNative115PinsFailBeforeAnyRuntimeIsCreated() throws Exception {
        WorksRunner.Profile profile = new com.google.gson.Gson().fromJson(
                "{\"mods\":[{\"id\":\"advantageworkstest\",\"sha256\":null}]}", WorksRunner.Profile.class);
        try { WorksRunner.requireInputPins(profile); fail("Pending candidate pins must fail closed"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("SHA-256 pin")); }
    }

    @Test
    public void historical114CannotBeLaunchedAsNative115() throws Exception {
        try {
            WorksRunner.readProfile(new File("profiles/native114/packaged-current.json"));
            fail("Historical 1.14.4 profile must not launch native115");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Historical source profile"));
        }
    }

    @Test
    public void historicalQualificationCannotBeLaunchedAsNative115() throws Exception {
        try {
            WorksRunner.readProfile(new File("profiles/packaged-current.json"));
            fail("Historical 1.13.2 profile must not launch native115");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Historical source profile"));
        }
    }
}
