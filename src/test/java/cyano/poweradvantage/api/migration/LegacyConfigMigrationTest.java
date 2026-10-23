package cyano.poweradvantage.api.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cyano.poweradvantage.api.migration.LegacyConfigMigration.Mapping;
import cyano.poweradvantage.api.migration.LegacyConfigMigration.ValueType;

public class LegacyConfigMigrationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void importsOnceAndRetainsLegacyFile() throws Exception {
        Path config = temporary.newFolder("config").toPath();
        Path legacy = config.resolve("example.cfg");
        Files.write(legacy, Arrays.asList(
                "options {",
                "    B:enabled=false",
                "    I:count=17",
                "    S:name=old value",
                "}",
                "\"other values\" {",
                "    S:ratio=0.25",
                "}"), StandardCharsets.UTF_8);

        LegacyConfigMigration.Result first = LegacyConfigMigration.migrate(config, "example",
                Mapping.of("options", "enabled", "options", "enabled", ValueType.BOOLEAN),
                Mapping.of("options", "count", "options", "count", ValueType.INTEGER),
                Mapping.of("options", "name", "options", "name", ValueType.STRING),
                Mapping.of("other values", "ratio", "energy", "ratio", ValueType.DOUBLE));

        assertTrue(first.wasMigrated());
        assertEquals(4, first.getImportedValues());
        assertTrue(Files.exists(legacy));
        assertTrue(Files.exists(first.getReportFile()));
        String toml = new String(Files.readAllBytes(first.getTargetFile()), StandardCharsets.UTF_8);
        assertTrue(toml.contains("enabled = false"));
        assertTrue(toml.contains("count = 17"));
        assertTrue(toml.contains("name = \"old value\""));
        assertTrue(toml.contains("ratio = 0.25"));

        byte[] originalTarget = Files.readAllBytes(first.getTargetFile());
        LegacyConfigMigration.Result second = LegacyConfigMigration.migrate(config, "example",
                Mapping.of("options", "enabled", "options", "enabled", ValueType.BOOLEAN));
        assertFalse(second.wasMigrated());
        assertTrue(Arrays.equals(originalTarget, Files.readAllBytes(first.getTargetFile())));
    }
}
