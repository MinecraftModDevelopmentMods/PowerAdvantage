package zone.moddev.mc.advantageworks;

import java.util.List;
import net.minecraft.nbt.CompoundNBT;

/** One real loaded-machine tick, with exact work, control and resource observations. */
final class SteamReloadControlCases {
    private static final int X = 8, Y = 4, Z = 8;
    private static final Class<?>[] NONE = new Class<?>[0];
    private SteamReloadControlCases() {}

    static void register(List<MachineScenario> cases) {
        cases.add(new MachineScenario("steam/lifecycle/crusher-comparator-matrix",
                "Actual empty, one-item, half-stack and full-stack crusher inputs produce comparator 0, 1, 8 and 15.", 1,
                c -> {
                    int[] stock = {0, 1, 32, 64};
                    for (int i = 0; i < stock.length; i++) {
                        int x = 3 + i * 6;
                        c.block(x, Y, Z, "steamadvantage:steam_crusher");
                        c.put(x, Y, Z, 0, "minecraft:stone", stock[i]);
                    }
                }, holding(new int[]{3, 9, 15, 21}, c -> {
                    int[] stock = {0, 1, 32, 64}, expected = {0, 1, 8, 15}, measured = new int[4];
                    for (int i = 0; i < stock.length; i++) {
                        int x = 3 + i * 6;
                        c.require(c.count(x, Y, Z, "minecraft:stone") == stock[i], "Unpowered comparator stock remains exact: " + stock[i]);
                        measured[i] = ((Number)c.invoke(x, Y, Z, "getComparatorOutput", NONE)).intValue();
                        c.require(measured[i] == expected[i], "Actual crusher comparator " + stock[i] + " items -> " + expected[i]);
                    }
                    c.record("comparator.inputs", stock);
                    c.record("comparator.actual", measured);
                })).exclusive());
        crusher(cases, "unpowered", false, 40);
        crusher(cases, "signalled", true, 40);
        crusher(cases, "no-steam", false, 0);
        furnace(cases, false);
        furnace(cases, true);
    }

    private static void crusher(List<MachineScenario> cases, String name, boolean signalled, float steam) {
        cases.add(new MachineScenario("steam/lifecycle/crusher-first-loaded-" + name,
                "One native tick observes live redstone before touching saved crusher progress; no reload grace or replacement.", 1,
                c -> {
                    c.block(X, Y, Z, "steamadvantage:steam_crusher");
                    if (signalled) c.block(X - 1, Y, Z, "minecraft:redstone_block");
                    c.put(X, Y, Z, 0, "minecraft:stone", 1);
                    CompoundNBT tag = new CompoundNBT(); tag.putShort("progress", (short)106); c.seed(X, Y, Z, tag);
                    c.power(X, Y, Z, "steam", steam);
                    c.require(c.nbt(X, Y, Z).getShort("progress") == 106, "Loaded work is 106 before the first native tick");
                }, holding(new int[]{X}, c -> {
                    int expected = signalled || steam == 0 ? 105 : 107;
                    int actual = c.nbt(X, Y, Z).getShort("progress");
                    float remaining = c.energy(X, Y, Z, "steam");
                    float wanted = steam == 0 ? 0 : signalled ? 39.9375F : 38.4375F;
                    c.record("firstLoaded.progress", actual);
                    c.record("firstLoaded.steam", remaining);
                    c.record("firstLoaded.signalled", signalled);
                    c.require(actual == expected, "The first native tick gives exact progress " + expected);
                    c.require(remaining == wanted, "Only the intended first-tick work and steam leakage are charged");
                    c.require(c.count(X, Y, Z, "minecraft:stone") == 1, "Incomplete work retains its one input");
                    c.require(c.count(X, Y, Z, "minecraft:cobblestone") == 0, "One tick does not invent a complete output");
                })).exclusive());
    }

