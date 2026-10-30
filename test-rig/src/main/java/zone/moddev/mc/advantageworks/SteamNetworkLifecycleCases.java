package zone.moddev.mc.advantageworks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Real timed network stimuli and finite natural-tick work, never calls a machine update directly. */
final class SteamNetworkLifecycleCases {
    private static final int SHORT_TICKS = 1040;
    private static final int ENDURANCE_TICKS = 36000;
    private static final PropertyBool ACTIVE = PropertyBool.create("active");

    private SteamNetworkLifecycleCases() {}

    static void register(List<MachineScenario> cases) {
        for (boolean gap : new boolean[]{false, true}) {
            for (boolean negative : new boolean[]{false, true}) shortCase(cases, gap, negative);
        }
    }

    static void registerEnduranceSmoke(List<MachineScenario> cases) {
        endurance(cases, 1200, "steam/lifecycle/network-endurance-smoke");
    }

    static void registerEndurance(List<MachineScenario> cases) {
        endurance(cases, ENDURANCE_TICKS, "steam/lifecycle/network-endurance-30min");
    }

    private static void shortCase(List<MachineScenario> cases, boolean gap, boolean negative) {
        String kind = gap ? "pipe-gap" : "switch";
        String id = "steam/lifecycle/network-" + kind + (negative ? "-stays-off" : "-off-on");
        Job[] job = new Job[1];
        cases.add(new MachineScenario(id,
                "A real 16-block steam route drives finite crushing, is interrupted at measured tick 240, "
                + "must stop after its local buffer drains, and is reconnected at tick 480 only after the closed "
                + "route passes its gate. A permanently disconnected control must spend only idle steam. "
                + "Every measured tick reconciles work, all input/output items, and the finite steam ledger.",
                SHORT_TICKS, c -> {
                    c.steamSupply(3, 4, 8, 6000);
                    c.block(11, 5, 8, negative && !gap ? "minecraft:redstone_block" : "minecraft:air");
                    for (int x = 4; x <= 18; x++) {
                        c.block(x, 4, 8, x == 11 ? "steamadvantage:steam_switch"
                                : gap && negative && x == 15 ? "minecraft:air" : "steamadvantage:steam_pipe");
                    }
                    c.block(19, 4, 8, "steamadvantage:steam_crusher");
                    c.put(19, 4, 8, 0, "minecraft:stone", 8);
                    c.require(c.inventory(19, 4, 8).isItemValidForSlot(0, c.inventory(19, 4, 8).getStackInSlot(0)),
                            "The active native crushing registry accepts the finite stone stock");
                    c.require(c.world.getBlockState(c.at(11, 4, 8)).getValue(ACTIVE) == !(negative && !gap),
                            "Real redstone establishes the declared initial switch state");
                    job[0] = new Job(c, SHORT_TICKS, 19, false, gap, negative);
                    job[0].sources.add(new BlockPos(3, 4, 8));
                    job[0].begin(c, 8);
                }, c -> job[0].finish(c))
                .timed(c -> job[0].tick(c))
                .cleanup(c -> { if (job[0] != null) job[0].cleanup(c); }));
    }

