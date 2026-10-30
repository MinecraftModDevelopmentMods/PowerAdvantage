package zone.moddev.mc.advantageworks;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Native partial unloads. No world/tile getter is used for an absent chunk. */
public final class LegacyPartialNetworkCases {
    static final int TICKS = 1100, RELEASE = 240, ABSENT_TICKS = 240;
    private LegacyPartialNetworkCases() {}

    static void register(List<MachineScenario> cases) {
        for (String type : new String[]{"steam", "electric", "fluid"})
            for (String part : new String[]{"bridge", "source", "consumer"}) add(cases, type, part);
    }

    static void registerBridges(List<MachineScenario> cases) {
        for (String type : new String[]{"steam", "electric", "fluid"}) add(cases, type, "bridge");
    }

    private static void add(List<MachineScenario> cases, String type, String part) {
        String id = "power/partial/" + type + (part.equals("bridge") ? "" : "-" + part);
        Job[] job = new Job[1];
        cases.add(new MachineScenario(id,
                "A finite " + type + " network works before its " + part + " chunk is unforced and queued "
                + "through the native provider. A real unload and 240 consecutive absent ticks are required "
                + "while other endpoints remain loaded and unchanged. The same ticket restores the chunk; "
                + "event-time typed NBT and finite resources survive, and native processing resumes without rebuilding.",
                TICKS, c -> {
                    String pipe = type.equals("steam") ? "steamadvantage:steam_pipe"
                            : type.equals("electric") ? "electricadvantage:electric_conduit" : "poweradvantage:fluid_pipe";
                    if (type.equals("steam")) {
                        c.steamSupply(3, 4, 3, 6000);
                        c.block(20, 4, 20, "steamadvantage:steam_crusher");
                        c.put(20, 4, 20, 0, "minecraft:stone", 8);
                        c.require(c.inventory(20, 4, 20).isItemValidForSlot(0, c.inventory(20, 4, 20).getStackInSlot(0)),
                                "Native stone crushing recipe is available");
                    } else if (type.equals("electric")) {
                        c.electricSupply(3, 4, 3, 6000);
                        NativeStacks.tag(c.inventory(3, 4, 3).getStackInSlot(0)).setString("PartialNetworkWitness", id);
                        c.block(20, 4, 20, "electricadvantage:electric_oven");
                        c.put(20, 4, 20, 0, "minecraft:potato", 8);
                    } else {
                        c.block(3, 4, 3, "poweradvantage:fluid_metal_tank");
                        c.tank(3, 4, 3, "minecraft:water", 6000);
                        c.block(20, 4, 20, "steamadvantage:steam_boiler_coal");
                        c.put(20, 4, 20, 0, "minecraft:coal", 1);
                    }
                    for (int x = 4; x <= 20; x++) c.block(x, 4, 3, pipe);
                    for (int z = 4; z < 20; z++) c.block(20, 4, z, pipe);
                    job[0] = new Job(c, type, part);
                    c.require(!job[0].sourceChunk.equals(job[0].consumerChunk)
                                    && !job[0].bridgeChunk.equals(job[0].sourceChunk)
                                    && !job[0].bridgeChunk.equals(job[0].consumerChunk),
                            "The route has genuinely separate source, bridge, and consumer chunks");
                    job[0].begin();
                }, c -> job[0].verify())
                .timed(c -> job[0].tick())
                .cleanup(c -> { if (job[0] != null) job[0].cleanup(); })
                .exclusive());
    }

    public static final class Job {
        final MachineContext c;
        final String type, part;
        final BlockPos source, consumer;
        final ChunkPos sourceChunk, consumerChunk, bridgeChunk, selected;
        final Gate gate = new Gate();
        final Isolation isolation;
        final List<Map<String, Object>> events = new ArrayList<>(), polls = new ArrayList<>();
        final TileEntity[] retainedTiles = new TileEntity[2];
        final Map<BlockPos, NBTTagCompound> frozenTags = new LinkedHashMap<>();
        Map<BlockPos, String> frozenBlocks;
        State frozenSource, frozenConsumer, previous, beforeRestore;
        Throwable eventFailure;
        boolean released, restoring, registered, ticketRestored;
        int frames;
        double paid, idle;

