package cyano.poweradvantage.api.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

/**
 * Bridges the OreSpawn 1.x per-dimension files shipped by the original
 * Advantage releases into an OreSpawn 4 provider override.
 *
 * <p>OreSpawn 1.13 imports OS2/OS3 files itself, but no longer contains the
 * OS1 bridge used by its 1.10 build. This helper intentionally handles only
 * the small, data-only OS1 format. Source files are retained unchanged, and
 * an existing modern provider or global profile always remains authoritative.</p>
 */
public final class LegacyOreSpawnConfigBridge {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String REPORT_NAME = "orespawn-os3-migration-report.json";

    private LegacyOreSpawnConfigBridge() {
    }

    public static final class Result {
        private final boolean migrated;
        private final int rules;
        private final Path source;
        private final Path target;

        private Result(boolean migrated, int rules, Path source, Path target) {
            this.migrated = migrated;
            this.rules = rules;
            this.source = source;
            this.target = target;
        }

        public boolean wasMigrated() { return migrated; }
        public int getRuleCount() { return rules; }
        public Path getSource() { return source; }
        public Path getTarget() { return target; }
    }

    /**
     * Converts {@code config/orespawn/<modid>.json} when it is an OS1 file.
     * The generated {@code config/<modid>-orespawn.json} is the normal
     * OreSpawn 4 provider-override location.
     */
    public static synchronized Result migrate(Path configDirectory, String modId) throws IOException {
        Path source = configDirectory.resolve("orespawn").resolve(modId + ".json");
        Path target = configDirectory.resolve(modId + "-orespawn.json");
        if (!Files.isRegularFile(source) || Files.exists(target)
                || Files.exists(configDirectory.resolve("orespawn-worldgen.json"))) {
            return new Result(false, 0, source, target);
        }

        JsonObject legacy = readObject(source);
        if (legacy == null || !legacy.has("dimensions") || !legacy.get("dimensions").isJsonArray()) {
            return new Result(false, 0, source, target);
        }

        JsonObject provider = emptyProvider(modId);
        JsonObject ores = provider.getAsJsonObject("ores");
        int ordinal = 0;
        for (JsonElement dimensionElement : legacy.getAsJsonArray("dimensions")) {
            if (!dimensionElement.isJsonObject()) continue;
            JsonObject dimension = dimensionElement.getAsJsonObject();
            JsonElement dimensionId = dimension.get("dimension");
            JsonArray oldOres = array(dimension, "ores");
            for (JsonElement oreElement : oldOres) {
                if (!oreElement.isJsonObject()) continue;
                JsonObject oldOre = oreElement.getAsJsonObject();
                String output = string(oldOre, "blockID", "");
                if (!isRegistryName(output)) continue;

                JsonObject ore = convertOre(oldOre, output, dimensionId);
                if (ore == null) continue;
                String path = "legacy/" + safe(output) + "_" + ordinal++;
                ores.add(modId + ":" + path, ore);
            }
        }
        if (ores.size() == 0) return new Result(false, 0, source, target);

        Files.createDirectories(configDirectory);
        writeAtomic(target, provider);
        updateReport(configDirectory.resolve(REPORT_NAME), modId, source, target, ores);
        return new Result(true, ores.size(), source, target);
    }