    private static void endurance(List<MachineScenario> cases, int ticks, String id) {
        Job[] job = new Job[1];
        cases.add(new MachineScenario(id,
                "Eight finite 10000-steam tanks feed a real crusher through pipes for exactly " + ticks
                + " measured world ticks. Vanilla hoppers draw 320 stone from a chest and deliver products "
                + "to another chest. Every post-warmup tick must perform productive work, every completed batch "
                + "is recorded, and all items and paid/idle steam reconcile. Nothing is refilled after setup.",
                ticks, c -> {
                    for (int z = 2; z <= 9; z++) {
                        c.steamSupply(3, 4, z, 10000);
                        c.block(4, 4, z, "steamadvantage:steam_pipe");
                    }
                    for (int x = 5; x <= 21; x++) c.block(x, 4, 8, "steamadvantage:steam_pipe");
                    c.block(22, 4, 8, "steamadvantage:steam_crusher");
                    c.block(22, 5, 8, "minecraft:hopper");
                    c.block(22, 6, 8, "minecraft:chest");
                    c.block(23, 3, 8, "minecraft:chest");
                    c.block(22, 3, 8, "minecraft:hopper");
                    c.facing(22, 3, 8, "east");
                    for (int slot = 0; slot < 5; slot++) c.put(22, 6, 8, slot, "minecraft:stone", 64);
                    c.require(c.inventory(22, 4, 8).isItemValidForSlot(0, new ItemStack(net.minecraft.init.Blocks.STONE)),
                            "Endurance uses a real registered stone crushing recipe");
                    job[0] = new Job(c, ticks, 22, true, false, false);
                    for (int z = 2; z <= 9; z++) job[0].sources.add(new BlockPos(3, 4, z));
                    job[0].begin(c, 320);
                }, c -> job[0].finish(c))
                .timed(c -> job[0].tick(c))
                .cleanup(c -> { if (job[0] != null) job[0].cleanup(c); })
                .exclusive());
    }

    private static final class Job {
        final Clock clock;
        final int crusherX;
        final boolean endurance, gap, negative;
        final List<BlockPos> sources = new ArrayList<>();
        final List<Map<String, Object>> samples = new ArrayList<>(), batches = new ArrayList<>(), events = new ArrayList<>();
        FlowLedger ledger;
        Frame closed;
        int checkpoints;
        long startedNanos;

        Job(MachineContext c, int ticks, int crusherX, boolean endurance, boolean gap, boolean negative) {
            this.clock = new Clock(ticks); this.crusherX = crusherX;
            this.endurance = endurance; this.gap = gap; this.negative = negative;
            c.record("lifecycle.checkpoints", samples);
            c.record("lifecycle.completedBatches", batches);
            c.record("lifecycle.controlEvents", events);
        }

        void begin(MachineContext c, int stock) {
            Frame initial = capture(c);
            if (initial.input != stock || initial.output != 0 || initial.progress != 0)
                throw new AssertionError("Invalid finite lifecycle stock");
            ledger = new FlowLedger(stock, initial, endurance);
            sample(c, 0, initial);
            c.record("lifecycle.initialSteam", initial.totalSteam());
            c.record("lifecycle.seededItems", stock);
            c.record("lifecycle.requestedTicks", clock.limit);
            if (endurance && clock.limit == ENDURANCE_TICKS) {
                c.record("lifecycle.wallClockConvention",
                        "First measured tick to last measured tick: 36000 ticks span about 1799.95 seconds at 20 TPS; minimum 1799 seconds");
            }
        }