        Job(MachineContext c, String type, String part) {
            this.c = c; this.type = type; this.part = part;
            isolation = part.equals("source") ? null : new Isolation(type);
            source = c.at(3, 4, 3); consumer = c.at(20, 4, 20);
            sourceChunk = new ChunkPos(source); consumerChunk = new ChunkPos(consumer);
            bridgeChunk = new ChunkPos(c.at(20, 4, 3));
            selected = part.equals("source") ? sourceChunk : part.equals("consumer") ? consumerChunk : bridgeChunk;
            c.record("partial.type", type); c.record("partial.selectedPart", part);
            c.record("partial.events", events); c.record("partial.observations", polls);
            c.record("partial.selectedChunk", chunkCoordinates(selected));
            c.record("partial.isolationApplicable", isolation != null);
            if (isolation != null) c.record("partial.isolationSamples", isolation.samples);
        }

        void begin() {
            retainedTiles[0] = tile(source); retainedTiles[1] = tile(consumer);
            previous = state();
            checkResources(type, previous);
            MinecraftForge.EVENT_BUS.register(this); registered = true;
            poll(0);
        }

        @SubscribeEvent public void unloaded(ChunkEvent.Unload event) {
            if (event.getWorld() != c.world || !chunkPosition(event.getChunk()).equals(selected) || !released) return;
            try {
                gate.unloaded(elapsed());
                frozenBlocks = blocks(event.getChunk());
                for (BlockPos pos : new BlockPos[]{source, consumer}) if (new ChunkPos(pos).equals(selected)) {
                    TileEntity nativeTile = event.getChunk().getTileEntityMap().get(pos);
                    if (nativeTile == null) throw new AssertionError("Missing tile in actual unload callback");
                    frozenTags.put(pos, nativeTile.writeToNBT(new NBTTagCompound()).copy());
                    if (pos.equals(source)) frozenSource = read(nativeTile, true);
                    else frozenConsumer = read(nativeTile, false);
                }
                event("unload", elapsed(), event.getChunk(), false);
            } catch (Throwable failure) { eventFailure = failure; event("unload", elapsed(), event.getChunk(), false); }
        }

        @SubscribeEvent public void loaded(ChunkEvent.Load event) {
            if (event.getWorld() != c.world || !chunkPosition(event.getChunk()).equals(selected) || !released) return;
            try {
                gate.loaded(elapsed());
                if (!restoring) throw new AssertionError("Production reloaded the released chunk before explicit recovery");
                if (frozenBlocks == null || !frozenBlocks.equals(blocks(event.getChunk())))
                    throw new AssertionError("Reload changed the native network block states");
                for (Map.Entry<BlockPos, NBTTagCompound> entry : frozenTags.entrySet()) {
                    TileEntity nativeTile = event.getChunk().getTileEntityMap().get(entry.getKey());
                    if (nativeTile == null || !entry.getValue().equals(nativeTile.writeToNBT(new NBTTagCompound())))
                        throw new AssertionError("Actual unload/load callback typed NBT differs at " + entry.getKey());
                }
                event("load", elapsed(), event.getChunk(), true);
            } catch (Throwable failure) { eventFailure = failure; event("load", elapsed(), event.getChunk(), false); }
        }

