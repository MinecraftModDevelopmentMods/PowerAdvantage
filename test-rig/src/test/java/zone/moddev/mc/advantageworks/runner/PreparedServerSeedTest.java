package zone.moddev.mc.advantageworks.runner;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PreparedServerSeedTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String LAUNCHER = "forge-1.16.5-36.2.34.jar";
    private static final String MINECRAFT = "minecraft_server.1.16.5.jar";
    private static final String LIBRARY = "libraries/native/runtime.jar";

    private WorksRunner.Profile profile() {
        return new Gson().fromJson("{\"name\":\"seed-test\",\"mode\":\"packaged\","
                + "\"minecraftVersion\":\"1.16.5\",\"portStatus\":\"PORTED_UNQUALIFIED\","
                + "\"qualificationStatus\":\"UNQUALIFIED\",\"mods\":[],"
                + "\"forge\":{\"version\":\"1.16.5-36.2.34\"}}", WorksRunner.Profile.class);
    }

    private Path seed() throws Exception {
        Path root = temporary.newFolder().toPath();
        Files.createDirectories(root.resolve("libraries/native"));
        Files.write(root.resolve(LAUNCHER), new byte[] {1, 2});
        Files.write(root.resolve(MINECRAFT), new byte[] {3, 4});
        Files.write(root.resolve(LIBRARY), new byte[] {5, 6});
        seal(root);
        return root;
    }

    private void seal(Path root) throws Exception {
        JsonObject files = new JsonObject();
        for (String name : Arrays.asList(LAUNCHER, MINECRAFT, LIBRARY)) {
            Path path = root.resolve(name);
            JsonObject entry = new JsonObject();
            entry.addProperty("sha256", hash(path));
            entry.addProperty("size", Files.size(path));
            files.add(name, entry);
        }
        JsonObject inventory = new JsonObject();
        inventory.add("files", files);
        Files.write(root.resolve(WorksRunner.PreparedServerSeed.INVENTORY),
                inventory.toString().getBytes(StandardCharsets.UTF_8));
    }

    private WorksRunner.PreparedServerSeed load(Path root) throws Exception {
        Path inventory = root.resolve(WorksRunner.PreparedServerSeed.INVENTORY);
        return new WorksRunner.PreparedServerSeed(root, inventory, hash(inventory), LAUNCHER, MINECRAFT);
    }

    private Path runtime() throws Exception {
        Path runtime = temporary.newFolder().toPath();
        Files.createDirectories(runtime.resolve("advantage-works-results"));
        return runtime;
    }

    private static String hash(Path path) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))) {
            text.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
        }
        return text.toString();
    }

    private interface Action { void run() throws Exception; }

    private static Exception rejected(Action action) throws Exception {
        try { action.run(); }
        catch (Exception expected) { return expected; }
        throw new AssertionError("Invalid prepared seed was accepted");
    }

    @Test public void seedOptionRequiresAllThreeExplicitPinsAndRejectsDuplicates() throws Exception {
        String[] prefix = {"--profile", "profile.json"};
        assertNull(WorksRunner.Arguments.parse(prefix).preparedServerSeed);
        for (String option : Arrays.asList("--prepared-server-seed", "--prepared-server-seed-inventory", "--prepared-server-seed-sha256")) {
            rejected(() -> WorksRunner.Arguments.parse(new String[] {"--profile", "p", option, "value"}));
        }
        rejected(() -> WorksRunner.Arguments.parse(new String[] {"--profile", "p",
                "--prepared-server-seed", "one", "--prepared-server-seed", "two"}));
    }

    @Test public void optionAbsentPreservesTheExistingPinnedInstallerBranch() throws Exception {
        Path rig = temporary.newFolder().toPath();
        Path target = runtime();
        Path installer = rig.resolve("installer.jar");
        Files.write(installer, new byte[] {7});
        Files.write(target.resolve(LAUNCHER), new byte[] {8});
        WorksRunner.Profile profile = new Gson().fromJson("{\"forge\":{\"version\":\"1.16.5-36.2.34\","
                + "\"installerPath\":\"installer.jar\",\"sha256\":\"" + hash(installer) + "\"}}", WorksRunner.Profile.class);
        assertNull(WorksRunner.preparedServerSeed(rig.toFile(), profile,
                WorksRunner.Arguments.parse(new String[] {"--profile", "p"})));
        WorksRunner.preparePackagedServer(rig.toFile(), target.toFile(), profile);
        assertArrayEquals(new byte[] {8}, Files.readAllBytes(target.resolve(LAUNCHER)));
        Files.write(installer, new byte[] {9});
        assertTrue(rejected(() -> WorksRunner.preparePackagedServer(rig.toFile(), target.toFile(), profile))
                .getMessage().contains("installer SHA-256 mismatch"));
    }

    @Test public void preparedPathStagesExactCodeAndRawReceiptWithoutInstallerConfiguration() throws Exception {
        Path source = seed(), target = runtime();
        WorksRunner.Profile profile = profile();
        profile.preparedSeed = load(source);
        WorksRunner.preparePackagedServer(temporary.getRoot(), target.toFile(), profile);
        for (String name : Arrays.asList(LAUNCHER, MINECRAFT, LIBRARY, WorksRunner.PreparedServerSeed.INVENTORY)) {
            assertArrayEquals(Files.readAllBytes(source.resolve(name)), Files.readAllBytes(target.resolve(name)));
        }
        profile.preparedSeed.verifySource();
    }

    @Test public void missingCorruptAndUnexpectedCodeNeverFallBackToInstaller() throws Exception {
        for (String mutation : Arrays.asList("missing", "corrupt", "extra-root", "extra-library")) {
            Path source = seed();
            if (mutation.equals("missing")) Files.delete(source.resolve(LIBRARY));
            if (mutation.equals("corrupt")) Files.write(source.resolve(LIBRARY), new byte[] {6, 5});
            if (mutation.equals("extra-root")) Files.write(source.resolve("installer.jar"), new byte[] {1});
            if (mutation.equals("extra-library")) Files.write(source.resolve("libraries/extra.jar"), new byte[] {1});
            rejected(() -> load(source));
        }
    }

    @Test public void sourceWorldsAndEmptyUninventoriedDirectoriesAreRejected() throws Exception {
        Path source = seed();
        Files.createDirectory(source.resolve("world"));
        rejected(() -> load(source));
        Files.delete(source.resolve("world"));
        Files.createDirectory(source.resolve("libraries/unlisted"));
        rejected(() -> load(source));
    }

    @Test public void runtimeWorldAndConfigRemainOutsideCodeCustody() throws Exception {
        WorksRunner.PreparedServerSeed seal = load(seed());
        Path target = runtime();
        Files.createDirectories(target.resolve("world"));
        Files.write(target.resolve("world/level.dat"), new byte[] {1});
        Files.write(target.resolve("server.properties"), "level-type=DEFAULT".getBytes(StandardCharsets.UTF_8));
        seal.stage(target.toFile());
        Files.write(target.resolve("world/level.dat"), new byte[] {2});
        seal.verifyTree(target, false);
        assertEquals("level-type=DEFAULT", new String(Files.readAllBytes(target.resolve("server.properties")), StandardCharsets.UTF_8));
    }

    @Test public void strictAuditsRejectEveryKindOfStagedCodeDrift() throws Exception {
        for (String mutation : Arrays.asList("missing", "corrupt", "extra-root", "extra-library", "receipt")) {
            WorksRunner.PreparedServerSeed seal = load(seed());
            Path target = runtime();
            seal.stage(target.toFile());
            if (mutation.equals("missing")) Files.delete(target.resolve(LIBRARY));
            if (mutation.equals("corrupt")) Files.write(target.resolve(LIBRARY), new byte[] {6, 5});
            if (mutation.equals("extra-root")) Files.write(target.resolve("installer.jar"), new byte[] {1});
            if (mutation.equals("extra-library")) Files.write(target.resolve("libraries/extra.jar"), new byte[] {1});
            if (mutation.equals("receipt")) Files.write(target.resolve(WorksRunner.PreparedServerSeed.INVENTORY), new byte[] {1});
            rejected(() -> seal.verifyTree(target, false));
        }
    }

    @Test public void seedAndReceiptMutationsAfterStagingFailBeforeTheNextBoot() throws Exception {
        for (String mutation : Arrays.asList("source-code", "source-receipt")) {
            Path source = seed(), target = runtime();
            WorksRunner.Profile profile = profile();
            profile.preparedSeed = load(source);
            profile.preparedSeed.stage(target.toFile());
            WorksRunner.auditPreparedServer(profile, target.toFile(), "first-before");
            Files.write(source.resolve(mutation.equals("source-code") ? LIBRARY : WorksRunner.PreparedServerSeed.INVENTORY), new byte[] {0});
            rejected(() -> WorksRunner.auditPreparedServer(profile, target.toFile(), "next-before"));
            assertTrue(new String(Files.readAllBytes(target.resolve("advantage-works-results/prepared-server-seed-next-before.json")),
                    StandardCharsets.UTF_8).contains("FAIL"));
        }
    }

    @Test public void invalidPathsHashesAndSizesFailClosed() throws Exception {
        for (String path : Arrays.asList("../escape.jar", "/absolute.jar", "libraries/../escape.jar", "libraries//a.jar",
                "libraries/./a.jar", "libraries\\a.jar", "C:/a.jar", "mods/a.jar", "other.jar")) {
            Path source = seed();
            writeInventory(source, "{\"files\":{\"" + path.replace("\\", "\\\\") + "\":{\"sha256\":\""
                    + hash(source.resolve(LIBRARY)) + "\",\"size\":2}}}");
            rejected(() -> load(source));
        }
        for (String size : Arrays.asList("-1", "1.5", "\"2\"", "9223372036854775808")) {
            Path source = seed();
            writeInventory(source, "{\"files\":{\"libraries/a.jar\":{\"sha256\":\"" + hash(source.resolve(LIBRARY))
                    + "\",\"size\":" + size + "}}}");
            rejected(() -> load(source));
        }
    }

    @Test public void duplicateCaseFoldedPathsAndDuplicateJsonFieldsAreRejected() throws Exception {
        Path source = seed();
        String value = "{\"sha256\":\"" + hash(source.resolve(LIBRARY)) + "\",\"size\":2}";
        for (String body : Arrays.asList("{\"files\":{\"libraries/a.jar\":" + value + ",\"libraries/a.jar\":" + value + "}}",
                "{\"files\":{\"libraries/a.jar\":" + value + ",\"libraries/A.jar\":" + value + "}}",
                "{\"files\":{},\"files\":{}}")) {
            writeInventory(source, body);
            rejected(() -> load(source));
        }
    }

    @Test public void emptyOversizedAndWronglyPinnedInventoriesAreRejected() throws Exception {
        Path source = seed();
        Path receipt = source.resolve(WorksRunner.PreparedServerSeed.INVENTORY);
        rejected(() -> new WorksRunner.PreparedServerSeed(source, receipt,
                "0000000000000000000000000000000000000000000000000000000000000000", LAUNCHER, MINECRAFT));
        writeInventory(source, "{\"files\":{}}");
        rejected(() -> load(source));
        Files.write(receipt, new byte[WorksRunner.PreparedServerSeed.MAX_INVENTORY_BYTES + 1]);
        rejected(() -> load(source));
    }

    @Test public void timestampOnlyZipChangesAreNotNormalizedAway() throws Exception {
        Path source = seed();
        writeJar(source.resolve(LIBRARY), 1767225600000L);
        seal(source);
        WorksRunner.PreparedServerSeed prepared = load(source);
        Path target = runtime();
        prepared.stage(target.toFile());
        writeJar(target.resolve(LIBRARY), 1767225604000L);
        rejected(() -> prepared.verifyTree(target, false));
    }

    @Test public void overlapAndPreexistingCodeCannotOverwriteASeedOrRuntime() throws Exception {
        Path source = seed();
        WorksRunner.PreparedServerSeed seal = load(source);
        rejected(() -> seal.stage(source.toFile()));
        rejected(() -> seal.stage(source.resolve("destination").toFile()));
        Path target = runtime();
        Files.write(target.resolve(LAUNCHER), new byte[] {99});
        rejected(() -> seal.stage(target.toFile()));
        assertArrayEquals(new byte[] {99}, Files.readAllBytes(target.resolve(LAUNCHER)));
        seal.verifySource();
    }

    @Test public void failedAttemptKeepsThePrimaryFailureAndPerformsPostCloseAudit() throws Exception {
        WorksRunner.Profile profile = profile();
        profile.preparedSeed = load(seed());
        Path target = runtime();
        profile.preparedSeed.stage(target.toFile());
        Files.write(target.resolve(LIBRARY), new byte[] {6, 5});
        FakeProcess process = new FakeProcess();
        profile.preparedSeed.processes.add(process);
        Exception primary = new Exception("operation failed");
        profile.preparedSeed.closeAfterFailure(profile, target.toFile(), primary);
        assertFalse(process.alive);
        assertTrue(process.output.toString("UTF-8").contains("stop"));
        assertEquals("operation failed", primary.getMessage());
        assertEquals(1, primary.getSuppressed().length);
        assertTrue(new String(Files.readAllBytes(target.resolve("advantage-works-results/prepared-server-seed-failure-after-close.json")),
                StandardCharsets.UTF_8).contains("FAIL"));
    }

    @Test public void validateOnlyRejectsAnInvalidSeedBeforeCreatingAnyRuntime() throws Exception {
        Path rig = temporary.newFolder().toPath(), source = seed();
        Files.createDirectories(rig.resolve("profiles"));
        Files.createDirectories(rig.resolve("src/main/resources/assets/advantageworkstest"));
        Files.write(rig.resolve("build.gradle"), new byte[0]);
        Path profileFile = rig.resolve("profiles/native.json");
        Files.write(profileFile, new Gson().toJson(profile()).getBytes(StandardCharsets.UTF_8));
        Path receipt = source.resolve(WorksRunner.PreparedServerSeed.INVENTORY);
        String pin = hash(receipt);
        Files.write(source.resolve(LIBRARY), new byte[] {0});
        rejected(() -> WorksRunner.main(new String[] {"--profile", profileFile.toString(), "--validate-only",
                "--prepared-server-seed", source.toString(), "--prepared-server-seed-inventory", receipt.toString(),
                "--prepared-server-seed-sha256", pin}));
        assertFalse(Files.exists(rig.resolve("build/works-native116")));
    }

    private void writeInventory(Path source, String content) throws Exception {
        Files.write(source.resolve(WorksRunner.PreparedServerSeed.INVENTORY), content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeJar(Path path, long time) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            JarEntry entry = new JarEntry("payload");
            entry.setTime(time);
            output.putNextEntry(entry);
            output.write(new byte[] {1, 2, 3});
            output.closeEntry();
        }
    }

    private static final class FakeProcess extends Process {
        boolean alive = true;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        @Override public OutputStream getOutputStream() { return output; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { alive = false; return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { alive = false; return true; }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy() { alive = false; }
        @Override public Process destroyForcibly() { alive = false; return this; }
        @Override public boolean isAlive() { return alive; }
    }
}
