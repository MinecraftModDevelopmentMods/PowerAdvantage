package cyano.poweradvantage.api.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports the simple scalar values used by the 1.10/1.12 Forge configuration
 * format into a Forge 1.13 TOML file. The legacy file is never changed.
 */
public final class LegacyConfigMigration {
    private static final Pattern CATEGORY = Pattern.compile("^\\s*\\\"?([^\\\"{]+?)\\\"?\\s*\\{\\s*$");
    private static final Pattern PROPERTY = Pattern.compile("^\\s*([BISD]):([^=]+)=(.*)$");

    private LegacyConfigMigration() {}

    public enum ValueType {
        STRING, BOOLEAN, INTEGER, DOUBLE
    }

    public static final class Mapping {
        private final String legacyPath;
        private final String tomlSection;
        private final String tomlKey;
        private final ValueType type;

        private Mapping(String legacyCategory, String legacyKey, String tomlSection,
                String tomlKey, ValueType type) {
            this.legacyPath = key(legacyCategory, legacyKey);
            this.tomlSection = tomlSection;
            this.tomlKey = tomlKey;
            this.type = type;
        }

        public static Mapping of(String legacyCategory, String legacyKey, String tomlSection,
                String tomlKey, ValueType type) {
            return new Mapping(legacyCategory, legacyKey, tomlSection, tomlKey, type);
        }
    }

    public static final class Result {
        private final boolean migrated;
        private final int importedValues;
        private final Path legacyFile;
        private final Path targetFile;
        private final Path reportFile;

        private Result(boolean migrated, int importedValues, Path legacyFile, Path targetFile, Path reportFile) {
            this.migrated = migrated;
            this.importedValues = importedValues;
            this.legacyFile = legacyFile;
            this.targetFile = targetFile;
            this.reportFile = reportFile;
        }

        public boolean wasMigrated() { return migrated; }
        public int getImportedValues() { return importedValues; }
        public Path getLegacyFile() { return legacyFile; }
        public Path getTargetFile() { return targetFile; }
        public Path getReportFile() { return reportFile; }
    }

    public static Result migrate(Path configDirectory, String modId, Mapping... mappings) throws IOException {
        Path legacyFile = configDirectory.resolve(modId + ".cfg");
        Path targetFile = configDirectory.resolve(modId + "-common.toml");
        Path reportFile = configDirectory.resolve(modId + "-legacy-config-migration.txt");
        if (!Files.isRegularFile(legacyFile) || Files.exists(targetFile)) {
            return new Result(false, 0, legacyFile, targetFile, reportFile);
        }

        Files.createDirectories(configDirectory);
        Map<String, String> legacyValues = readLegacyValues(legacyFile);
        Map<String, List<String>> sections = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        report.add("Advantage legacy configuration migration");
        report.add("Mod: " + modId);
        report.add("Migrated at: " + Instant.now().toString());
        report.add("Source retained: " + legacyFile.toAbsolutePath());
        report.add("Target created: " + targetFile.toAbsolutePath());
        report.add("");

        int imported = 0;
        for (Mapping mapping : mappings) {
            String raw = legacyValues.get(mapping.legacyPath);
            if (raw == null) continue;
            try {
                String tomlValue = toToml(raw, mapping.type);
                sections.computeIfAbsent(mapping.tomlSection, ignored -> new ArrayList<>())
                        .add(mapping.tomlKey + " = " + tomlValue);
                report.add("IMPORTED " + mapping.legacyPath + " -> "
                        + mapping.tomlSection + "." + mapping.tomlKey + " = " + raw);
                imported++;
            } catch (IllegalArgumentException exception) {
                report.add("SKIPPED invalid value for " + mapping.legacyPath + ": " + raw);
            }
        }

        List<String> toml = new ArrayList<>();
        toml.add("# Generated once from " + modId + ".cfg by the Advantage 1.13 migration.");
        toml.add("# The original cfg file has deliberately been retained.");
        toml.add("");
        for (Map.Entry<String, List<String>> section : sections.entrySet()) {
            toml.add("[" + section.getKey() + "]");
            toml.addAll(section.getValue());
            toml.add("");
        }

        writeAtomically(targetFile, toml);
        report.add("");
        report.add("Imported values: " + imported);
        report.add("The migration is idempotent: this importer does not run while the TOML file exists.");
        writeAtomically(reportFile, report);
        return new Result(true, imported, legacyFile, targetFile, reportFile);
    }

    private static Map<String, String> readLegacyValues(Path legacyFile) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        String category = "";
        for (String line : Files.readAllLines(legacyFile, StandardCharsets.UTF_8)) {
            Matcher categoryMatcher = CATEGORY.matcher(line);
            if (categoryMatcher.matches()) {
                category = categoryMatcher.group(1).trim();
                continue;
            }
            if (line.trim().equals("}")) {
                category = "";
                continue;
            }
            Matcher propertyMatcher = PROPERTY.matcher(line);
            if (!category.isEmpty() && propertyMatcher.matches()) {
                values.put(key(category, propertyMatcher.group(2).trim()), propertyMatcher.group(3).trim());
            }
        }
        return values;
    }

    private static String toToml(String raw, ValueType type) {
        switch (type) {
            case BOOLEAN:
                if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                    throw new IllegalArgumentException("not a boolean");
                }
                return raw.toLowerCase(Locale.ROOT);
            case INTEGER:
                return Integer.toString(Integer.parseInt(raw));
            case DOUBLE:
                return Double.toString(Double.parseDouble(raw));
            case STRING:
            default:
                return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    private static String key(String category, String property) {
        return category.trim().toLowerCase(Locale.ROOT) + "/" + property.trim().toLowerCase(Locale.ROOT);
    }

    private static void writeAtomically(Path target, List<String> lines) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.write(temporary, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
