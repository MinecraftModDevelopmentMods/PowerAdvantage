package zone.moddev.mc.advantageworks;

import com.google.gson.JsonObject;
import cyano.poweradvantage.api.fluid.FluidNetworkApi;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import cyano.poweradvantage.api.migration.LegacyMigrationApi;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockFlowingFluid;
import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.IProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.fluid.Fluid;
import cyano.poweradvantage.api.fluid.FluidStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class WorksController {
    private static final WorksController INSTANCE = new WorksController();
    private static final int STATUS_NEUTRAL = 8;
    private static final int STATUS_PASS = 5;
    private static final int STATUS_FAIL = 14;
    private static final int STATUS_ATTENTION = 4;

    private WorksDefinition definition;
    private final Map<String, WorksDefinition.Station> stations = new LinkedHashMap<>();
    private final Map<String, String> statuses = new LinkedHashMap<>();
    private final WorksResultWriter resultWriter = new WorksResultWriter();
    private String activeExclusiveStation;

    static WorksController getInstance() {
        return INSTANCE;
    }

    synchronized void loadDefinition() {
        if (definition != null) return;
        definition = WorksDefinitionLoader.load();
        for (WorksDefinition.Station station : definition.stations) {
            stations.put(station.id.toUpperCase(Locale.ROOT), station);
            statuses.put(station.id, "NOT_BUILT");
        }
    }

    void requireAuthorizedWorld(MinecraftServer server) throws CommandException {
        File marker = new File("advantage-works.allow");
        if (!marker.isFile() || !server.getFolderName().startsWith("AdvantageWorks")) {
            throw commandError("Refusing to alter an unmarked world. Expected AdvantageWorks level and advantage-works.allow.");
        }
    }

    synchronized String build(MinecraftServer server, String target) throws CommandException {
        loadDefinition();
        WorldServer world = overworld(server);
        if ("all".equalsIgnoreCase(target)) buildConcourse(world);
        List<WorksDefinition.Station> selected = select(target, true);
        int built = 0;
        int skipped = 0;
        for (WorksDefinition.Station station : selected) {
            if (!requirementsPresent(station)) {
                statuses.put(station.id, "SKIP");
                setStatusBlock(world, station, STATUS_ATTENTION);
                skipped++;
                continue;
            }
            resetStation(world, station);
            statuses.put(station.id, "READY");
            built++;
        }
        world.setSpawnPoint(new BlockPos(0, 4, 0));
        return "Advantage Works built " + built + " station(s); skipped " + skipped + " for missing mods.";
    }

    synchronized String reset(MinecraftServer server, String target) throws CommandException {
        List<WorksDefinition.Station> selected = select(target, true);
        WorldServer world = overworld(server);
        int reset = 0;
        for (WorksDefinition.Station station : selected) {
            if (!requirementsPresent(station)) continue;
            setStationChunksForced(world, station, false);
            resetStation(world, station);
            statuses.put(station.id, "READY");
            if (station.id.equals(activeExclusiveStation)) activeExclusiveStation = null;
            reset++;
        }
        return "Reset " + reset + " station(s).";
    }

    synchronized String start(MinecraftServer server, String target) throws CommandException {
        WorksDefinition.Station station = oneStation(target);
        if (!requirementsPresent(station)) {
            statuses.put(station.id, "SKIP");
            return station.id + " skipped because required mods are absent.";
        }
        if (station.exclusive && activeExclusiveStation != null
                && !station.id.equals(activeExclusiveStation)) {
            throw commandError("Stop exclusive station " + activeExclusiveStation + " first.");
        }
        WorldServer world = overworld(server);
        setStationChunksForced(world, station, true);
        applyActions(world, station, station.startActions);
        if (station.exclusive) activeExclusiveStation = station.id;
        statuses.put(station.id, "RUNNING");
        setStatusBlock(world, station, STATUS_NEUTRAL);
        return "Started " + station.id + " - " + station.name;
    }

    synchronized String stop(MinecraftServer server, String target) throws CommandException {
        WorksDefinition.Station station = oneStation(target);
        WorldServer world = overworld(server);
        if (requirementsPresent(station)) {
            applyActions(world, station, station.stopActions);
            removeTaggedEntities(world, station);
            setStationChunksForced(world, station, false);
        }
        if (station.id.equals(activeExclusiveStation)) activeExclusiveStation = null;
        statuses.put(station.id, "STOPPED");
        return "Stopped " + station.id + " - " + station.name;
    }

    private static void setStationChunksForced(WorldServer world,
                                                WorksDefinition.Station station,
                                                boolean forced) {
        int minChunkX = (station.origin[0] + station.boundsMin[0]) >> 4;
        int maxChunkX = (station.origin[0] + station.boundsMax[0]) >> 4;
        int minChunkZ = (station.origin[2] + station.boundsMin[2]) >> 4;
        int maxChunkZ = (station.origin[2] + station.boundsMax[2]) >> 4;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                world.func_212414_b(chunkX, chunkZ, forced);
            }
        }
    }

    synchronized String check(MinecraftServer server, String target) throws CommandException {
        List<WorksDefinition.Station> selected = select(target, true);
        WorldServer world = overworld(server);
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (WorksDefinition.Station station : selected) {
            String status = checkStation(world, station);
            summary.put(status, summary.containsKey(status) ? summary.get(status) + 1 : 1);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("stationStatuses", new LinkedHashMap<>(statuses));
        resultWriter.write("all", "summary", payload);
        return "Advantage Works check: " + summary;
    }

    synchronized String status(String target) throws CommandException {
        if ("all".equalsIgnoreCase(target)) return statuses.toString();
        WorksDefinition.Station station = oneStation(target);
        return station.id + " " + station.name + ": " + statuses.get(station.id);
    }

    synchronized String checkpoint(MinecraftServer server, String target, String name) throws CommandException {
        WorksDefinition.Station station = oneStation(target);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkpoint", name);
        payload.put("worldTime", overworld(server).getGameTime());
        payload.put("snapshot", snapshot(overworld(server), station));
        File result = resultWriter.write(station.id, "checkpoint-" + name, payload);
        return "Checkpoint written for " + station.id + " to " + result.getPath();
    }

    synchronized String sampleWorldgen(MinecraftServer server, int radius) throws CommandException {
        if (radius < 1 || radius > 16) {
            throw commandError("Worldgen sample radius must be between 1 and 16 chunks");
        }
        WorldServer world = overworld(server);
        Map<String, String> expectedBiomes = new LinkedHashMap<>();
        expectedBiomes.put("poweradvantage:crude_oil", "DESERT");
        expectedBiomes.put("mineralogy:crude_oil", "OCEAN");
        expectedBiomes.put("electricadvantage:li_ore", null);
        expectedBiomes.put("electricadvantage:sulfur_ore", null);
        expectedBiomes.put("mineralogy:sulfur_ore", null);

        Set<Long> chunks = new LinkedHashSet<>();
        Map<String, String> centers = new LinkedHashMap<>();
        BlockPos spawn = world.getSpawnPoint();
        addChunkSquare(chunks, spawn.getX() >> 4, spawn.getZ() >> 4, radius);
        centers.put("spawn", (spawn.getX() >> 4) + "," + (spawn.getZ() >> 4));
        addBiomeSample(world, chunks, centers, BiomeDictionary.Type.getType("SANDY"), "DESERT", radius,
                BiomeDictionary.Type.getType("BEACH"), BiomeDictionary.Type.getType("MESA"));
        addBiomeSample(world, chunks, centers, BiomeDictionary.Type.getType("OCEAN"), "OCEAN", radius);

        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, Long> violations = new LinkedHashMap<>();
        Map<String, Long> boundaryBlocks = new LinkedHashMap<>();
        for (String id : expectedBiomes.keySet()) {
            counts.put(id, 0L);
            if (expectedBiomes.get(id) != null) {
                violations.put(id, 0L);
                boundaryBlocks.put(id, 0L);
            }
        }

        // Populate a halo first; large imported OS1 clusters can cross a sampled chunk boundary.
        Set<Long> populationChunks = new LinkedHashSet<>();
        for (Long packed : chunks) {
            int chunkX = (int) (packed >> 32);
            int chunkZ = (int) (long) packed;
            addChunkSquare(populationChunks, chunkX, chunkZ, 2);
        }
        for (Long packed : populationChunks) {
            int chunkX = (int) (packed >> 32);
            int chunkZ = (int) (long) packed;
            world.getChunk(chunkX, chunkZ);
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Long packed : chunks) {
            int chunkX = (int) (packed >> 32);
            int chunkZ = (int) (long) packed;
            Chunk chunk = world.getChunk(chunkX, chunkZ);
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            Biome chunkCenterBiome = world.getBiome(
                    new BlockPos(minX + 8, world.getSeaLevel(), minZ + 8));
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    cursor.setPos(minX + localX, 0, minZ + localZ);
                    for (int y = 0; y <= 96; y++) {
                        cursor.setY(y);
                        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(
                                chunk.getBlockState(cursor).getBlock());
                        String name = id == null ? null : id.toString();
                        if (name == null || !counts.containsKey(name)) continue;
                        counts.put(name, counts.get(name) + 1L);
                        String expectedBiome = expectedBiomes.get(name);
                        if (expectedBiome != null && !matchesExpectedBiome(expectedBiome, chunkCenterBiome)) {
                            violations.put(name, violations.get(name) + 1L);
                        } else if (expectedBiome != null
                                && !matchesExpectedBiome(expectedBiome, world.getBiome(cursor))) {
                            boundaryBlocks.put(name, boundaryBlocks.get(name) + 1L);
                        }
                        }
                    }
                }
            }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("radius", radius);
        payload.put("chunksScanned", chunks.size());
        payload.put("centers", centers);
        payload.put("counts", counts);
        payload.put("biomeViolations", violations);
        payload.put("biomeBoundaryBlocks", boundaryBlocks);
        File result = resultWriter.write("worldgen", "sample", payload);
        return "Worldgen sample wrote " + chunks.size() + " chunks to " + result.getPath()
                + ": " + counts;
    }

    private static void addBiomeSample(WorldServer world, Set<Long> chunks,
                                       Map<String, String> centers,
                                       BiomeDictionary.Type type, String label, int radius,
                                       BiomeDictionary.Type... excludedTypes) {
        BlockPos origin = world.getSpawnPoint();
        BlockPos located = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        for (int offsetZ = -256; offsetZ <= 256; offsetZ += 4) {
            for (int offsetX = -256; offsetX <= 256; offsetX += 4) {
                BlockPos candidate = new BlockPos(
                        (originChunkX + offsetX) << 4, world.getSeaLevel(),
                        (originChunkZ + offsetZ) << 4);
                Biome biome = world.getBiome(candidate);
                if (!BiomeDictionary.hasType(biome, type)
                        || hasAnyBiomeType(biome, excludedTypes)) continue;
                double distance = candidate.distanceSq(origin);
                if (distance < bestDistance) {
                    located = candidate;
                    bestDistance = distance;
                }
            }
        }
        if (located == null) {
            centers.put(label, "NOT_FOUND");
            return;
        }
        int chunkX = located.getX() >> 4;
        int chunkZ = located.getZ() >> 4;
        centers.put(label, chunkX + "," + chunkZ);
        addChunkSquare(chunks, chunkX, chunkZ, radius);
    }

    private static boolean matchesExpectedBiome(String expected, Biome biome) {
        if ("DESERT".equals(expected)) {
            return BiomeDictionary.hasType(biome, BiomeDictionary.Type.getType("SANDY"))
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.getType("BEACH"))
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.getType("MESA"));
        }
        return BiomeDictionary.hasType(biome, BiomeDictionary.Type.getType(expected));
    }

    private static boolean hasAnyBiomeType(Biome biome, BiomeDictionary.Type... types) {
        for (BiomeDictionary.Type type : types) {
            if (BiomeDictionary.hasType(biome, type)) return true;
        }
        return false;
    }

    private static void addChunkSquare(Set<Long> chunks, int centerX, int centerZ, int radius) {
        for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
            for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
                chunks.add((((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL));
            }
        }
    }
    List<String> stationIdsWithAll() {
        loadDefinition();
        List<String> ids = new ArrayList<>();
        ids.add("all");
        ids.add("power");
        ids.add("steam");
        ids.add("electric");
        ids.addAll(stations.keySet());
        return ids;
    }

    private String checkStation(WorldServer world, WorksDefinition.Station station) {
        if (!requirementsPresent(station)) {
            statuses.put(station.id, "SKIP");
            setStatusBlock(world, station, STATUS_ATTENTION);
            writeStationResult(world, station, "SKIP", Collections.<Map<String, Object>>emptyList());
            return "SKIP";
        }
        List<Map<String, Object>> assertionResults = new ArrayList<>();
        boolean failed = false;
        boolean expectedFailure = false;
        boolean unexpectedPass = false;
        for (WorksDefinition.Assertion assertion : station.assertions) {
            if (!requirementsPresent(assertion.requiredMods) || !optionalTargetPresent(assertion)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", assertion.id);
                result.put("type", assertion.type);
                result.put("status", "SKIP");
                result.put("detail", "optional integration is absent");
                assertionResults.add(result);
                continue;
            }
            boolean passed;
            String detail;
            try {
                passed = evaluateAssertion(world, station, assertion);
                detail = passed ? "matched" : "did not match";
            } catch (Exception ex) {
                passed = false;
                detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
            String assertionStatus;
            if (assertion.expectedFailure != null && !assertion.expectedFailure.isEmpty()) {
                if (passed) {
                    assertionStatus = "UNEXPECTED_PASS";
                    unexpectedPass = true;
                } else {
                    assertionStatus = "EXPECTED_FAIL";
                    expectedFailure = true;
                }
            } else if (passed) {
                assertionStatus = "PASS";
            } else {
                assertionStatus = "FAIL";
                failed = true;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", assertion.id);
            result.put("type", assertion.type);
            result.put("status", assertionStatus);
            result.put("detail", detail);
            if (assertion.expectedFailure != null) result.put("bug", assertion.expectedFailure);
            assertionResults.add(result);
        }

        String status;
        if (failed) status = "FAIL";
        else if (unexpectedPass) status = "UNEXPECTED_PASS";
        else if (expectedFailure) status = "EXPECTED_FAIL";
        else if (!station.manualChecks.isEmpty()) status = "MANUAL_REQUIRED";
        else status = "PASS";
        statuses.put(station.id, status);
        setStatusBlock(world, station, statusColor(status));
        writeStationResult(world, station, status, assertionResults);
        return status;
    }

    private void writeStationResult(WorldServer world, WorksDefinition.Station station, String status,
                                    List<Map<String, Object>> assertions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", station.name);
        payload.put("district", station.district);
        payload.put("status", status);
        payload.put("worldTime", world.getGameTime());
        payload.put("assertions", assertions);
        payload.put("manualChecks", station.manualChecks);
        payload.put("snapshot", snapshot(world, station));
        resultWriter.write(station.id, "latest", payload);
    }

    private List<Map<String, Object>> snapshot(WorldServer world, WorksDefinition.Station station) {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (WorksDefinition.Placement placement : station.placements) positions.add(absolute(station, placement.pos));
        for (WorksDefinition.Assertion assertion : station.assertions) {
            if (assertion.pos != null) positions.add(absolute(station, assertion.pos));
        }
        for (BlockPos pos : positions) {
            Map<String, Object> block = new LinkedHashMap<>();
            IBlockState state = world.getBlockState(pos);
            ResourceLocation name = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            block.put("pos", Arrays.asList(pos.getX(), pos.getY(), pos.getZ()));
            block.put("block", name == null ? "unknown" : name.toString());
            block.put("state", state.toString());
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) block.put("nbt", tile.write(new NBTTagCompound()).toString());
            snapshot.add(block);
        }
        return snapshot;
    }

    private boolean evaluateAssertion(WorldServer world, WorksDefinition.Station station,
                                      WorksDefinition.Assertion assertion) {
        if ("registryBlock".equals(assertion.type)) {
            return block(assertion.block) != Blocks.AIR;
        }
        if ("registryItem".equals(assertion.type)) {
            return item(assertion.item) != null;
        }
        if ("modLoaded".equals(assertion.type)) {
            return ModList.get().isLoaded(assertion.item);
        }
        if ("fluidAmount".equals(assertion.type)) {
            long total = 0;
            for (int[] position : assertion.positions) {
                TileEntity source = world.getTileEntity(absolute(station, position));
                if (source == null) return false;
                NBTTagCompound tank = source.write(new NBTTagCompound()).getCompound(assertion.nbtPath);
                if (tank.contains("Empty", 1)) continue;
                int amount = tank.getInt("Amount");
                if (amount < 0) return false;
                if (assertion.fluid == null || assertion.fluid.equals(tank.getString("FluidName"))) total += amount;
            }
            return compare(total, assertion.operator, assertion.value);
        }
        BlockPos pos = absolute(station, assertion.pos);
        if ("block".equals(assertion.type)) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() != block(assertion.block)) return false;
            return assertion.meta == null || state.equals(legacyState(assertion.block, state.getBlock(), assertion.meta));
        }
        if ("blockProperty".equals(assertion.type)) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() != block(assertion.block)) return false;
            for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                if (entry.getKey().getName().equals(assertion.property)) {
                    return assertion.expectedText.equals(String.valueOf(entry.getValue()));
                }
            }
            return false;
        }
        if ("comparatorOutput".equals(assertion.type)) {
            IBlockState state = world.getBlockState(pos);
            Block source = state.getBlock();
            return source.hasComparatorInputOverride(state)
                    && compare(source.getComparatorInputOverride(state, world, pos),
                    assertion.operator, assertion.value);
        }
        TileEntity tile = world.getTileEntity(pos);
        if ("tileExists".equals(assertion.type)) return tile != null;
        if ("inventoryEmpty".equals(assertion.type)) {
            if (!(tile instanceof IInventory)) return false;
            return inventoryCount((IInventory) tile, assertion.item) == 0;
        }
        if ("inventoryCount".equals(assertion.type)) {
            if (!(tile instanceof IInventory)) return false;
            return compare(inventoryCount((IInventory) tile, assertion.item), assertion.operator, assertion.count);
        }
        if ("inventoryNbtNumber".equals(assertion.type)) {
            if (!(tile instanceof IInventory) || assertion.slot == null) return false;
            IInventory inventory = (IInventory) tile;
            if (assertion.slot < 0 || assertion.slot >= inventory.getSizeInventory()) return false;
            ItemStack stack = inventory.getStackInSlot(assertion.slot);
            if (stack.isEmpty() || !stack.hasTag()) return false;
            Number number = numberAt(stack.getTag(), assertion.nbtPath);
            return number != null && compare(number.doubleValue(), assertion.operator, assertion.value);
        }
        if ("nbtNumber".equals(assertion.type)) {
            if (tile == null) return false;
            NBTTagCompound nbt = tile.write(new NBTTagCompound());
            Number number = numberAt(nbt, assertion.nbtPath);
            return number != null && compare(number.doubleValue(), assertion.operator, assertion.value);
        }
        if ("nbtString".equals(assertion.type)) {
            if (tile == null) return false;
            String text = stringAt(tile.write(new NBTTagCompound()), assertion.nbtPath);
            return assertion.expectedText != null && assertion.expectedText.equals(text);
        }
        if ("energy".equals(assertion.type)) {
            if (tile == null) return false;
            int[] energy = tile.write(new NBTTagCompound()).getIntArray("Energy");
            int index = assertion.energyIndex == null ? 0 : assertion.energyIndex;
            return index >= 0 && index < energy.length
                    && compare(Float.intBitsToFloat(energy[index]), assertion.operator, assertion.value);
        }
        throw new IllegalArgumentException("Unsupported assertion type " + assertion.type);
    }

    private static int inventoryCount(IInventory inventory, String itemName) {
        Item expected = itemName == null ? null : item(itemName);
        int count = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && (expected == null || stack.getItem() == expected)) count += stack.getCount();
        }
        return count;
    }

    private static Number numberAt(NBTTagCompound root, String path) {
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        NBTTagCompound current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.contains(parts[i], 10)) return null;
            current = current.getCompound(parts[i]);
        }
        String leaf = parts[parts.length - 1];
        if (!current.contains(leaf, 99)) return null;
        return current.getDouble(leaf);
    }

    private static String stringAt(NBTTagCompound root, String path) {
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        NBTTagCompound current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.contains(parts[i], 10)) return null;
            current = current.getCompound(parts[i]);
        }
        String leaf = parts[parts.length - 1];
        return current.contains(leaf, 8) ? current.getString(leaf) : null;
    }

    private static boolean compare(double actual, String operator, Number expected) {
        if (expected == null) return false;
        double value = expected.doubleValue();
        switch (operator == null ? "equals" : operator) {
            case "equals": return Math.abs(actual - value) < 0.0001;
            case "min": return actual >= value;
            case "max": return actual <= value;
            case "greater": return actual > value;
            case "less": return actual < value;
            default: throw new IllegalArgumentException("Unsupported comparison " + operator);
        }
    }

    private void resetStation(WorldServer world, WorksDefinition.Station station) {
        clearStation(world, station);
        decorateStation(world, station);
        for (WorksDefinition.Placement placement : station.placements) applyPlacement(world, station, placement);
        applyActions(world, station, station.setupActions);
        setStatusBlock(world, station, STATUS_NEUTRAL);
    }

    private void clearStation(WorldServer world, WorksDefinition.Station station) {
        BlockPos min = absolute(station, station.boundsMin);
        BlockPos max = absolute(station, station.boundsMax);
        removeTaggedEntities(world, station);
        for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(min, max)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        }
    }

    private void removeTaggedEntities(WorldServer world, WorksDefinition.Station station) {
        BlockPos min = absolute(station, station.boundsMin);
        BlockPos max = absolute(station, station.boundsMax);
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class,
                new AxisAlignedBB(min, max.add(1, 1, 1)))) {
            if (station.id.equals(entity.getEntityData().getString("AdvantageWorksStation"))) {
                entity.remove();
            }
        }
    }

    private void decorateStation(WorldServer world, WorksDefinition.Station station) {
        int color = districtColor(station.district);
        int maxX = station.boundsMax[0];
        int maxZ = station.boundsMax[2];
        for (int x = station.boundsMin[0]; x <= maxX; x++) {
            for (int z = station.boundsMin[2]; z <= maxZ; z++) {
                boolean edge = x == station.boundsMin[0] || x == maxX
                        || z == station.boundsMin[2] || z == maxZ;
                IBlockState state = edge ? wool(color).getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
                world.setBlockState(absolute(station, new int[]{x, 0, z}), state, 2);
            }
        }
        if (station.enclosed) encloseStation(world, station);
        placeSign(world, absolute(station, new int[]{1, 1, 1}), station.id,
                station.name, "", "");
        placeCommandSign(world, absolute(station, new int[]{3, 1, 1}), "[START]",
                "/advworks start " + station.id);
        placeCommandSign(world, absolute(station, new int[]{5, 1, 1}), "[RESET]",
                "/advworks reset " + station.id);
        placeCommandSign(world, absolute(station, new int[]{7, 1, 1}), "[CHECK]",
                "/advworks check " + station.id);
        placeCommandSign(world, absolute(station, new int[]{9, 1, 1}), "[STOP]",
                "/advworks stop " + station.id);
    }

    private void encloseStation(WorldServer world, WorksDefinition.Station station) {
        int minX = station.boundsMin[0];
        int maxX = station.boundsMax[0];
        int minZ = station.boundsMin[2];
        int maxZ = station.boundsMax[2];
        int gateX = (minX + maxX) / 2;
        for (int y = 1; y <= 4; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!(x == gateX && y <= 2)) {
                    world.setBlockState(absolute(station, new int[]{x, y, minZ}),
                            Blocks.GLASS.getDefaultState(), 2);
                }
                world.setBlockState(absolute(station, new int[]{x, y, maxZ}),
                        Blocks.GLASS.getDefaultState(), 2);
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                world.setBlockState(absolute(station, new int[]{minX, y, z}),
                        Blocks.GLASS.getDefaultState(), 2);
                world.setBlockState(absolute(station, new int[]{maxX, y, z}),
                        Blocks.GLASS.getDefaultState(), 2);
            }
        }
        world.setBlockState(absolute(station, new int[]{gateX, 1, minZ}),
                Blocks.OAK_FENCE_GATE.getDefaultState().with(BlockFenceGate.HORIZONTAL_FACING, EnumFacing.NORTH), 2);
    }

    private void buildConcourse(WorldServer world) {
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                world.setBlockState(new BlockPos(x, 3, z), Blocks.STONE_BRICKS.getDefaultState(), 2);
            }
        }
        for (int i = -140; i <= 160; i++) {
            for (int width = -2; width <= 2; width++) {
                world.setBlockState(new BlockPos(i, 3, width), Blocks.STONE_BRICKS.getDefaultState(), 2);
                world.setBlockState(new BlockPos(width, 3, i), Blocks.STONE_BRICKS.getDefaultState(), 2);
            }
        }
        placeSign(world, new BlockPos(-3, 4, 0), "POWER WORKS", "WEST", "", "");
        placeSign(world, new BlockPos(0, 4, -3), "STEAM WORKS", "NORTH", "", "");
        placeSign(world, new BlockPos(3, 4, 0), "ELECTRIC WORKS", "EAST", "", "");
        placeSign(world, new BlockPos(0, 4, 3), "PROVING", "GROUNDS SOUTH", "", "");
    }

    private void applyActions(WorldServer world, WorksDefinition.Station station,
                              List<WorksDefinition.Action> actions) {
        for (WorksDefinition.Action action : actions) {
            if (!requirementsPresent(action.requiredMods)) continue;
            String type = action.type == null ? "setBlock" : action.type;
            BlockPos pos = action.pos == null ? null : absolute(station, action.pos);
            switch (type) {
                case "setBlock":
                    applyPlacement(world, station, action);
                    break;
                case "setAir":
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                    break;
                case "clearInventory":
                    TileEntity clearTile = world.getTileEntity(pos);
                    if (clearTile instanceof IInventory) ((IInventory) clearTile).clear();
                    break;
                case "setInventory":
                    TileEntity tile = world.getTileEntity(pos);
                    if (!(tile instanceof IInventory)) throw new IllegalStateException("No inventory at " + pos);
                    Item stackItem = item(action.item);
                    if (stackItem == null) throw new IllegalStateException("Unknown item " + action.item);
                    ItemStack stack = itemStack(stackItem, action.count, action.damage);
                    if (action.nbt != null) {
                        try {
                            stack.setTag(JsonNbtConverter.toCompound(action.nbt));
                        } catch (Exception ex) {
                            throw new IllegalStateException("Cannot parse item NBT for " + action.item, ex);
                        }
                    }
                    if (action.enchantment != null) {
                        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(
                                new ResourceLocation(action.enchantment));
                        if (enchantment == null) {
                            throw new IllegalStateException("Unknown enchantment " + action.enchantment);
                        }
                        stack.addEnchantment(enchantment, action.level);
                    }
                    ((IInventory) tile).setInventorySlotContents(action.slot, stack);
                    tile.markDirty();
                    break;
                case "fillInventory":
                    TileEntity fillTile = world.getTileEntity(pos);
                    if (!(fillTile instanceof IInventory)) throw new IllegalStateException("No inventory at " + pos);
                    Item fillItem = item(action.item);
                    if (fillItem == null) throw new IllegalStateException("Unknown item " + action.item);
                    IInventory inventory = (IInventory) fillTile;
                    ItemStack fillTemplate = itemStack(fillItem, 1, action.damage);
                    int fillCount = Math.min(action.count,
                            Math.min(fillTemplate.getMaxStackSize(), inventory.getInventoryStackLimit()));
                    for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                        inventory.setInventorySlotContents(slot,
                                itemStack(fillItem, fillCount, action.damage));
                    }
                    fillTile.markDirty();
                    break;
                case "mergeNbt":
                    mergeTileNbt(world, pos, action.nbt);
                    break;
                case "setFluid":
                    TileEntity fluidTile = world.getTileEntity(pos);
                    Fluid fluid = fluid(action.fluid);
                    if (fluid == null) throw new IllegalStateException("Unknown fluid " + action.fluid);
                    int requested = (int) Math.min(Integer.MAX_VALUE, action.value);
                    int filled;
                    if (fluidTile instanceof IAdvantageFluidHandler) {
                        IAdvantageFluidHandler handler = (IAdvantageFluidHandler) fluidTile;
                        handler.drain(null, Integer.MAX_VALUE, true);
                        filled = requested == 0 ? 0 : handler.fill(null, new FluidStack(fluid, requested), true);
                    } else {
                        throw new IllegalStateException("No fluid handler at " + pos);
                    }
                    if (filled != requested) {
                        throw new IllegalStateException("Fluid handler at " + pos + " accepted " + filled
                                + " of " + requested + " mB " + action.fluid);
                    }
                    fluidTile.markDirty();
                    break;
                case "offerFluid":
                    Fluid offeredFluid = fluid(action.fluid);
                    if (offeredFluid == null) throw new IllegalStateException("Unknown fluid " + action.fluid);
                    FluidStack offeredStack = new FluidStack(offeredFluid, (int) action.value);
                    int accepted = FluidNetworkApi.offerFluid(world, pos, offeredStack, action.transferLimit);
                    if (accepted != action.expectedValue.intValue()) {
                        throw new IllegalStateException("Public fluid API at " + pos + " accepted " + accepted
                                + " mB; expected " + action.expectedValue);
                    }
                    if (offeredStack.amount != action.value) {
                        throw new IllegalStateException("Public fluid API mutated its offered stack at " + pos);
                    }
                    break;
                case "spawnItem":
                    Item dropped = item(action.item);
                    if (dropped == null) throw new IllegalStateException("Unknown item " + action.item);
                    EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.1,
                            pos.getZ() + 0.5, itemStack(dropped, action.count, action.damage));
                    tagEntity(entityItem, station.id);
                    world.spawnEntity(entityItem);
                    break;
                case "spawnEntity":
                    Entity entity = EntityType.create(world, new ResourceLocation(action.entity));
                    if (entity == null) throw new IllegalStateException("Unknown entity " + action.entity);
                    entity.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    tagEntity(entity, station.id);
                    world.spawnEntity(entity);
                    break;
                case "time":
                    world.setDayTime(action.value);
                    break;
                case "weatherClear":
                    world.getWorldInfo().setRaining(false);
                    world.getWorldInfo().setThundering(false);
                    break;
                case "weatherRain":
                    world.getWorldInfo().setRaining(true);
                    world.getWorldInfo().setThundering(false);
                    break;
                case "weatherThunder":
                    world.getWorldInfo().setRaining(true);
                    world.getWorldInfo().setThundering(true);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported action type " + type);
            }
        }
    }

    private void applyPlacement(WorldServer world, WorksDefinition.Station station,
                                WorksDefinition.Placement placement) {
        if (!requirementsPresent(placement.requiredMods)) return;
        Block target = block(placement.block);
        if (target == Blocks.AIR && !"minecraft:air".equals(placement.block)) {
            if (placement.optional) return;
            throw new IllegalStateException("Unknown block " + placement.block + " for " + station.id);
        }
        BlockPos pos = absolute(station, placement.pos);
        world.setBlockState(pos, legacyState(placement.block, target, placement.meta), 2);
        if (placement.nbt != null) mergeTileNbt(world, pos, placement.nbt);
    }

    private void mergeTileNbt(WorldServer world, BlockPos pos, JsonObject supplied) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) throw new IllegalStateException("No tile entity at " + pos);
        try {
            NBTTagCompound base = tile.write(new NBTTagCompound());
            NBTTagCompound additions = JsonNbtConverter.toCompound(supplied);
            base.merge(additions);
            base.setInt("x", pos.getX());
            base.setInt("y", pos.getY());
            base.setInt("z", pos.getZ());
            tile.read(base);
            tile.markDirty();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot merge tile NBT at " + pos, ex);
        }
    }

    private List<WorksDefinition.Station> select(String target, boolean allowGroups) throws CommandException {
        loadDefinition();
        if ("all".equalsIgnoreCase(target)) return new ArrayList<>(stations.values());
        if (allowGroups) {
            String district = target.toLowerCase(Locale.ROOT);
            if ("power".equals(district) || "steam".equals(district) || "electric".equals(district)) {
                List<WorksDefinition.Station> result = new ArrayList<>();
                for (WorksDefinition.Station station : stations.values()) {
                    if (district.equals(station.district)) result.add(station);
                }
                return result;
            }
        }
        return Collections.singletonList(oneStation(target));
    }

    private WorksDefinition.Station oneStation(String id) throws CommandException {
        loadDefinition();
        WorksDefinition.Station station = stations.get(id.toUpperCase(Locale.ROOT));
        if (station == null) throw commandError("Unknown Advantage Works station " + id);
        return station;
    }

    private static boolean requirementsPresent(WorksDefinition.Station station) {
        return requirementsPresent(station.requiredMods);
    }

    private static boolean requirementsPresent(List<String> requiredMods) {
        for (String mod : requiredMods) if (!ModList.get().isLoaded(mod)) return false;
        return true;
    }

    private static boolean optionalTargetPresent(WorksDefinition.Assertion assertion) {
        if (!assertion.optional) return true;
        if (assertion.block != null) return block(assertion.block) != Blocks.AIR;
        if (assertion.item != null && !"modLoaded".equals(assertion.type)) return item(assertion.item) != null;
        return true;
    }

    private int districtColor(String id) {
        for (WorksDefinition.District district : definition.districts) {
            if (district.id.equals(id)) return district.woolMeta;
        }
        return STATUS_NEUTRAL;
    }

    private static int statusColor(String status) {
        if ("PASS".equals(status)) return STATUS_PASS;
        if ("FAIL".equals(status)) return STATUS_FAIL;
        return STATUS_ATTENTION;
    }

    private static void setStatusBlock(WorldServer world, WorksDefinition.Station station, int meta) {
        world.setBlockState(absolute(station, new int[]{11, 1, 1}), wool(meta).getDefaultState(), 2);
    }

    private static void placeCommandSign(WorldServer world, BlockPos pos, String label, String command) {
        world.setBlockState(pos, Blocks.SIGN.getDefaultState().with(BlockStandingSign.ROTATION, 8), 2);
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntitySign)) return;
        TileEntitySign sign = (TileEntitySign) tile;
        TextComponentString clickable = new TextComponentString(label);
        clickable.setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        sign.signText[0] = clickable;
        sign.signText[1] = new TextComponentString("right-click");
        sign.markDirty();
    }

    private static void placeSign(WorldServer world, BlockPos pos, String... lines) {
        world.setBlockState(pos, Blocks.SIGN.getDefaultState().with(BlockStandingSign.ROTATION, 8), 2);
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntitySign)) return;
        TileEntitySign sign = (TileEntitySign) tile;
        for (int i = 0; i < sign.signText.length; i++) {
            sign.signText[i] = new TextComponentString(i < lines.length ? trimSign(lines[i]) : "");
        }
        sign.markDirty();
    }

    private static String trimSign(String text) {
        if (text == null) return "";
        return text.length() <= 15 ? text : text.substring(0, 15);
    }

    private static BlockPos absolute(WorksDefinition.Station station, int[] relative) {
        return new BlockPos(station.origin[0] + relative[0], station.origin[1] + relative[1],
                station.origin[2] + relative[2]);
    }

    private static void tagEntity(Entity entity, String stationId) {
        entity.getEntityData().setString("AdvantageWorksStation", stationId);
    }

    private static WorldServer overworld(MinecraftServer server) throws CommandException {
        WorldServer world = server.getWorld(DimensionType.OVERWORLD);
        if (world == null) throw commandError("Overworld is not loaded");
        return world;
    }

    private static Block block(String name) {
        if (name == null) return Blocks.AIR;
        String resolved = "minecraft:flowing_water".equals(name) ? "minecraft:water"
                : "minecraft:stonebrick".equals(name) ? "minecraft:stone_bricks" : name;
        Block value = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(resolved));
        return value == null ? Blocks.AIR : value;
    }

    private static Item item(String name) {
        if (name == null) return null;
        String resolved = "minecraft:sapling".equals(name) ? "minecraft:oak_sapling"
                : "minecraft:planks".equals(name) ? "minecraft:oak_planks" : name;
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(resolved));
    }

    private static CommandException commandError(String message) {
        return new CommandException(new TextComponentString(message));
    }

    private static IBlockState legacyState(String name, Block block, int metadata) {
        if (name != null && (name.startsWith("poweradvantage:")
                || name.startsWith("steamadvantage:") || name.startsWith("electricadvantage:"))) {
            return LegacyMigrationApi.decodeBlockState(new ResourceLocation(name), block, metadata);
        }
        if (block instanceof BlockFlowingFluid) {
            return block.getDefaultState().with(BlockFlowingFluid.LEVEL, Integer.valueOf(metadata & 15));
        }
        return block.getDefaultState();
    }

    private static ItemStack itemStack(Item item, int count, int damage) {
        ItemStack stack = new ItemStack(item, count);
        if (damage != 0) stack.setDamage(damage);
        return stack;
    }

    private static Fluid fluid(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        ResourceLocation id = name.indexOf(':') >= 0 ? new ResourceLocation(name)
                : new ResourceLocation("poweradvantage", name);
        Fluid value = IRegistry.field_212619_h.func_212608_b(id);
        if (value == null) {
            String migratedName = LegacyMigrationApi.fluidTarget(name);
            if (!name.equals(migratedName)) {
                value = IRegistry.field_212619_h.func_212608_b(new ResourceLocation(migratedName));
            }
        }
        if (value == null && name.indexOf(':') < 0) {
            value = IRegistry.field_212619_h.func_212608_b(new ResourceLocation("minecraft", name));
        }
        return value;
    }

    private static Block wool(int metadata) {
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
                "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", colors[metadata & 15] + "_wool"));
    }
}