        void tick(MachineContext c) {
            if (c.settingUp) throw new IllegalStateException("Timed lifecycle work cannot run during setup");
            int elapsed = Math.toIntExact(c.world.getTotalWorldTime() - c.activationTick);
            clock.advance(elapsed);
            if (elapsed == 1) startedNanos = System.nanoTime();
            Frame frame = capture(c);
            int oldProducts = ledger.previous.output;
            ledger.accept(frame);
            if (frame.output != oldProducts) {
                Map<String, Object> batch = row(elapsed, frame);
                batch.put("ordinal", frame.output);
                batches.add(batch);
            }
            if (endurance) {
                if (elapsed % 1000 == 0 || elapsed == clock.limit) {
                    c.require(frame.output >= Math.max(0, (elapsed - 32) / 125)
                                    && frame.output <= elapsed / 125,
                            "Finite endurance output keeps the native 125-tick rate at tick " + elapsed);
                    if (!samples.isEmpty() && elapsed >= 1000) {
                        int previousProducts = ((Number)samples.get(samples.size() - 1).get("products")).intValue();
                        c.require(frame.output > previousProducts, "Each endurance checkpoint contains genuinely new products");
                    }
                    sample(c, elapsed, frame); checkpoints++;
                }
                return;
            }
            if (elapsed == 40 || elapsed == 240 || elapsed == 400 || elapsed == 480 || elapsed == 560 || elapsed == clock.limit) {
                sample(c, elapsed, frame); checkpoints++;
                if (negative) {
                    c.require(frame.output == 0 && frame.input == 8 && frame.progress == 0 && frame.machineSteam == 0,
                            "Disconnected control never works or receives steam at tick " + elapsed);
                    c.require(frame.totalSteam() == 6000 - elapsed * 0.0625,
                            "Disconnected control spends only exact source idle leakage");
                    return;
                }
                if (elapsed == 40) c.require(frame.machineSteam > 0 && frame.progress > 0,
                        "The route demonstrably supplies real productive work before interruption");
                if (elapsed == 240) {
                    c.require(frame.output >= 1, "A complete real product proves the pre-interruption route worked");
                    control(c, elapsed, true);
                } else if (elapsed == 400) {
                    c.require(frame.machineSteam == 0 && frame.progress == 0,
                            "The interrupted route has exhausted its real local buffer and progress");
                    closed = frame;
                } else if (elapsed == 480) {
                    c.require(closed != null && frame.input == closed.input && frame.output == closed.output
                                    && frame.progress == 0 && frame.machineSteam == 0,
                            "An eighty-tick closed interval makes no products or hidden progress");
                    c.require(closed.totalSteam() - frame.totalSteam() == 80 * 0.0625,
                            "No steam crosses the closed route; only the source's exact idle loss remains");
                    control(c, elapsed, false);
                } else if (elapsed == 560) {
                    c.require(frame.machineSteam > 0 && frame.progress > 0,
                            "Reconnection recharges the emptied consumer and resumes real work without replacement");
                } else if (elapsed == clock.limit) {
                    c.require(closed != null && frame.output >= closed.output + 3,
                            "Reconnected route completes at least three additional finite batches");
                }
            }
        }

        void control(MachineContext c, int elapsed, boolean interrupt) {
            BlockPos local = gap ? new BlockPos(15, 4, 8) : new BlockPos(11, 5, 8);
            String before = gap ? interrupt ? "steamadvantage:steam_pipe" : "minecraft:air"
                    : interrupt ? "minecraft:air" : "minecraft:redstone_block";
            String after = gap ? interrupt ? "minecraft:air" : "steamadvantage:steam_pipe"
                    : interrupt ? "minecraft:redstone_block" : "minecraft:air";
            BlockPos pos = c.at(local.getX(), local.getY(), local.getZ());
            if (c.settingUp || !c.positions.contains(pos)) throw new IllegalStateException("Undeclared timed control location");
            IBlockState old = c.world.getBlockState(pos);
            if (!before.equals(String.valueOf(old.getBlock().getRegistryName()))) throw new AssertionError("Timed control precondition changed");
            if (c.world.getTileEntity(pos) != null) throw new IllegalStateException("Timed control cannot replace machine state");
            Block replacement = Block.getBlockFromName(after);
            if (replacement == null || !after.equals(String.valueOf(replacement.getRegistryName()))
                    || replacement.hasTileEntity(replacement.getDefaultState())) throw new IllegalArgumentException("Invalid control block");
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("tick", elapsed); event.put("position", new int[]{pos.getX(), pos.getY(), pos.getZ()});
            event.put("before", old.toString()); event.put("requested", after);
            event.put("method", "native setBlockState(flags=3), with player-destruction callback on removal; no stock/NBT writes");
            events.add(event);
            if (!c.world.setBlockState(pos, replacement.getDefaultState(), 3)) throw new AssertionError("Native control placement/removal failed");
            if ("minecraft:air".equals(after)) old.getBlock().onPlayerDestroy(c.world, pos, old);
            event.put("after", c.world.getBlockState(pos).toString());
            c.require(after.equals(String.valueOf(c.world.getBlockState(pos).getBlock().getRegistryName())),
                    "The declared timed network stimulus actually changes the world");
            c.require(c.world.getBlockState(c.at(11, 4, 8)).getValue(ACTIVE) == (gap || !interrupt),
                    "The switch reflects actual redstone, not a directly seeded active property");
        }