    private static void furnace(List<MachineScenario> cases, boolean signalled) {
        cases.add(new MachineScenario("steam/lifecycle/furnace-first-loaded-" + (signalled ? "signalled" : "unpowered"),
                "A burning, partly completed furnace uses the real control signal for its first steam heat bonus and work increment.", 1,
                c -> {
                    c.block(X, Y, Z, "steamadvantage:steam_furnace");
                    if (signalled) c.block(X - 1, Y, Z, "minecraft:redstone_block");
                    c.put(X, Y, Z, 1, "minecraft:sand", 1);
                    CompoundNBT tag = new CompoundNBT();
                    tag.putIntArray("Data", new int[]{Float.floatToRawIntBits(.25F), 0, 0, 10, 10, Float.floatToRawIntBits(1000)});
                    c.seed(X, Y, Z, tag); c.power(X, Y, Z, "steam", 40);
                    c.require(c.nbt(X, Y, Z).getIntArray("Data")[3] == 10, "Loaded burning state exists before any tick");
                }, holding(new int[]{X}, c -> {
                    int[] data = c.nbt(X, Y, Z).getIntArray("Data");
                    c.require(data.length == 6, "Furnace reports all native process fields");
                    float heat = Float.intBitsToFloat(data[5]), progress = Float.intBitsToFloat(data[0]);
                    float expectedHeat = signalled ? 989.5F : 1027F;
                    float expectedProgress = .25F + 3.5E-6F * expectedHeat;
                    c.record("firstLoaded.heat", heat); c.record("firstLoaded.progress", progress);
                    c.record("firstLoaded.steam", c.energy(X, Y, Z, "steam"));
                    c.record("firstLoaded.signalled", signalled);
                    c.require(data[3] == 9 && data[4] == 10, "One real tick burns exactly one stored fuel tick");
                    c.require(heat == expectedHeat, "First-tick heat uses 2.5 ordinary heat or 15 steam-assisted heat as appropriate");
                    c.require(Math.abs(progress - expectedProgress) < .0000001F, "Saved quarter-cycle work receives the correct first-tick increment");
                    c.require(c.energy(X, Y, Z, "steam") == (signalled ? 40 : 39.5F), "Only a permitted steam bonus costs its rated 0.5 steam");
                    c.require(c.count(X, Y, Z, "minecraft:sand") == 1 && c.count(X, Y, Z, "minecraft:glass") == 0,
                            "One loaded tick retains the unfinished input and does not fabricate output");
                })).exclusive());
    }
    private static java.util.function.Consumer<MachineContext> holding(int[] columns, java.util.function.Consumer<MachineContext> checks) {
        return c -> {
            Throwable problem = null;
            try {
                checks.accept(c);
            } catch (RuntimeException | Error failure) {
                problem = failure;
                throw failure;
            } finally {
                try {
                    stopMeasuredTiles(c, columns);
                } catch (RuntimeException | Error holdFailure) {
                    if (problem != null) problem.addSuppressed(holdFailure);
                    else throw holdFailure;
                }
            }
        };
    }

    private static void stopMeasuredTiles(MachineContext c, int... columns) {
        java.util.List<java.util.Map<String, Object>> held = new java.util.ArrayList<>();
        for (int x : columns) {
            net.minecraft.tileentity.TileEntity actual = c.tile(x, Y, Z);
            Object before = c.nbt(x, Y, Z);
            removeMeasuredTile(c.world.tickableTileEntities, actual);
            c.require(!c.world.tickableTileEntities.contains(actual), "Measured control tile is no longer tickable");
            c.require(c.tile(x, Y, Z) == actual, "Holding preserves the same tile in the world and chunk indexes");
            c.require(before.equals(c.nbt(x, Y, Z)), "Holding writes no NBT or expected state");
            java.util.Map<String, Object> witness = new java.util.LinkedHashMap<>();
            witness.put("x", x); witness.put("y", Y); witness.put("z", Z);
            witness.put("removed", true); witness.put("sameTile", true); witness.put("nbtUnchanged", true);
            held.add(witness);
        }
        c.record("control.heldTiles", held);
        c.record("control.heldAtMeasuredEnd", true);
    }

    static <T> void removeMeasuredTile(java.util.List<T> tickables, T actual) {
        int registrations = 0;
        for (T entry : tickables) if (entry == actual) registrations++;
        if (registrations != 1) throw new AssertionError("Expected one real measured tick registration, found " + registrations);
        for (java.util.Iterator<T> iterator = tickables.iterator(); iterator.hasNext();) {
            if (iterator.next() == actual) { iterator.remove(); break; }
        }
        for (T entry : tickables) if (entry == actual) throw new AssertionError("Measured tile remained tickable");
    }
}
