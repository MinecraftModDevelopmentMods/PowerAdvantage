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

public class Native116ProfileTest {
    @Test
    public void profilesSeparateUnqualifiedDestinationsFromHistoricalSources() throws Exception {
        File[] files = new File("profiles/native116").listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(files);
        assertEquals(10, files.length);
        int ported = 0, blocked = 0;
        for (File file : files) {
            JsonObject raw;
            try (FileReader reader = new FileReader(file)) {
                raw = new JsonParser().parse(reader).getAsJsonObject();
            }
            assertEquals("1.16.5", raw.get("minecraftVersion").getAsString());
            assertEquals("UNQUALIFIED", raw.get("qualificationStatus").getAsString());
            assertEquals("build/works-native116", raw.get("runtimeBase").getAsString());
            assertEquals("1.16.5-36.2.34", raw.getAsJsonObject("forge").get("version").getAsString());
            assertEquals(64, raw.getAsJsonObject("forge").get("sha256").getAsString().length());
            String source = raw.get("legacySourceProfile").getAsString();
            assertTrue(source, source.startsWith("../../../PA113/PowerAdvantage/test-rig/profiles/"));
            assertEquals(new File(".").getCanonicalFile(), WorksRunner.findTestRig(file));
            if (raw.get("portStatus").getAsString().equals("UNPORTED")) {
                blocked++;
                try { WorksRunner.readNative116Profile(file); fail("Unported profile was accepted: " + file); }
                catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("not ported")); }
            } else {
                ported++;
                WorksRunner.readNative116Profile(file);
                int harness = 0;
                for (JsonElement element : raw.getAsJsonArray("mods")) {
                    JsonObject mod = element.getAsJsonObject();
                    assertTrue(file.toString(), mod.get("version").getAsString().endsWith(".116051"));
                    String path = mod.get("path").getAsString();
                    assertFalse(file.toString(), historicalWorkspacePath(path));
                    String artifact = new File(path).getName();
                    assertEquals(mod.get("file").getAsString(), artifact);
                    assertTrue(artifact, artifact.contains("-" + mod.get("version").getAsString()));
                    assertTrue(mod.get("workspaceProduced").getAsBoolean());
                    if (mod.get("id").getAsString().equals("advantageworkstest")) harness++;
                }
                assertEquals(1, harness);
            }
        }
        assertEquals(10, ported);
        assertEquals(0, blocked);
    }

    @Test public void archiveHashDigitsDoNotIdentifyAHistoricalWorkspace() {
        assertFalse(historicalWorkspacePath("build/native116-artifacts/B4CBB113B3/Native-1.0.0.116051.jar"));
        for (String line : new String[] {"110", "112", "113", "114"}) {
            assertTrue(historicalWorkspacePath("D:/PowerAdvantage/PA" + line + "/PowerAdvantage/build/mod.jar"));
            assertTrue(historicalWorkspacePath("D:\\PowerAdvantage\\PA" + line + "\\PowerAdvantage\\build\\mod.jar"));
        }
    }

    private static boolean historicalWorkspacePath(String path) {
        return java.util.regex.Pattern.compile("(^|[/\\\\])PA(?:110|112|113|114)([/\\\\]|$)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(path).find();
    }

    @Test
    public void explicitlyUnportedProfilesStillFailClosed() throws Exception {
        File file = File.createTempFile("unported-native116-", ".json");
        try {
            Files.write(file.toPath(), ("{\"name\":\"blocked\",\"mode\":\"packaged\","
                    + "\"minecraftVersion\":\"1.16.5\",\"portStatus\":\"UNPORTED\","
                    + "\"portBlocker\":\"missing genuine native dependency\"}").getBytes(StandardCharsets.UTF_8));
            try { WorksRunner.readNative116Profile(file); fail("Unported profile was accepted"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("not ported")); }
        } finally {
            Files.delete(file.toPath());
        }
    }

    @Test
    public void pendingNative116PinsFailBeforeAnyRuntimeIsCreated() throws Exception {
        WorksRunner.Profile profile = new com.google.gson.Gson().fromJson(
                "{\"mods\":[{\"id\":\"advantageworkstest\",\"sha256\":null}]}", WorksRunner.Profile.class);
        try { WorksRunner.requireInputPins(profile); fail("Pending candidate pins must fail closed"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("SHA-256 pin")); }
    }

    @Test
    public void historical114CannotBeLaunchedAsNative116() throws Exception {
        try {
            WorksRunner.readNative116Profile(new File("profiles/native114/packaged-current.json"));
            fail("Historical 1.14.4 profile must not launch native116");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Historical source profile"));
        }
    }

    @Test
    public void historicalQualificationCannotBeLaunchedAsNative116() throws Exception {
        try {
            WorksRunner.readNative116Profile(new File("profiles/packaged-current.json"));
            fail("Historical 1.13.2 profile must not launch native116");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Historical source profile"));
        }
    }
}