        Frame capture(MachineContext c) {
            double[] steam = new double[sources.size()];
            for (int i = 0; i < steam.length; i++) {
                BlockPos pos = sources.get(i);
                steam[i] = c.energy(pos.getX(), pos.getY(), pos.getZ(), "steam");
            }
            int input = countAllowed(c.inventory(crusherX, 4, 8), 0, 1, "minecraft:stone");
            int output = countAllowed(c.inventory(crusherX, 4, 8), 1, 6, "minecraft:cobblestone");
            if (endurance) {
                input += countAllowed(c.inventory(22, 5, 8), 0, 5, "minecraft:stone");
                input += countAllowed(c.inventory(22, 6, 8), 0, 27, "minecraft:stone");
                output += countAllowed(c.inventory(22, 3, 8), 0, 5, "minecraft:cobblestone");
                output += countAllowed(c.inventory(23, 3, 8), 0, 27, "minecraft:cobblestone");
            }
            return new Frame(steam, c.energy(crusherX, 4, 8, "steam"),
                    input, output, c.nbt(crusherX, 4, 8).getShort("progress"));
        }

        void sample(MachineContext c, int elapsed, Frame frame) {
            samples.add(row(elapsed, frame));
            BlockPos min = c.at(0, 0, 0), max = c.at(31, 31, 31);
            c.require(c.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1, max.getY() + 1, max.getZ() + 1)).isEmpty(), "No lifecycle cargo is spilled as dropped entities");
        }

        void finish(MachineContext c) {
            long wallNanos = System.nanoTime() - startedNanos;
            c.record("lifecycle.measuredWallSeconds", wallNanos / 1.0e9);
            c.record("lifecycle.wallStartMeasuredTick", 1);
            c.record("lifecycle.wallEndMeasuredTick", clock.last);
            clock.complete();
            c.require(ledger.frames == clock.limit, "Every requested world tick has a measured finite-resource ledger");
            c.require(checkpoints == (endurance ? clock.limit / 1000 + (clock.limit % 1000 == 0 ? 0 : 1) : 6),
                    "Every declared lifecycle checkpoint was observed");
            c.require(events.size() == (endurance || negative ? 0 : 2),
                    "The exact declared interruption/reconnection actions occurred");
            c.require(batches.size() == ledger.previous.output, "Every output batch has its own measured receipt");
            c.require(ledger.initialSteam - ledger.previous.totalSteam() == ledger.paidTicks * 1.5 + ledger.idleLoss,
                    "Final finite steam reconciles every productive tick and observed quantized idle loss");
            if (endurance && clock.limit == ENDURANCE_TICKS) {
                c.require(provesThirtyMinutes(ledger.frames, wallNanos),
                        "Thirty-minute proof includes 36000 natural ticks and at least 1799 real seconds between tick-1/tick-36000 observations");
            }
            c.record("lifecycle.measuredTicks", ledger.frames);
            c.record("lifecycle.paidWorkTicks", ledger.paidTicks);
            c.record("lifecycle.lostProgressTicks", ledger.lostProgress);
            c.record("lifecycle.idleSteamLoss", ledger.idleLoss);
            c.record("lifecycle.finalSteam", ledger.previous.totalSteam());
        }

        void cleanup(MachineContext c) {
            c.record("lifecycle.cleanupCalled", true);
            c.record("lifecycle.lastMeasuredTick", clock.last);
        }
    }

    private static int countAllowed(IInventory inventory, int from, int to, String item) {
        if (inventory.getSizeInventory() < to) throw new AssertionError("Native inventory shape changed");
        int count = 0;
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            int amount = stack == null ? 0 : stack.getCount();
            if (amount == 0) continue;
            if (amount < 0 || !item.equals(String.valueOf(stack.getItem().getRegistryName())))
                throw new AssertionError("Wrong material in native automation path: " + item + " slot " + slot);
            count += amount;
        }
        return count;
    }

    private static Map<String, Object> row(int elapsed, Frame frame) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tick", elapsed); row.put("sourceSteam", frame.sourceSteam.clone());
        row.put("machineSteam", frame.machineSteam); row.put("totalSteam", frame.totalSteam());
        row.put("inputs", frame.input); row.put("products", frame.output); row.put("progress", frame.progress);
        return row;
    }

    static boolean provesThirtyMinutes(int ticks, long elapsedNanos) {
        return ticks == ENDURANCE_TICKS && elapsedNanos >= java.util.concurrent.TimeUnit.SECONDS.toNanos(1799);
    }

    static final class Clock {
        final int limit;
        int last;
        Clock(int limit) {
            if (limit < 1 || limit > 36000) throw new IllegalArgumentException("Invalid lifecycle duration");
            this.limit = limit;
        }
        void advance(int elapsed) {
            if (elapsed != last + 1 || elapsed > limit) throw new AssertionError("Missing, duplicate, or out-of-window natural tick: " + elapsed);
            last = elapsed;
        }
        void complete() {
            if (last != limit) throw new AssertionError("Lifecycle ended without all requested native ticks");
        }
    }

    static final class Frame {
        final double[] sourceSteam;
        final double machineSteam;
        final int input, output, progress;
        Frame(double[] sourceSteam, double machineSteam, int input, int output, int progress) {
            this.sourceSteam = sourceSteam.clone(); this.machineSteam = machineSteam;
            this.input = input; this.output = output; this.progress = progress;
        }
        double totalSteam() {
            double total = machineSteam;
            for (double source : sourceSteam) total += source;
            return total;
        }
    }

    /** Independent accounting, not a duplicate implementation of network distribution or machine ticking. */
    static final class FlowLedger {
        final int stock;
        final boolean continuous;
        final double initialSteam;
        Frame previous;
        int frames, paidTicks, lostProgress;
        double idleLoss;
        FlowLedger(int stock, Frame initial, boolean continuous) {
            this.stock = stock; this.previous = initial; this.continuous = continuous;
            initialSteam = initial.totalSteam();
            validate(initial);
        }
        void accept(Frame frame) {
            validate(frame);
            if (frame.sourceSteam.length != previous.sourceSteam.length) throw new AssertionError("Source count changed");
            int productDelta = frame.output - previous.output;
            int workDelta = productDelta * 125 + frame.progress - previous.progress;
            if (productDelta < 0 || productDelta > 1 || workDelta < -1 || workDelta > 1)
                throw new AssertionError("Native work/products skipped, duplicated or lost");
            if (continuous && frames >= 32 && workDelta != 1) throw new AssertionError("Active endurance stalled after its bounded warmup");
            int paid = workDelta == 1 ? 1 : 0;
            double idle = previous.totalSteam() - frame.totalSteam() - paid * 1.5;
            if (idle < 0 || idle > (frame.sourceSteam.length + 1) * 0.0625 || idle * 16 != Math.rint(idle * 16))
                throw new AssertionError("Steam was created/lost outside productive work and native idle leakage: " + idle);
            paidTicks += paid;
            if (workDelta == -1) lostProgress++;
            idleLoss += idle;
            frames++;
            previous = frame;
        }
        private void validate(Frame frame) {
            if (frame.input < 0 || frame.output < 0 || frame.input + frame.output != stock
                    || frame.progress < 0 || frame.progress >= 125) throw new AssertionError("Finite material/progress ledger does not reconcile");
            if (!Double.isFinite(frame.machineSteam) || frame.machineSteam < 0 || frame.machineSteam > 50)
                throw new AssertionError("Invalid native crusher steam buffer");
            for (double source : frame.sourceSteam) if (!Double.isFinite(source) || source < 0 || source > 10000)
                throw new AssertionError("Invalid finite steam source");
        }
    }
}
