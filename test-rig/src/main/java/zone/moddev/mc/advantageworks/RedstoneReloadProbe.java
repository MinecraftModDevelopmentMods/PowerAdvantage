package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.command.Commands;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Separate cold-process experiment: finite earned work, never expected-state restoration. */
final class RedstoneReloadProbe {
    private static final File MARKER = new File("advantage-redstone-reload.allow");
    private static final File FIXTURE = new File("redstone-reload-fixture.json");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int OBSERVATION_TICKS = 16;
    private static final int READINESS_TICKS = 80;
    private enum Phase { FROZEN, PREPARING, SEEDING, RESTORING, OPERATING, DONE }

    private final String processToken = UUID.randomUUID().toString();
    private final List<Witness> witnesses = definitions();
    private final List<TileEntity> suspended = new ArrayList<>();
    private final List<Map<String, Object>> seedTrace = new ArrayList<>();
    private final List<Map<String, Object>> ticks = new ArrayList<>();
    private final List<Map<String, Object>> readiness = new ArrayList<>();
    private Phase phase = Phase.FROZEN;
    private WorldServer world;
    private String label;
    private boolean used;
    private int preparationIndex;
    private int constructionCalls;
    private long tickStart;
    private Map<String, Object> initial;
    private Map<String, Object> restored;

    static void register() {
        if (!MARKER.isFile()) return;
        RedstoneReloadProbe probe = new RedstoneReloadProbe();
        MinecraftForge.EVENT_BUS.addListener(probe::commands);
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
    }