    private static JsonObject convertOre(JsonObject oldOre, String output, JsonElement dimensionId) {
        JsonObject placement = new JsonObject();
        placement.addProperty("enabled", true);
        placement.addProperty("min_y", clamp(integer(oldOre, "minHeight", 0), 0, 255));
        int maximum = clamp(integer(oldOre, "maxHeight", 256), 0, 256);
        if (maximum <= placement.get("min_y").getAsInt()) return null;
        placement.addProperty("max_y", maximum - 1);
        placement.addProperty("frequency", Math.max(0.0D, decimal(oldOre, "frequency", 0.5D)));
        int size = integer(oldOre, "size", 8);
        int variation = Math.max(0, integer(oldOre, "variation", 0));
        if (variation == 0) {
            placement.addProperty("quantity", clamp(size, 1, 64));
        } else {
            placement.addProperty("min_quantity", clamp(size - variation, 1, 64));
            placement.addProperty("max_quantity", clamp(size + variation - 1, 1, 64));
        }
        placement.addProperty("pattern", "default");
        placement.addProperty("height_distribution", "uniform");
        placement.addProperty("spread", 8);
        placement.addProperty("vertical_spread", clamp(variation, 1, 64));
        placement.addProperty("node_size", clamp(size, 1, 32));
        addStrings(placement, "host_blocks", "minecraft:stone");
        addStrings(placement, "host_tags", "forge:stone");
        addStrings(placement, "host_families", "sedimentary", "metamorphic",
                "igneous_intrusive", "igneous_volcanic");
        addBiomeSelectors(placement, array(oldOre, "biomes"));
        placement.add("geomes", new JsonObject());

        JsonObject ore = new JsonObject();
        ore.addProperty("block", output);
        ore.addProperty("enabled", true);
        ore.addProperty("native_generation", false);
        ore.addProperty("suppress_vanilla", false);
        ore.addProperty("retrogen", false);
        if (dimensionId != null && dimensionId.isJsonPrimitive()
                && dimensionId.getAsJsonPrimitive().isString()
                && "+".equals(dimensionId.getAsString())) {
            JsonObject selectors = new JsonObject();
            selectors.add("orespawn:all_except_nether_end", placement);
            ore.add("dimension_selectors", selectors);
            return ore;
        }
        String dimension = dimensionName(dimensionId);
        if (dimension == null) return null;
        JsonObject dimensions = new JsonObject();
        dimensions.add(dimension, placement);
        ore.add("dimensions", dimensions);
        return ore;
    }

    private static void addBiomeSelectors(JsonObject placement, JsonArray legacy) {
        JsonArray ids = new JsonArray();
        JsonArray dictionary = new JsonArray();
        for (JsonElement element : legacy) {
            if (!element.isJsonPrimitive()) continue;
            String value = element.getAsString().trim();
            if (isRegistryName(value)) ids.add(value);
            else if (value.matches("[A-Za-z_]+")) dictionary.add(value.toUpperCase(Locale.ROOT));
        }
        placement.add("biome_ids", ids);
        placement.add("excluded_biome_ids", new JsonArray());
        placement.add("biome_dictionary", dictionary);
        placement.add("excluded_biome_dictionary", new JsonArray());
    }

    private static JsonObject emptyProvider(String modId) {
        JsonObject provider = new JsonObject();
        provider.addProperty("schema_version", 4);
        provider.addProperty("provider_modid", modId);
        provider.addProperty("provider_revision", 1);
        for (String section : new String[] { "rocks", "ores", "geomes", "biome_rules",
                "terrain_dimensions", "fluid_deposits", "biome_palettes",
                "dimension_materials", "templates" }) {
            provider.add(section, new JsonObject());
        }
        return provider;
    }

    private static void updateReport(Path reportPath, String modId, Path source, Path target,
            JsonObject ores) throws IOException {
        JsonObject report = Files.isRegularFile(reportPath) ? readObject(reportPath) : null;
        if (report == null) report = new JsonObject();
        report.addProperty("format", "advantage-os1-to-orespawn4-provider");
        JsonObject migrations = report.has("migrations") && report.get("migrations").isJsonObject()
                ? report.getAsJsonObject("migrations") : new JsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("os1_config_source", source.toAbsolutePath().toString());
        entry.addProperty("provider_override", target.toAbsolutePath().toString());
        JsonArray rules = new JsonArray();
        for (Map.Entry<String, JsonElement> rule : ores.entrySet()) rules.add(rule.getKey());
        entry.add("rules", rules);
        entry.addProperty("source_retained", true);
        migrations.add(modId, entry);
        report.add("migrations", migrations);
        writeAtomic(reportPath, report);
    }

    private static JsonObject readObject(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement value = new JsonParser().parse(reader);
            return value.isJsonObject() ? value.getAsJsonObject() : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static void writeAtomic(Path destination, JsonObject value) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = destination.resolveSibling(destination.getFileName().toString() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static void addStrings(JsonObject object, String key, String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        object.add(key, array);
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException malformed) { return fallback; }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException malformed) { return fallback; }
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        try { return object.has(key) ? object.get(key).getAsDouble() : fallback; }
        catch (RuntimeException malformed) { return fallback; }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String dimensionName(JsonElement value) {
        try {
            int id = value == null ? 0 : value.getAsInt();
            if (id == 0) return "minecraft:overworld";
            if (id == -1) return "minecraft:the_nether";
            if (id == 1) return "minecraft:the_end";
        } catch (RuntimeException ignored) {
            // Unknown dimensions are deliberately not guessed during migration.
        }
        return null;
    }

    private static boolean isRegistryName(String value) {
        return value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static String safe(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
