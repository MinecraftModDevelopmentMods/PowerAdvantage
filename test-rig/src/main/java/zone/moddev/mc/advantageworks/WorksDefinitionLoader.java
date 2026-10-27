package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class WorksDefinitionLoader {
    private static final String RESOURCE = "/assets/advantageworkstest/works/works-1.15.2.json";
    private static final String STATION_SOURCE = "/assets/advantageworkstest/works/works-1.13.2.json";
    private static final Gson GSON = new GsonBuilder().create();
    private static final Set<String> ACTION_TYPES = new HashSet<>(Arrays.asList(
            "setBlock", "setAir", "clearInventory", "setInventory", "fillInventory",
            "mergeNbt", "setFluid", "offerFluid", "spawnItem", "spawnEntity", "time", "weatherClear",
            "weatherRain", "weatherThunder"));
    private static final Set<String> ASSERTION_TYPES = new HashSet<>(Arrays.asList(
            "registryBlock", "registryItem", "modLoaded", "block", "blockProperty", "tileExists",
            "inventoryEmpty", "inventoryCount", "inventoryNbtNumber", "nbtNumber", "nbtString", "fluidAmount", "energy",
            "comparatorOutput"));

    private WorksDefinitionLoader() {
    }

    static WorksDefinition load() {
        return load(RESOURCE, "1.15.2");
    }

    static WorksDefinition loadNative116() {
        return load("/assets/advantageworkstest/works/works-1.16.5.json", "1.16.5");
    }

    static WorksDefinition load(String resource, String minecraft) {
        JsonObject target = readResource(resource);
        if (target.get("format").getAsInt() != 1 || !minecraft.equals(target.get("minecraft").getAsString())
                || !STATION_SOURCE.equals(target.get("stationSource").getAsString()))
            throw new IllegalArgumentException("Unsupported native works descriptor");
        JsonObject stations = readResource(STATION_SOURCE);
        // Only the target header changes. All station cards remain the immutable source specification.
        stations.add("format", target.get("format"));
        stations.add("minecraft", target.get("minecraft"));
        stations.add("title", target.get("title"));
        WorksDefinition definition = GSON.fromJson(stations, WorksDefinition.class);
        validate(definition, minecraft);
        return definition;
    }

    private static JsonObject readResource(String resource) {
        InputStream stream = WorksDefinitionLoader.class.getResourceAsStream(resource);
        if (stream == null) throw new IllegalStateException("Missing works definition " + resource);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read works definition " + resource, ex);
        }
    }

    private static void validate(WorksDefinition definition, String minecraft) {
        if (definition == null || definition.format != 1 || !minecraft.equals(definition.minecraft)) {
            throw new IllegalArgumentException("Unsupported or missing works definition header");
        }
        Set<String> districtIds = new HashSet<>();
        for (WorksDefinition.District district : definition.districts) {
            requireId(district.id, "district");
            if (!districtIds.add(district.id)) {
                throw new IllegalArgumentException("Duplicate district " + district.id);
            }
        }

        Set<String> stationIds = new HashSet<>();
        Map<String, int[]> absoluteBounds = new HashMap<>();
        for (WorksDefinition.Station station : definition.stations) {
            requireId(station.id, "station");
            if (!station.id.matches("(?:[WSE]-\\d{2}|P-API-\\d{2})")) {
                throw new IllegalArgumentException("Station IDs must match handbook cards: " + station.id);
            }
            if (!stationIds.add(station.id)) {
                throw new IllegalArgumentException("Duplicate station " + station.id);
            }
            if (!districtIds.contains(station.district)) {
                throw new IllegalArgumentException("Unknown district " + station.district + " for " + station.id);
            }
            requireVector(station.origin, station.id + " origin");
            requireVector(station.boundsMin, station.id + " boundsMin");
            requireVector(station.boundsMax, station.id + " boundsMax");
            for (int axis = 0; axis < 3; axis++) {
                if (station.boundsMin[axis] > station.boundsMax[axis]) {
                    throw new IllegalArgumentException("Inverted bounds for " + station.id);
                }
                if (station.boundsMax[axis] - station.boundsMin[axis] > 63) {
                    throw new IllegalArgumentException("Station exceeds 64 blocks on one axis: " + station.id);
                }
            }
            int[] bounds = absoluteBounds(station);
            for (Map.Entry<String, int[]> other : absoluteBounds.entrySet()) {
                if (overlaps(bounds, other.getValue())) {
                    throw new IllegalArgumentException(station.id + " overlaps " + other.getKey());
                }
            }
            absoluteBounds.put(station.id, bounds);
            validatePlacements(station);
        }
        if (definition.stations.size() != 21) {
            throw new IllegalArgumentException("The complete works must define all 20 handbook cards and the API station; found "
                    + definition.stations.size());
        }
    }

    private static void validatePlacements(WorksDefinition.Station station) {
        for (WorksDefinition.Placement placement : station.placements) {
            validatePosition(station, placement.pos, "placement");
            requireResource(placement.block, station.id + " placement block");
            validateNbt(placement.nbt, station.id + " placement NBT");
        }
        validateActions(station, station.setupActions, "setup action");
        validateActions(station, station.startActions, "start action");
        validateActions(station, station.stopActions, "stop action");
        Set<String> assertionIds = new HashSet<>();
        for (WorksDefinition.Assertion assertion : station.assertions) {
            requireId(assertion.id, station.id + " assertion");
            if (!assertionIds.add(assertion.id)) {
                throw new IllegalArgumentException("Duplicate assertion " + station.id + "/" + assertion.id);
            }
            if (!ASSERTION_TYPES.contains(assertion.type)) {
                throw new IllegalArgumentException("Unsupported assertion type " + assertion.type
                        + " in " + station.id + "/" + assertion.id);
            }
            if (assertion.pos != null) validatePosition(station, assertion.pos, "assertion");
            if ("fluidAmount".equals(assertion.type)) {
                if (assertion.positions.isEmpty() || assertion.value == null) {
                    throw new IllegalArgumentException("Missing fluid positions/value for " + station.id + "/" + assertion.id);
                }
                Set<String> unique = new HashSet<>();
                for (int[] position : assertion.positions) {
                    validatePosition(station, position, "fluid assertion");
                    if (!unique.add(Arrays.toString(position))) {
                        throw new IllegalArgumentException("Duplicate fluid position for " + station.id + "/" + assertion.id);
                    }
                }
                requireId(assertion.nbtPath, "fluid tank NBT key");
                if (assertion.fluid != null) requireResource(assertion.fluid, "fluid identity");
            }
            if ("registryBlock".equals(assertion.type) || "block".equals(assertion.type)
                    || "blockProperty".equals(assertion.type)) {
                requireResource(assertion.block, station.id + "/" + assertion.id + " block");
            }
            if ("blockProperty".equals(assertion.type)) {
                requireId(assertion.property, "block-state property");
                requireId(assertion.expectedText, "expected block-state value");
            }
            if ("registryItem".equals(assertion.type) || "inventoryCount".equals(assertion.type)
                    || "inventoryEmpty".equals(assertion.type)) {
                if (assertion.item != null) {
                    requireResource(assertion.item, station.id + "/" + assertion.id + " item");
                }
            }
            if ("modLoaded".equals(assertion.type)) requireId(assertion.item, "mod ID");
            if ("inventoryNbtNumber".equals(assertion.type)) {
                if (assertion.slot == null) throw new IllegalArgumentException("Missing inventory slot for "
                        + station.id + "/" + assertion.id);
                requireId(assertion.nbtPath, "NBT path");
            }
            if ("nbtNumber".equals(assertion.type) || "nbtString".equals(assertion.type)) {
                requireId(assertion.nbtPath, "NBT path");
            }
            if ("nbtString".equals(assertion.type)) {
                requireId(assertion.expectedText, "expected NBT text");
            }
            if (("inventoryNbtNumber".equals(assertion.type) || "nbtNumber".equals(assertion.type)
                    || "energy".equals(assertion.type) || "comparatorOutput".equals(assertion.type))
                    && assertion.value == null) {
                throw new IllegalArgumentException("Missing expected value for " + station.id + "/" + assertion.id);
            }
        }
    }

    private static void validateActions(WorksDefinition.Station station,
                                        Iterable<WorksDefinition.Action> actions, String label) {
        for (WorksDefinition.Action action : actions) {
            String type = action.type == null ? "setBlock" : action.type;
            if (!ACTION_TYPES.contains(type)) {
                throw new IllegalArgumentException("Unsupported " + label + " type " + type
                        + " in " + station.id);
            }
            validatePosition(station, action.pos, label);
            if ("setBlock".equals(type)) requireResource(action.block, station.id + " action block");
            if ("setInventory".equals(type) || "fillInventory".equals(type)
                    || "spawnItem".equals(type)) {
                requireResource(action.item, station.id + " action item");
            }
            if ("spawnEntity".equals(type)) requireResource(action.entity, station.id + " action entity");
            if ("mergeNbt".equals(type) && action.nbt == null) {
                throw new IllegalArgumentException("Missing NBT for " + station.id + " " + label);
            }
            if ("setFluid".equals(type) || "offerFluid".equals(type)) {
                requireId(action.fluid, station.id + " action fluid");
                if (action.value < 0 || ("offerFluid".equals(type) && action.value == 0)
                        || action.value > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid fluid amount in " + station.id);
                }
            }
            if ("offerFluid".equals(type)) {
                if (action.transferLimit <= 0 || action.expectedValue == null
                        || action.expectedValue < 0 || action.expectedValue > action.transferLimit
                        || action.expectedValue > action.value) {
                    throw new IllegalArgumentException("Invalid public fluid API expectation in " + station.id);
                }
            }
            if (action.enchantment != null) {
                requireResource(action.enchantment, station.id + " action enchantment");
                if (action.level < 1) throw new IllegalArgumentException("Invalid enchantment level in " + station.id);
            }
            validateNbt(action.nbt, station.id + " " + label + " NBT");
        }
    }

    private static void validateNbt(com.google.gson.JsonObject nbt, String label) {
        if (nbt == null) return;
        try {
            JsonNbtConverter.toCompound(nbt);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed " + label, ex);
        }
    }

    private static void requireResource(String value, String label) {
        requireId(value, label);
        new ResourceLocation(value);
    }

    private static void validatePosition(WorksDefinition.Station station, int[] pos, String label) {
        requireVector(pos, station.id + " " + label);
        for (int axis = 0; axis < 3; axis++) {
            if (pos[axis] < station.boundsMin[axis] || pos[axis] > station.boundsMax[axis]) {
                throw new IllegalArgumentException(label + " escapes bounds for " + station.id);
            }
        }
    }

    private static int[] absoluteBounds(WorksDefinition.Station station) {
        return new int[]{
                station.origin[0] + station.boundsMin[0],
                station.origin[1] + station.boundsMin[1],
                station.origin[2] + station.boundsMin[2],
                station.origin[0] + station.boundsMax[0],
                station.origin[1] + station.boundsMax[1],
                station.origin[2] + station.boundsMax[2]
        };
    }

    private static boolean overlaps(int[] a, int[] b) {
        return a[0] <= b[3] && a[3] >= b[0]
                && a[1] <= b[4] && a[4] >= b[1]
                && a[2] <= b[5] && a[5] >= b[2];
    }

    private static void requireVector(int[] value, String label) {
        if (value == null || value.length != 3) {
            throw new IllegalArgumentException(label + " must contain exactly three coordinates");
        }
    }

    private static void requireId(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + label + " ID");
        }
    }
}