    private static List<Witness> definitions() {
        List<Witness> result = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            int offset = i == 8 ? 0 : i;
            result.add(new Witness(i == 8 ? "unheld" : "held-" + i, i != 8, offset,
                    new BlockPos(60000 + i * 64 + 14 + ((offset >> 1) & 1),
                            80 + (offset & 1), 60000 + ((offset >> 2) & 1))));
        }
        return result;
    }

    private void commands(FMLServerStartingEvent event) {
        event.getCommandDispatcher().register(Commands.literal("advredstonereload")
                .requires(source -> source.hasPermissionLevel(4))
                .then(Commands.argument("operation", StringArgumentType.word())
                        .then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                            if (!MARKER.isFile() || used) throw new IllegalStateException("Missing marker or reused probe");
                            WorldServer current = context.getSource().getWorld();
                            if (!current.getWorldInfo().getWorldName().equals("AdvantageWorksMigration"))
                                throw new IllegalStateException("Not an authorized disposable world");
                            String requested = StringArgumentType.getString(context, "label");
                            if (!requested.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Unsafe label");
                            world = current;
                            label = requested;
                            used = true;
                            try {
                                String operation = StringArgumentType.getString(context, "operation");
                                if ("seed".equals(operation)) {
                                    if (FIXTURE.exists()) throw new IllegalStateException("Never rebuild a saved fixture");
                                    phase = Phase.PREPARING;
                                } else if ("resume".equals(operation)) {
                                    JsonObject fixture = JSON.fromJson(new String(Files.readAllBytes(FIXTURE.toPath()),
                                            StandardCharsets.UTF_8), JsonObject.class);
                                    if (fixture.get("schema").getAsInt() != 1 || fixture.getAsJsonArray("cases").size() != 9
                                            || processToken.equals(fixture.get("processToken").getAsString()))
                                        throw new IllegalStateException("Missing cold-process seed authority");
                                    phase = Phase.RESTORING;
                                } else throw new IllegalArgumentException("Expected seed or resume");
                            } catch (Throwable failure) { fail(failure); }
                            return 1;
                        }))));
    }

    private boolean paused() { return phase != Phase.SEEDING && phase != Phase.OPERATING; }

    private void tick(TickEvent.WorldTickEvent event) {
        if (!(event.world instanceof WorldServer) || event.world.dimension.getType() != net.minecraft.world.dimension.DimensionType.OVERWORLD) return;
        WorldServer current = (WorldServer) event.world;
        if (event.phase == TickEvent.Phase.START) {
            tickStart = current.getGameTime();
            if (paused()) {
                suspended.addAll(current.tickableTileEntities);
                current.tickableTileEntities.clear();
            }
            return;
        }
        for (TileEntity tile : suspended)
            if (!tile.isRemoved() && !current.tickableTileEntities.contains(tile)) current.tickableTileEntities.add(tile);
        suspended.clear();
        if (current != world || phase == Phase.DONE) return;
        try {
            if (phase == Phase.PREPARING) {
                if (preparationIndex < witnesses.size()) {
                    construct(witnesses.get(preparationIndex++));
                } else if (ready("seed")) {
                    initial = frame(0);
                    phase = Phase.SEEDING;
                }
            } else if (phase == Phase.SEEDING) {
                Map<String, Object> observation = frame(seedTrace.size() + 1);
                seedTrace.add(observation);
                boolean ready = true;
                for (Witness witness : witnesses) {
                    NBTTagCompound tag = tile(witness).write(new NBTTagCompound());
                    ready &= tag.getShort("progress") > 0 && tag.contains("targetX", 99)
                            && ((PoweredEntity) tile(witness)).getEnergy(new ConduitType("electricity")) > 24;
                }
                if (ready) freezeSeed();
                else if (seedTrace.size() >= 16) throw new AssertionError("No finite natural mid-obsidian state within 16 ticks");
            } else if (phase == Phase.RESTORING) {
                if (preparationIndex < witnesses.size()) {
                    force(witnesses.get(preparationIndex++));
                } else if (ready("restart")) {
                    restored = frame(0);
                    writeResult("redstone-reload-restored-" + label + ".json", restored);
                    phase = Phase.OPERATING;
                }
            } else if (phase == Phase.OPERATING) {
                Map<String, Object> observation = frame(ticks.size() + 1);
                ticks.add(observation);
                writeResult(String.format(java.util.Locale.ROOT, "redstone-reload-tick-%s-%02d.json", label, ticks.size()), observation);
                if (ticks.size() == OBSERVATION_TICKS) {
                    phase = Phase.DONE;
                    Map<String, Object> report = metadata();
                    report.put("observationTicks", OBSERVATION_TICKS);
                    report.put("held", true);
                    report.put("restored", restored);
                    report.put("ticks", ticks);
                    report.put("readiness", readiness);
                    writeResult("redstone-reload-" + label + ".json", report);
                    complete();
                }
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private void force(Witness witness) {
        // Include every neighbor and the 3x3 target, also at x=15 chunk edges.
        for (int x = (witness.pos.getX() - 1) >> 4; x <= (witness.pos.getX() + 1) >> 4; x++)
            for (int z = (witness.pos.getZ() - 1) >> 4; z <= (witness.pos.getZ() + 1) >> 4; z++) {
                world.func_212414_b(x, z, true);
                world.getChunk(x, z);
            }
    }

    private boolean ready(String stage) {
        boolean all = true;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Witness witness : witnesses) {
            TileEntity tile = tile(witness);
            // Before ChunkHolder existed, native tile ticks required a loaded chunk and list membership.
            boolean ticking = world.isBlockLoaded(witness.pos);
            boolean listed = world.tickableTileEntities.contains(tile) && !tile.isRemoved();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", witness.id);
            row.put("pos", coordinates(witness.pos));
            row.put("blockTicking", ticking);
            row.put("tickableListed", listed);
            rows.add(row);
            all &= ticking && listed;
        }
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("phase", stage);
        sample.put("worldTime", world.getGameTime());
        sample.put("cases", rows);
        readiness.add(sample);
        if (!all && readiness.size() >= READINESS_TICKS)
            throw new AssertionError("Native block-ticking/list readiness timed out after " + READINESS_TICKS + " paused ticks");
        return all;
    }

    private void construct(Witness witness) {
        if (phase != Phase.PREPARING || FIXTURE.exists()) throw new IllegalStateException("Construction outside fresh setup");
        force(witness);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            world.setBlockState(witness.pos.add(dx, dy, dz), Blocks.AIR.getDefaultState(), 2);
        Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation("electricadvantage:electric_drill"));
        if (block == null || block == Blocks.AIR) throw new IllegalStateException("Electric drill registry absent");
        world.setBlockState(witness.pos, block.getDefaultState().with(BlockStateProperties.FACING, EnumFacing.NORTH), 2);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            world.setBlockState(witness.pos.add(dx, dy, -1), Blocks.OBSIDIAN.getDefaultState(), 2);
        TileEntity tile = tile(witness);
        ((PoweredEntity) tile).setEnergy(500, new ConduitType("electricity"));
        ItemStack dirt = new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT), 7);
        dirt.getOrCreateTag().setString("AdvWorksWitness", witness.id);
        ((IInventory) tile).setInventorySlotContents(0, dirt);
        ((IInventory) tile).setInventorySlotContents(1, new ItemStack(Items.APPLE, 13));
        tile.markDirty();
        constructionCalls++;
    }

    private TileEntity tile(Witness witness) {
        TileEntity tile = world.getTileEntity(witness.pos);
        if (!(tile instanceof PoweredEntity) || !(tile instanceof IInventory))
            throw new IllegalStateException("Missing powered inventory at " + witness.pos);
        return tile;
    }

    private void freezeSeed() {
        // Freeze BEFORE adding redstone: no tick may erase the naturally earned target/progress.
        phase = Phase.FROZEN;
        List<Map<String, Object>> preHold = snapshots();
        for (Witness witness : witnesses) if (witness.held)
            world.setBlockState(witness.pos.up(), Blocks.REDSTONE_BLOCK.getDefaultState(), 3);
        for (Witness witness : witnesses) tile(witness).markDirty();
        Map<String, Object> fixture = metadata();
        fixture.put("initialEnergy", 500);
        fixture.put("initial", initial);
        fixture.put("seedTrace", seedTrace);
        fixture.put("readiness", readiness);
        fixture.put("preHold", preHold);
        fixture.put("worldTime", world.getGameTime());
        fixture.put("cases", snapshots());
        fixture.put("freezeMethod", "Suspend tickable list before adding holds; no expected NBT writes; restart the same world");
        writeNew(FIXTURE, fixture);
        complete();
    }

    private Map<String, Object> frame(int index) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", index);
        result.put("tickStartWorldTime", tickStart);
        result.put("worldTime", world.getGameTime());
        result.put("cases", snapshots());
        return result;
    }

    private List<Map<String, Object>> snapshots() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Witness witness : witnesses) {
            TileEntity tile = tile(witness);
            NBTTagCompound nbt = tile.write(new NBTTagCompound());
            IBlockState state = world.getBlockState(witness.pos);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", witness.id);
            row.put("pos", coordinates(witness.pos));
            row.put("held", witness.held);
            row.put("offset", witness.offset);
            row.put("block", state.getBlock().getRegistryName().toString());
            row.put("state", state.toString());
            row.put("active", active(state));
            row.put("signal", world.isBlockPowered(witness.pos));
            row.put("holdBlock", world.getBlockState(witness.pos.up()).getBlock().getRegistryName().toString());
            row.put("energy", ((PoweredEntity) tile).getEnergy(new ConduitType("electricity")));
            row.put("progress", (int) nbt.getShort("progress"));
            row.put("nbt", nbt.toString());
            List<Map<String, Object>> targets = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = witness.pos.add(dx, dy, -1);
                Map<String, Object> target = new LinkedHashMap<>();
                target.put("pos", coordinates(pos));
                target.put("state", world.getBlockState(pos).toString());
                targets.add(target);
            }
            row.put("targets", targets);
            result.add(row);
        }
        return result;
    }

    private static boolean active(IBlockState state) {
        for (net.minecraft.state.IProperty<?> property : state.getProperties())
            if (property.getName().equals("active")) return Boolean.TRUE.equals(state.get(property));
        throw new IllegalStateException("Drill has no ACTIVE property");
    }

    private static int[] coordinates(BlockPos pos) { return new int[]{pos.getX(), pos.getY(), pos.getZ()}; }

    private Map<String, Object> metadata() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema", 1);
        result.put("processToken", processToken);
        result.put("constructionCallsThisProcess", constructionCalls);
        return result;
    }

    private void fail(Throwable failure) {
        phase = Phase.DONE;
        Map<String, Object> report = metadata();
        report.put("failure", failure.toString());
        java.io.StringWriter trace = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(trace));
        report.put("traceback", trace.toString());
        report.put("seedTrace", seedTrace);
        report.put("ticks", ticks);
        report.put("readiness", readiness);
        writeResult("redstone-reload-failure-" + label + ".json", report);
        complete();
    }

    private void complete() { System.out.println("ADVREDSTONE COMPLETE " + label); }

    private static void writeResult(String name, Object value) { writeNew(new File("advantage-works-results", name), value); }

    private static void writeNew(File path, Object value) {
        try {
            if (path.getParentFile() != null) Files.createDirectories(path.getParentFile().toPath());
            Files.write(path.toPath(), JSON.toJson(value).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot retain immutable observation " + path, error); }
    }

    private static final class Witness {
        final String id;
        final boolean held;
        final int offset;
        final BlockPos pos;
        Witness(String id, boolean held, int offset, BlockPos pos) {
            this.id = id; this.held = held; this.offset = offset; this.pos = pos;
        }
    }
}