        void tick() {
            int tick = elapsed();
            if (tick != frames + 1) throw new AssertionError("Partial fixture missed or duplicated a real world tick");
            frames = tick;
            if (eventFailure != null) throw new AssertionError("Native partial callback failed", eventFailure);
            boolean retained = retained();
            Chunk selectedChunk = c.readOnlyLoadedChunk(selected);
            boolean present = selectedChunk != null, ready = present && selectedChunk.isLoaded();
            boolean restore = false;
            if (tick >= RELEASE) {
                restore = gate.observe(tick, present, ready, retained);
                if (tick % 20 == 0 || tick == RELEASE) poll(tick);
            } else if (!retained || !present || !ready) throw new AssertionError("Fixture was not fully loaded before interruption");
            State now = state();
            checkResources(type, now);
            if (isolation != null && released && !restoring && !present && gate.unloadTick >= 0) {
                Chunk loadedSource = c.readOnlyLoadedChunk(sourceChunk);
                try {
                    isolation.observe(tick, now.source, loadedSource != null && loadedSource.isLoaded(), true);
                } finally {
                    c.record("partial.isolationAnchorTick", isolation.anchorTick);
                    c.record("partial.isolationAnchorSource", isolation.anchorSource);
                    c.record("partial.isolationLastTick", isolation.lastTick);
                    c.record("partial.isolationLastSource", isolation.lastSource);
                    c.record("partial.isolationStableTicks", isolation.lastTick - isolation.anchorTick);
                }
            }
            if (!type.equals("fluid")) {
                int length = type.equals("steam") ? 125 : 100;
                int delta = (now.output - previous.output) * length + now.progress - previous.progress;
                if (now.output < previous.output || now.output - previous.output > 1 || delta < -1 || delta > 1)
                    throw new AssertionError("Finite work/product state changed outside native operation");
                double work = delta == 1 ? type.equals("steam") ? 1.5 : 4 : 0;
                if (gate.recovered && delta < 0 && previous.consumer >= (type.equals("steam") ? 1.5 : 4))
                    throw new AssertionError("Powered work lost progress after native recovery");
                double loss = previous.total - now.total - work;
                double maximum = type.equals("steam") ? 0.125 : 0;
                if (loss < 0 || loss > maximum || loss * 16 != Math.rint(loss * 16))
                    throw new AssertionError("Partial unload lost or created power: " + loss);
                paid += work; idle += loss;
            }
            previous = now;
            if (restore) {
                if (isolation != null) isolation.complete();
                beforeRestore = now;
                restoring = true;
                gate.restore(tick);
                action("explicit-restore", tick);
                c.restoreOwnedChunk(selected);
                ticketRestored = true;
            }
            if (tick == RELEASE) {
                c.require(type.equals("fluid") ? now.converted > 0 && now.source < 6000 : now.output >= 1,
                        "A real working connected route precedes the unload");
                gate.request(tick);
                Chunk nativeChunk = c.readOnlyLoadedChunk(selected);
                if (nativeChunk == null || !nativeChunk.isLoaded()) throw new AssertionError("Selected chunk was not loaded at release");
                released = true;
                c.unforceOwnedChunk(selected);
                action("native-unload-queued", tick);
                c.world.getChunkProvider().unload(nativeChunk);
            }
        }

        State state() {
            State a = snapshot(source, true), b = snapshot(consumer, false);
            return new State(a.source, b.consumer, b.input, b.output, b.progress, b.converted);
        }

        State snapshot(BlockPos pos, boolean isSource) {
            Chunk chunk = c.readOnlyLoadedChunk(new ChunkPos(pos));
            if (chunk == null || !chunk.isLoaded()) {
                State frozen = isSource ? frozenSource : frozenConsumer;
                if (!new ChunkPos(pos).equals(selected) || frozen == null) throw new AssertionError("No native unload-time state for absent endpoint");
                return frozen;
            }
            return read(tile(pos), isSource);
        }

