package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.fluid.FluidNetworkApi;
import cyano.poweradvantage.api.fluid.FluidStack;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.command.Commands;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import zone.moddev.mc.poweradvantage.conduitnetwork.ConduitRegistry;

/** Observes real tile movement and chunk lifecycles only in marked disposable worlds. */
final class LifecycleProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Fixture> fixtures = new LinkedHashMap<>();
    private final Map<Long, Integer> unloads = new LinkedHashMap<>();
    private final Map<Long, Integer> loads = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> before = new LinkedHashMap<>();
    private final List<Map<String, Object>> movements = new ArrayList<>();
    private final Set<String> completed = new LinkedHashSet<>();
    private final Map<String, Integer> routeSteps = new LinkedHashMap<>();
    private final List<Map<String, Object>> chunkEvents = new ArrayList<>();
    private boolean watching;
    private boolean tracingLoads;

    static final class Fixture {
        String id;
        int[] min, max, from, to, hold;
        String block, energyType;
        String bucket;
        int[] source;
        float initialEnergy;
        List<int[]> witnesses;
        List<int[]> route, controls;
        Map<String, List<int[]>> groups;
    }

    static void validate(Fixture fixture) {
        if (fixture.id == null || !fixture.id.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid fixture ID");
        if (fixture.min == null || fixture.max == null || fixture.min.length != 3 || fixture.max.length != 3)
            throw new IllegalArgumentException("Invalid bounds");
        if (fixture.min[0] < 1024 || fixture.min[2] < 1024 || fixture.min[1] < 8 || fixture.max[1] > 240)
            throw new IllegalArgumentException("Fixtures must be remote and bounded above terrain");
        for (int axis = 0; axis < 3; axis++)
            if (fixture.max[axis] < fixture.min[axis] || fixture.max[axis] - fixture.min[axis] > 31)
                throw new IllegalArgumentException("Oversized fixture");
        if (fixture.witnesses == null || fixture.witnesses.isEmpty())
            throw new IllegalArgumentException("Missing witnesses");
        for (int[] pos : fixture.witnesses) requireInside(fixture, pos);
        if (fixture.bucket != null) {
            if (!fixture.bucket.matches("poweradvantage:(crude|refined)_oil_bucket"))
                throw new IllegalArgumentException("Unsupported oil bucket");
            requireInside(fixture, fixture.source);
            if (fixture.source[1] >= fixture.max[1])
                throw new IllegalArgumentException("Bucket needs headroom inside fixture bounds");
        }
        if (fixture.groups != null) {
            for (Map.Entry<String, List<int[]>> group : fixture.groups.entrySet()) {
                if (!group.getKey().matches("[a-z]+") || group.getValue().isEmpty())
                    throw new IllegalArgumentException("Invalid chunk group");
                for (int[] chunk : group.getValue()) {
                    if (chunk.length != 2 || chunk[0] < (fixture.min[0] >> 4)
                            || chunk[0] > (fixture.max[0] >> 4) || chunk[1] < (fixture.min[2] >> 4)
                            || chunk[1] > (fixture.max[2] >> 4))
                        throw new IllegalArgumentException("Chunk outside fixture bounds");
                }
            }
        }
        if (fixture.from != null) {
            requireInside(fixture, fixture.from);
            requireInside(fixture, fixture.to);
            requireInside(fixture, fixture.hold);
            int distance = 0;
            for (int axis = 0; axis < 3; axis++) distance += Math.abs(fixture.from[axis] - fixture.to[axis]);
            if ((fixture.route == null && distance != 1) || fixture.initialEnergy <= 0 || !Float.isFinite(fixture.initialEnergy))
                throw new IllegalArgumentException("Invalid movement fixture");
            if (!"steamadvantage:steam_drill".equals(fixture.block)
                    && !"electricadvantage:electric_drill".equals(fixture.block))
                throw new IllegalArgumentException("Not an Advantage drill");
            if (fixture.route != null) {
                if (fixture.route.size() < 2 || fixture.route.size() > 8 || fixture.controls == null
                        || fixture.controls.size() != fixture.route.size()
                        || !Arrays.equals(fixture.from, fixture.route.get(0))
                        || !Arrays.equals(fixture.to, fixture.route.get(fixture.route.size() - 1)))
                    throw new IllegalArgumentException("Invalid route");
                Set<String> visited = new LinkedHashSet<>();
                for (int i = 0; i < fixture.route.size(); i++) {
                    int[] pos = fixture.route.get(i);
                    requireInside(fixture, pos);
                    requireInside(fixture, fixture.controls.get(i));
                    if (!visited.add(Arrays.toString(pos))) throw new IllegalArgumentException("Cyclic route");
                    if (i > 0) {
                        int step = 0;
                        for (int axis = 0; axis < 3; axis++) step += Math.abs(pos[axis] - fixture.route.get(i - 1)[axis]);
                        if (step != 1) throw new IllegalArgumentException("Route teleport");
                    }
                }
            }
        }
    }

    private static void requireInside(Fixture fixture, int[] pos) {
        if (pos == null || pos.length != 3) throw new IllegalArgumentException("Invalid position");
        for (int axis = 0; axis < 3; axis++)
            if (pos[axis] < fixture.min[axis] || pos[axis] > fixture.max[axis])
                throw new IllegalArgumentException("Position outside fixture bounds");
    }

    static void register() {
        if (!new File("advantage-lifecycle.allow").isFile()) return;
        LifecycleProbe probe = new LifecycleProbe();
        try {
            Fixture[] definition = JSON.fromJson(new String(Files.readAllBytes(
                    new File("lifecycle-fixture.json").toPath()), StandardCharsets.UTF_8), Fixture[].class);
            if (definition.length == 0 || definition.length > 32) throw new IllegalArgumentException("Fixture count");
            for (Fixture fixture : definition) {
                validate(fixture);
                if (probe.fixtures.put(fixture.id, fixture) != null) throw new IllegalArgumentException("Duplicate fixture");
            }
        } catch (Exception error) {
            throw new IllegalStateException("Invalid lifecycle fixture", error);
        }
        MinecraftForge.EVENT_BUS.addListener(probe::starting);
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
        MinecraftForge.EVENT_BUS.addListener(probe::unload);
        MinecraftForge.EVENT_BUS.addListener(probe::load);
    }

    private void starting(FMLServerStartingEvent event) {
        event.getCommandDispatcher().register(Commands.literal("advlifecycle")
                .requires(source -> source.hasPermissionLevel(4))
                .then(Commands.argument("operation", StringArgumentType.word())
                        .then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                            WorksController.getInstance().requireAuthorizedWorld(context.getSource().getServer());
                            if (!new File("advantage-lifecycle.allow").isFile()) throw new IllegalStateException("Missing lifecycle marker");
                            String operation = StringArgumentType.getString(context, "operation");
                            String label = StringArgumentType.getString(context, "label");
                            if (!label.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid label");
                            execute(context.getSource().getWorld(), operation, label);
                            System.out.println("ADVLIFECYCLE COMPLETE " + operation + " " + label);
                            return 1;
                        }))));
    }

    private void execute(WorldServer world, String operation, String label) {
        if (operation.startsWith("release-") || operation.startsWith("load-")) {
            boolean acquire = operation.startsWith("load-");
            String group = operation.substring(acquire ? 5 : 8);
            Set<Long> selected = new LinkedHashSet<>();
            for (Fixture fixture : fixtures.values()) {
                if (fixture.groups == null || !fixture.groups.containsKey(group)) continue;
                for (int[] pos : fixture.groups.get(group)) selected.add(ChunkPos.asLong(pos[0], pos[1]));
            }
            if (selected.isEmpty()) throw new IllegalArgumentException("Unknown chunk group " + group);
            for (long key : selected) {
                ChunkPos pos = new ChunkPos(key);
                world.func_212414_b(pos.x, pos.z, acquire);
                if (acquire) world.getChunkProvider().provideChunk(pos.x, pos.z, true, true);
                else {
                    Chunk chunk = world.getChunkProvider().loadedChunks.get(key);
                    if (chunk != null) world.getChunkProvider().queueUnload(chunk);
                }
            }
            snapshot(world, label);
            return;
        }
        switch (operation) {
            case "place-buckets":
                for (Fixture oil : fixtures.values()) {
                    if (oil.bucket == null) continue;
                    BlockPos source = position(oil.source);
                    if (!world.isAirBlock(source)) throw new IllegalStateException("Oil source is not empty: " + oil.id);
                    net.minecraft.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                            new net.minecraft.util.ResourceLocation(oil.bucket));
                    if (!(item instanceof net.minecraft.item.ItemBucket)
                            || !((net.minecraft.item.ItemBucket) item).tryPlaceContainedLiquid(null, world, source, null))
                        throw new IllegalStateException("Bucket placement failed: " + oil.id);
                    String expected = oil.bucket.substring(0, oil.bucket.length() - "_bucket".length());
                    net.minecraft.fluid.IFluidState state = world.getFluidState(source);
                    if (!state.isSource() || !expected.equals(
                            net.minecraft.util.registry.IRegistry.field_212619_h.getKey(state.getFluid()).toString()))
                        throw new IllegalStateException("Bucket placed the wrong fluid: " + oil.id);
                }
                break;
            case "prime":
            case "low-prime":
                for (Fixture fixture : fixtures.values()) {
                    if (fixture.from == null) continue;
                    int[] current = fixture.route == null ? fixture.from : fixture.route.get(routeSteps.getOrDefault(fixture.id, 0));
                    TileEntity tile = world.getTileEntity(position(current));
                    if (!(tile instanceof PoweredEntity) || !fixture.block.equals(world.getBlockState(tile.getPos()).getBlock().getRegistryName().toString()))
                        throw new IllegalStateException("Missing drill " + fixture.id);
                    float reserve = "low-prime".equals(operation) ? ("steam".equals(fixture.energyType) ? 9 : 249) : fixture.initialEnergy;
                    ((PoweredEntity) tile).setEnergy(reserve, new ConduitType(fixture.energyType));
                    tile.markDirty();
                }
                break;
            case "watch": watching = true; break;
            case "trace-loads": tracingLoads = true; break;
            case "release":
                for (long key : chunks()) {
                    ChunkPos pos = new ChunkPos(key);
                    world.func_212414_b(pos.x, pos.z, false);
                    Chunk chunk = world.getChunkProvider().loadedChunks.get(key);
                    if (chunk != null) world.getChunkProvider().queueUnload(chunk);
                }
                break;
            case "load":
                for (long key : chunks()) {
                    ChunkPos pos = new ChunkPos(key);
                    world.func_212414_b(pos.x, pos.z, true);
                    world.getChunkProvider().provideChunk(pos.x, pos.z, true, true);
                }
                break;
            case "offer":
            case "offer-blocked":
                Fixture fixture = fixtures.get("C-FLUID");
                if (fixture == null) throw new IllegalStateException("Missing fluid fixture");
                int accepted = FluidNetworkApi.offerFluid(world, position(fixture.witnesses.get(0)),
                        new FluidStack(net.minecraft.init.Fluids.WATER, 1000), 1000);
                int expected = "offer-blocked".equals(operation) ? 0 : 1000;
                if (accepted != expected) throw new IllegalStateException("Fluid network accepted " + accepted + " instead of " + expected);
                break;
            case "isolated":
                for (Fixture network : fixtures.values()) {
                    if (network.groups == null) continue;
                    ConduitType type = new ConduitType("C-STEAM".equals(network.id) ? "steam"
                            : "C-ELECTRIC".equals(network.id) ? "electricity" : "fluid");
                    ConduitType energy = "C-FLUID".equals(network.id) ? new ConduitType("minecraft:water") : type;
                    if (!ConduitRegistry.getInstance().getRequestsForPower(world,
                            position(network.witnesses.get(0)), type, energy).isEmpty())
                        throw new IllegalStateException("Unloaded bridge still connects a consumer: " + network.id);
                }
                break;
            case "snapshot": break;
            case "legacy-read":
            case "changed-target-read":
                for (Fixture drill : fixtures.values()) {
                    if (drill.to == null) continue;
                    TileEntity tile = world.getTileEntity(position(drill.to));
                    if (!(tile instanceof PoweredEntity)
                            || !drill.block.equals(world.getBlockState(position(drill.to)).getBlock().getRegistryName().toString()))
                        throw new IllegalStateException("Missing restored drill " + drill.id);
                    NBTTagCompound saved = tile.write(new NBTTagCompound());
                    if (!saved.contains("targetX", 99) || saved.getShort("progress") <= 0)
                        throw new IllegalStateException("Drill must be mid-block " + drill.id);
                    if ("legacy-read".equals(operation)) {
                        saved.removeTag("TargetState");
                        saved.removeTag("TargetStates");
                    } else {
                        int[] target = {saved.getInt("targetX"), saved.getInt("targetY"), saved.getInt("targetZ")};
                        requireInside(drill, target);
                        world.setBlockState(position(target), Blocks.BEDROCK.getDefaultState(), 3);
                    }
                    // Round-trip real saved state; never inject quantities, energy or progress.
                    tile.read(saved);
                    tile.markDirty();
                }
                break;
            default: throw new IllegalArgumentException("Unknown lifecycle operation " + operation);
        }
        snapshot(world, label);
    }

    private Set<Long> chunks() {
        Set<Long> result = new LinkedHashSet<>();
        for (Fixture fixture : fixtures.values())
            for (int z = fixture.min[2] >> 4; z <= fixture.max[2] >> 4; z++)
                for (int x = fixture.min[0] >> 4; x <= fixture.max[0] >> 4; x++) result.add(ChunkPos.asLong(x, z));
        return result;
    }

    private void unload(ChunkEvent.Unload event) {
        if (event.getWorld() instanceof WorldServer) {
            increment(unloads, event.getChunk().getPos().asLong());
            recordEvent(event, "unload");
        }
    }

    private void load(ChunkEvent.Load event) {
        if (event.getWorld() instanceof WorldServer) {
            increment(loads, event.getChunk().getPos().asLong());
            recordEvent(event, "load");
        }
    }

    private void recordEvent(ChunkEvent event, String operation) {
        if (!chunks().contains(event.getChunk().getPos().asLong())) return;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("operation", operation);
        record.put("pos", Arrays.asList(event.getChunk().getPos().x, event.getChunk().getPos().z));
        record.put("worldTime", ((WorldServer) event.getWorld()).getGameTime());
        if (tracingLoads && "load".equals(operation)) {
            List<String> stack = new ArrayList<>();
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                stack.add(frame.toString());
                if (stack.size() == 64) break;
            }
            record.put("callStack", stack);
        }
        chunkEvents.add(record);
    }

    private void increment(Map<Long, Integer> counters, long key) {
        if (chunks().contains(key)) counters.put(key, counters.getOrDefault(key, 0) + 1);
    }

    private Map<String, Object> witness(WorldServer world, int[] coordinates) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pos", coordinates);
        BlockPos pos = position(coordinates);
        // No world.getChunk/getTileEntity call is permitted before this non-loading lookup.
        Chunk chunk = world.getChunkProvider().loadedChunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        result.put("loaded", chunk != null);
        if (chunk == null) return result;
        result.put("block", chunk.getBlockState(pos).getBlock().getRegistryName().toString());
        result.put("state", chunk.getBlockState(pos).toString());
        TileEntity tile = chunk.getTileEntity(pos);
        if (tile != null) {
            result.put("nbt", tile.write(new NBTTagCompound()).toString());
            result.put("tileIdentity", System.identityHashCode(tile));
            if (tile instanceof PoweredEntity) {
                PoweredEntity powered = (PoweredEntity) tile;
                Map<String, Float> energy = new LinkedHashMap<>();
                for (ConduitType type : powered.getTypes()) energy.put(type.toString(), powered.getEnergy(type));
                result.put("energy", energy);
            }
        }
        return result;
    }

    private void tick(TickEvent.WorldTickEvent event) {
        if (!watching || !(event.world instanceof WorldServer)) return;
        WorldServer world = (WorldServer) event.world;
        for (Fixture fixture : fixtures.values()) {
            if (fixture.from == null || completed.contains(fixture.id)) continue;
            int step = routeSteps.getOrDefault(fixture.id, 0);
            int[] from = fixture.route == null ? fixture.from : fixture.route.get(step);
            int[] to = fixture.route == null ? fixture.to : fixture.route.get(step + 1);
            if (event.phase == TickEvent.Phase.START) {
                Map<String, Object> original = witness(world, from);
                if (fixture.block.equals(original.get("block"))) before.put(fixture.id, original);
            } else {
                Map<String, Object> original = witness(world, from);
                Map<String, Object> moved = witness(world, to);
                if (fixture.block.equals(moved.get("block")) && !fixture.block.equals(original.get("block"))) {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("id", fixture.id);
                    record.put("worldTime", world.getGameTime());
                    record.put("before", before.get(fixture.id));
                    record.put("after", moved);
                    record.put("oldPosition", original);
                    if (fixture.route != null) record.put("step", step + 1);
                    movements.add(record);
                    if (fixture.route == null || step + 2 == fixture.route.size()) completed.add(fixture.id);
                    routeSteps.put(fixture.id, step + 1);
                    // Hold after observing the real move, without touching the drill's state or inventory.
                    int[] hold = fixture.controls == null ? fixture.hold : fixture.controls.get(step + 1);
                    world.setBlockState(position(hold), Blocks.REDSTONE_BLOCK.getDefaultState(), 3);
                }
            }
        }
    }

    private void snapshot(WorldServer world, String label) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> cases = new LinkedHashMap<>();
        for (Fixture fixture : fixtures.values()) {
            List<Map<String, Object>> values = new ArrayList<>();
            for (int[] pos : fixture.witnesses) values.add(witness(world, pos));
            cases.put(fixture.id, values);
        }
        List<Map<String, Object>> chunkStates = new ArrayList<>();
        for (long key : chunks()) {
            ChunkPos pos = new ChunkPos(key);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pos", Arrays.asList(pos.x, pos.z));
            entry.put("loaded", world.getChunkProvider().loadedChunks.containsKey(key));
            entry.put("unloads", unloads.getOrDefault(key, 0));
            entry.put("loads", loads.getOrDefault(key, 0));
            chunkStates.add(entry);
        }
        result.put("worldTime", world.getGameTime());
        result.put("cases", cases);
        result.put("chunks", chunkStates);
        result.put("movements", movements);
        result.put("chunkEvents", chunkEvents);
        result.put("routeSteps", routeSteps);
        new WorksResultWriter().write("lifecycle", label, result);
    }

    private static BlockPos position(int[] coordinates) {
        return new BlockPos(coordinates[0], coordinates[1], coordinates[2]);
    }
}