        State read(TileEntity nativeTile, boolean isSource) {
            if (type.equals("fluid")) {
                NativeFluidPort port = new NativeFluidPort(nativeTile, EnumFacing.UP);
                net.minecraftforge.fluids.FluidStack water = port.getFluidInTank(0);
                if (water != null && water.amount > 0 && water.getFluid() != net.minecraftforge.fluids.FluidRegistry.WATER)
                    throw new AssertionError("Fluid identity changed");
                int amount = water == null ? 0 : water.amount;
                double steam = isSource ? 0 : ((PoweredEntity)nativeTile).getEnergy(new ConduitType("steam"));
                return new State(isSource ? amount : 0, isSource ? 0 : amount, 0, 0, 0, steam);
            }
            double energy = ((PoweredEntity)nativeTile).getEnergy(new ConduitType(type.equals("steam") ? "steam" : "electricity"));
            if (isSource) return new State(energy, 0, 0, 0, 0, 0);
            IInventory inventory = (IInventory)nativeTile;
            String in = type.equals("steam") ? "minecraft:stone" : "minecraft:potato";
            String out = type.equals("steam") ? "minecraft:cobblestone" : "minecraft:baked_potato";
            int inputs = count(inventory.getStackInSlot(0), in), outputs = 0;
            for (int slot = 1; slot < inventory.getSizeInventory(); slot++) outputs += count(inventory.getStackInSlot(slot), out);
            NBTTagCompound tag = nativeTile.writeToNBT(new NBTTagCompound());
            return new State(0, energy, inputs, outputs, tag.getShort(type.equals("steam") ? "progress" : "cookTime"), 0);
        }

        TileEntity tile(BlockPos pos) {
            Chunk loaded = c.readOnlyLoadedChunk(new ChunkPos(pos));
            if (loaded == null || !loaded.isLoaded()) throw new AssertionError("Observer refuses to load an absent chunk");
            TileEntity tile = loaded.getTileEntityMap().get(pos);
            if (tile == null || !tile.getPos().equals(pos)) throw new AssertionError("Missing/moved native endpoint");
            return tile;
        }

        boolean retained() {
            for (ChunkPos chunk : new ChunkPos[]{sourceChunk, consumerChunk, bridgeChunk}) {
                if (chunk.equals(selected)) continue;
                Chunk current = c.readOnlyLoadedChunk(chunk);
                if (current == null || !current.isLoaded()) return false;
            }
            if (!sourceChunk.equals(selected) && tile(source) != retainedTiles[0]) return false;
            return consumerChunk.equals(selected) || tile(consumer) == retainedTiles[1];
        }

        Map<BlockPos, String> blocks(Chunk chunk) {
            Map<BlockPos, String> result = new LinkedHashMap<>();
            for (BlockPos pos : c.positions) if (new ChunkPos(pos).equals(selected))
                result.put(pos, chunk.getBlockState(pos).toString());
            return result;
        }

        void verify() {
            gate.complete();
            c.require(frames == TICKS, "All declared native ticks were observed");
            c.require(gate.absentSamples >= ABSENT_TICKS && gate.recovered,
                    "Actual selected chunk absence lasted 240 ticks before a real later reload");
            if (isolation != null) {
                isolation.complete();
                c.require(isolation.lastTick - isolation.anchorTick >= 220,
                        "Loaded source transferred nothing across the absent bridge/consumer for at least 220 measured ticks");
            }
            c.require(beforeRestore != null && (type.equals("fluid")
                            ? previous.converted > beforeRestore.converted && previous.source < beforeRestore.source
                            : previous.output > beforeRestore.output),
                    "Restored native network resumes finite work and transfers without rebuilding or refill");
            if (!type.equals("fluid")) c.require(6000 - previous.total == paid + idle,
                    "Finite electrical/steam reserve pays only real work and native idle loss");
            c.record("partial.completed", true); c.record("partial.absentTicks", gate.absentSamples);
            c.record("partial.unloadTick", gate.unloadTick); c.record("partial.loadTick", gate.loadTick);
            c.record("partial.restoreTick", gate.restoreTick);
            c.record("partial.measuredTicks", frames); c.record("partial.finalSource", previous.source);
            c.record("partial.finalConsumer", previous.consumer); c.record("partial.finalOutput", previous.output);
            c.record("partial.convertedWater", previous.converted);
            c.record("partial.paidWork", paid); c.record("partial.idleLoss", idle);
            poll(elapsed());
        }

        void cleanup() {
            if (registered) { MinecraftForge.EVENT_BUS.unregister(this); registered = false; }
            if (released && !ticketRestored) {
                action("cleanup-ticket-restore", elapsed());
                restoring = true; c.restoreOwnedChunk(selected); ticketRestored = true;
            }
            c.record("partial.cleanupCalled", true);
        }

        void event(String kind, int tick, Chunk chunk, boolean equal) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("operation", kind); row.put("tick", tick); row.put("chunk", chunkCoordinates(chunkPosition(chunk)));
            row.put("explicitRecovery", restoring); row.put("typedStateEqual", equal);
            List<Map<String, Object>> tags = new ArrayList<>();
            for (Map.Entry<BlockPos, NBTTagCompound> entry : frozenTags.entrySet()) {
                Map<String, Object> tag = new LinkedHashMap<>();
                tag.put("pos", new int[]{entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ()});
                tag.put("nbtBase64", encode(entry.getValue())); tags.add(tag);
                TileEntity observed = chunk.getTileEntityMap().get(entry.getKey());
                if (observed != null) tag.put("observedNbtBase64", encode(observed.writeToNBT(new NBTTagCompound())));
            }
            row.put("unloadState", tags);
            List<Map<String, Object>> nativeBlocks = new ArrayList<>();
            for (Map.Entry<BlockPos, String> block : blocks(chunk).entrySet()) {
                Map<String, Object> nativeBlock = new LinkedHashMap<>();
                nativeBlock.put("pos", new int[]{block.getKey().getX(), block.getKey().getY(), block.getKey().getZ()});
                nativeBlock.put("state", block.getValue()); nativeBlocks.add(nativeBlock);
            }
            row.put("networkBlocks", nativeBlocks);
            if (eventFailure != null) row.put("failure", eventFailure.toString());
            events.add(row); c.actions.add(row);
        }

        void poll(int tick) {
            Chunk found = c.readOnlyLoadedChunk(selected);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tick", tick); row.put("selectedPresent", found != null);
            row.put("selectedLoaded", found != null && found.isLoaded());
            row.put("retainedLoaded", retained()); row.put("unloadTick", gate.unloadTick);
            row.put("loadTick", gate.loadTick); row.put("explicitRecovery", restoring);
            polls.add(row);
        }

        void action(String name, int tick) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("operation", name); row.put("tick", tick); row.put("chunk", chunkCoordinates(selected));
            c.actions.add(row);
        }

        int elapsed() { return Math.toIntExact(c.world.getTotalWorldTime() - c.activationTick); }
    }

    /** Anchoring the first actual absent sample grants at most one native unload-phase grace tick. */
    static final class Isolation {
        final String type;
        final List<Map<String, Object>> samples = new ArrayList<>();
        int anchorTick = -1, lastTick = -1;
        double anchorSource, lastSource;
        Isolation(String type) {
            if (!type.equals("steam") && !type.equals("electric") && !type.equals("fluid"))
                throw new IllegalArgumentException("Unknown isolation resource");
            this.type = type;
        }
        void observe(int tick, double source, boolean loaded, boolean absent) {
            if (!loaded || !absent || !Double.isFinite(source) || source < 0)
                throw new AssertionError("Isolation must observe an actually loaded finite source and absent target");
            if (anchorTick >= 0 && tick != lastTick + 1)
                throw new AssertionError("Isolation source observations are not consecutive native ticks");
            if (anchorTick < 0) { anchorTick = tick; anchorSource = source; }
            double expected = anchorSource - (type.equals("steam") ? (tick - anchorTick) * 0.0625 : 0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tick", tick); row.put("source", source); row.put("sourceLoaded", loaded);
            row.put("selectedAbsent", absent); row.put("expectedSource", expected);
            samples.add(row); lastTick = tick; lastSource = source;
            if (source != expected) throw new AssertionError("Ghost transfer across an absent network section: expected source "
                    + expected + ", observed " + source);
        }
        void complete() {
            if (anchorTick < 0 || lastTick - anchorTick < 220 || samples.size() != lastTick - anchorTick + 1)
                throw new AssertionError("Missing at least 220 consecutive isolation source ticks");
        }
    }

    static final class Gate {
        int requestTick = -1, unloadTick = -1, restoreTick = -1, loadTick = -1, absentStart = -1, absentSamples;
        boolean unexpectedReload, recovered;
        void request(int tick) {
            if (requestTick >= 0) throw new AssertionError("Duplicate release request");
            requestTick = tick;
        }
        void unloaded(int tick) {
            if (requestTick < 0 || restoreTick >= 0 || unloadTick >= 0) throw new AssertionError("Unexpected native unload event");
            unloadTick = tick;
        }
        void loaded(int tick) {
            if (restoreTick < 0) { unexpectedReload = true; return; }
            if (loadTick >= 0) throw new AssertionError("Unexpected duplicate native load event");
            loadTick = tick;
        }
        boolean observe(int tick, boolean present, boolean ready, boolean retained) {
            if (!retained) throw new AssertionError("A retained network chunk/tile was lost");
            if (unexpectedReload) throw new AssertionError("Released network chunk reloaded itself");
            if (requestTick < 0) return false;
            if (restoreTick < 0) {
                if (present) {
                    if (absentStart >= 0) throw new AssertionError("Released chunk did not stay absent");
                    if (tick - requestTick >= 80) throw new AssertionError("Native chunk never genuinely unloaded");
                    return false;
                }
                if (unloadTick < 0) {
                    if (tick - requestTick >= 80) throw new AssertionError("Missing actual unload callback");
                    return false;
                }
                if (absentStart < 0) absentStart = tick;
                absentSamples = tick - absentStart;
                return absentSamples >= ABSENT_TICKS;
            }
            if (tick - restoreTick > 80 && !recovered) throw new AssertionError("Native chunk did not reload within 80 ticks");
            if (present && ready && loadTick >= restoreTick && tick > loadTick) recovered = true;
            return false;
        }
        void restore(int tick) {
            if (restoreTick >= 0 || absentSamples < ABSENT_TICKS || unloadTick < 0) throw new AssertionError("Premature recovery");
            restoreTick = tick;
        }
        void complete() {
            if (!recovered || unexpectedReload || absentSamples < ABSENT_TICKS || loadTick < restoreTick)
                throw new AssertionError("Missing native partial-unload/recovery proof");
        }
    }

    static final class State {
        final double source, consumer, total, converted;
        final int input, output, progress;
        State(double source, double consumer, int input, int output, int progress, double converted) {
            this.source = source; this.consumer = consumer; this.input = input; this.output = output;
            this.progress = progress; this.converted = converted; total = source + consumer;
        }
    }

    static void checkResources(String type, State state) {
        if (!Double.isFinite(state.source) || !Double.isFinite(state.consumer) || state.source < 0 || state.consumer < 0)
            throw new AssertionError("Invalid finite native reserve");
        if (type.equals("fluid")) {
            if (!Double.isFinite(state.converted) || state.converted < 0 || state.total + state.converted != 6000)
                throw new AssertionError("Water/steam conservation failed");
        } else if (state.input < 0 || state.output < 0 || state.input + state.output != 8
                || state.progress < 0 || state.progress >= (type.equals("steam") ? 125 : 100) || state.total > 6000) {
            throw new AssertionError("Finite native item/energy state changed");
        }
    }

    static int[] chunkCoordinates(ChunkPos chunk) { return new int[]{chunk.chunkXPos, chunk.chunkZPos}; }
    static ChunkPos chunkPosition(Chunk chunk) { return chunk.getChunkCoordIntPair(); }
    private static int count(ItemStack stack, String name) {
        int amount = NativeStacks.count(stack);
        if (amount != 0 && !name.equals(String.valueOf(stack.getItem().getRegistryName())))
            throw new AssertionError("Unexpected material in finite processing fixture");
        return amount;
    }
    private static String encode(NBTTagCompound tag) {
        try {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(tag, output);
            return java.util.Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot record typed native NBT", failure); }
    }
}
